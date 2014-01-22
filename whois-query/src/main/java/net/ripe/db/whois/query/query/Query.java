package net.ripe.db.whois.query.query;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import joptsimple.OptionException;
import joptsimple.OptionSet;
import net.ripe.db.whois.common.Message;
import net.ripe.db.whois.common.Messages;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.ip.IpInterval;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.ObjectTemplate;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.attrs.AsBlockRange;
import net.ripe.db.whois.query.QueryFlag;
import net.ripe.db.whois.query.domain.QueryCompletionInfo;
import net.ripe.db.whois.query.domain.QueryException;
import net.ripe.db.whois.query.domain.QueryMessages;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static net.ripe.db.whois.common.domain.CIString.ciString;

//TODO [TP] This class has way too many responsibilities, needs to be refactored.
public class Query {
    public static final int MAX_QUERY_ELEMENTS = 60;

    public static final EnumSet<ObjectType> ABUSE_CONTACT_OBJECT_TYPES = EnumSet.of(ObjectType.INETNUM, ObjectType.INET6NUM, ObjectType.AUT_NUM);
    private static final EnumSet<ObjectType> GRS_LIMIT_TYPES = EnumSet.of(ObjectType.AUT_NUM, ObjectType.INETNUM, ObjectType.INET6NUM, ObjectType.ROUTE, ObjectType.ROUTE6, ObjectType.DOMAIN);
    private static final EnumSet<ObjectType> DEFAULT_TYPES_LOOKUP_IN_BOTH_DIRECTIONS = EnumSet.of(ObjectType.INETNUM, ObjectType.INET6NUM, ObjectType.ROUTE, ObjectType.ROUTE6, ObjectType.DOMAIN);
    private static final EnumSet<ObjectType> DEFAULT_TYPES_ALL = EnumSet.allOf(ObjectType.class);

    private static final List<QueryValidator> QUERY_VALIDATORS = Lists.newArrayList(
            new MatchOperationValidator(),
            new ProxyValidator(),
            new AbuseContactValidator(),
            new CombinationValidator(),
            new SearchKeyValidator(),
            new TagValidator(),
            new VersionValidator());

    public static enum MatchOperation {
        MATCH_EXACT_OR_FIRST_LEVEL_LESS_SPECIFIC(),
        MATCH_EXACT(QueryFlag.EXACT),
        MATCH_FIRST_LEVEL_LESS_SPECIFIC(QueryFlag.ONE_LESS),
        MATCH_EXACT_AND_ALL_LEVELS_LESS_SPECIFIC(QueryFlag.ALL_LESS),
        MATCH_FIRST_LEVEL_MORE_SPECIFIC(QueryFlag.ONE_MORE),
        MATCH_ALL_LEVELS_MORE_SPECIFIC(QueryFlag.ALL_MORE);

        private final QueryFlag queryFlag;

        private MatchOperation() {
            this(null);
        }

        private MatchOperation(final QueryFlag queryFlag) {
            this.queryFlag = queryFlag;
        }

        boolean hasFlag() {
            return queryFlag != null;
        }

        public QueryFlag getQueryFlag() {
            return queryFlag;
        }
    }

    public static enum SystemInfoOption {
        VERSION, TYPES, SOURCES
    }

    public static enum Origin {
        PORT43, REST
    }

    private static final QueryFlagParser PARSER = new QueryFlagParser();

    private static final Joiner SPACE_JOINER = Joiner.on(' ');
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings();
    private static final Splitter SPACE_SPLITTER = Splitter.on(' ').omitEmptyStrings();

    private final Messages messages = new Messages();
    private final OptionSet options;

    private final String originalStringQuery;

    private final Set<String> sources;
    private final Set<ObjectType> objectTypeFilter;
    private final Set<ObjectType> suppliedObjectTypes;
    private final Set<AttributeType> attributeTypeFilter;
    private final MatchOperation matchOperation;
    private final SearchKey searchKey;

    private List<String> passwords;
    private String ssoToken;
    private Origin origin;

    private Query(final String query) {
        originalStringQuery = query;
        String[] args = Iterables.toArray(SPACE_SPLITTER.split(query), String.class);
        if (args.length > MAX_QUERY_ELEMENTS) {
            messages.add(QueryMessages.malformedQuery());
        }

        options = PARSER.parse(args);
        searchKey = new SearchKey(SPACE_JOINER.join(options.nonOptionArguments()).trim());

        sources = parseSources();
        suppliedObjectTypes = parseSuppliedObjectTypes();
        objectTypeFilter = generateAndFilterObjectTypes();
        attributeTypeFilter = parseAttributeTypes();
        matchOperation = parseMatchOperations();
    }

    public static Query parse(final String args) {
        return parse(args, Origin.PORT43);
    }

    public static Query parse(final String args, final Origin origin) {
        try {
            final Query query = new Query(args.trim());

            for (final QueryValidator queryValidator : QUERY_VALIDATORS) {
                queryValidator.validate(query, query.messages);
            }

            final Collection<Message> errors = query.messages.getMessages(Messages.Type.ERROR);
            if (!errors.isEmpty()) {
                throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, errors);
            }

            return query;
        } catch (OptionException e) {
            throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery());
        }
    }

    public static Query parse(final String args, final String ssoToken, final List<String> passwords) {
        Query query = parse(args, Origin.REST);
        query.ssoToken = ssoToken;
        query.passwords = passwords;
        return query;
    }

    public List<String> getPasswords() {
        return passwords;
    }

    public String getSsoToken() {
        return ssoToken;
    }

    public boolean via(Origin origin) {
        return this.origin == origin;
    }

    public static boolean hasFlags(String queryString) {
        return !PARSER.parse(Iterables.toArray(SPACE_SPLITTER.split(queryString), String.class)).specs().isEmpty();
    }

    public Collection<Message> getWarnings() {
        return messages.getMessages(Messages.Type.WARNING);
    }

    public boolean hasOptions() {
        return options.hasOptions();
    }

    public boolean hasOption(final QueryFlag queryFlag) {
        for (final String flag : queryFlag.getFlags()) {
            if (options.has(flag)) {
                return true;
            }
        }

        return false;
    }

    public boolean isAllSources() {
        return hasOption(QueryFlag.ALL_SOURCES);
    }

    public boolean isResource() {
        return hasOption(QueryFlag.RESOURCE);
    }

    public boolean isLookupInBothDirections() {
        return hasOption(QueryFlag.REVERSE_DOMAIN);
    }

    public boolean isReturningIrt() {
        return isBriefAbuseContact() || (!isKeysOnly() && hasOption(QueryFlag.IRT));
    }

    public boolean isGrouping() {
        return (!isKeysOnly() && !hasOption(QueryFlag.NO_GROUPING)) && !isBriefAbuseContact();
    }

    public boolean isBriefAbuseContact() {
        return hasOption(QueryFlag.ABUSE_CONTACT);
    }

    public boolean isKeysOnly() {
        return hasOption(QueryFlag.PRIMARY_KEYS);
    }

    public boolean isPrimaryObjectsOnly() {
        return !(isReturningReferencedObjects() || isReturningIrt() || isGrouping());
    }

    public boolean isFiltered() {
        return !(hasOption(QueryFlag.NO_FILTERING) || isKeysOnly() || isHelp() || isTemplate() || isVerbose());
    }

    public boolean isHelp() {
        return getSearchValue().equalsIgnoreCase("help");
    }

    public boolean isSystemInfo() {
        return hasOption(QueryFlag.LIST_SOURCES_OR_VERSION) || hasOption(QueryFlag.LIST_SOURCES) || hasOption(QueryFlag.VERSION) || hasOption(QueryFlag.TYPES);
    }

    public boolean isReturningReferencedObjects() {
        return !(hasOption(QueryFlag.NO_REFERENCED) || isShortHand() || isKeysOnly() || isResource() || isBriefAbuseContact());
    }

    public boolean isInverse() {
        return hasOption(QueryFlag.INVERSE);
    }

    public boolean isTemplate() {
        return hasOption(QueryFlag.TEMPLATE);
    }

    public boolean isVersionList() {
        return hasOption(QueryFlag.LIST_VERSIONS);
    }

    public boolean isVersionDiff() {
        return hasOption(QueryFlag.DIFF_VERSIONS);
    }

    public boolean isObjectVersion() {
        return hasOption(QueryFlag.SHOW_VERSION);
    }

    public int getObjectVersion() {
        if (hasOption(QueryFlag.SHOW_VERSION)) {
            final int version = Integer.parseInt(getOptionValue(QueryFlag.SHOW_VERSION));
            if (version < 1) {
                throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery("version flag number must be greater than 0"));
            }
            return version;
        }
        return -1;
    }

    public int[] getObjectVersions() {
        if (hasOption(QueryFlag.DIFF_VERSIONS)) {
            final String[] values = StringUtils.split(getOptionValue(QueryFlag.DIFF_VERSIONS), ':');
            if (values.length != 2) {
                throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery("diff versions must be in the format a:b"));
            }
            final int firstValue = Integer.parseInt(values[0]);
            final int secondValue = Integer.parseInt(values[1]);
            if (firstValue < 1 || secondValue < 1) {
                throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery("diff version number must be greater than 0"));
            }
            if (secondValue == firstValue) {
                throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery("diff versions are the same"));
            }
            return new int[]{firstValue, secondValue};
        }
        return new int[]{-1, -1};
    }

    public String getTemplateOption() {
        return getOptionValue(QueryFlag.TEMPLATE);
    }

    public boolean isVerbose() {
        return hasOption(QueryFlag.VERBOSE);
    }

    public boolean isValidSyntax() {
        return hasOption(QueryFlag.VALID_SYNTAX);
    }

    public boolean isNoValidSyntax() {
        return hasOption(QueryFlag.NO_VALID_SYNTAX);
    }

    public SystemInfoOption getSystemInfoOption() {
        if (hasOption(QueryFlag.LIST_SOURCES_OR_VERSION)) {
            final String optionValue = getOptionValue(QueryFlag.LIST_SOURCES_OR_VERSION).trim();
            try {
                return SystemInfoOption.valueOf(optionValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery("Invalid option: " + optionValue));
            }
        }

        if (hasOption(QueryFlag.LIST_SOURCES)) {
            return SystemInfoOption.SOURCES;
        }

        if (hasOption(QueryFlag.VERSION)) {
            return SystemInfoOption.VERSION;
        }

        if (hasOption(QueryFlag.TYPES)) {
            return SystemInfoOption.TYPES;
        }

        throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery());
    }

    public String getVerboseOption() {
        return getOptionValue(QueryFlag.VERBOSE);
    }

    public boolean hasObjectTypesSpecified() {
        return hasOption(QueryFlag.SELECT_TYPES);
    }

    public Set<ObjectType> getObjectTypes() {
        return objectTypeFilter;
    }

    public Set<AttributeType> getAttributeTypes() {
        return attributeTypeFilter;
    }

    public Query.MatchOperation matchOperation() {
        return matchOperation;
    }

    public boolean hasIpFlags() {
        return isLookupInBothDirections() || matchOperation != null;
    }

    public boolean hasObjectTypeFilter(ObjectType objectType) {
        return objectTypeFilter.contains(objectType);
    }

    public String getSearchValue() {
        return searchKey.getValue();
    }

    public Set<ObjectType> getSuppliedObjectTypes(){
        return suppliedObjectTypes;
    }

    public IpInterval<?> getIpKeyOrNull() {
        final IpInterval<?> ipKey = searchKey.getIpKeyOrNull();
        if (ipKey != null) {
            return ipKey;
        }

        if (isLookupInBothDirections()) {
            return searchKey.getIpKeyOrNullReverse();
        }

        return null;
    }

    public IpInterval<?> getIpKeyOrNullReverse() {
        final IpInterval<?> ipKey = searchKey.getIpKeyOrNullReverse();
        if (ipKey != null) {
            return ipKey;
        }

        if (isLookupInBothDirections()) {
            return searchKey.getIpKeyOrNull();
        }

        return null;
    }

    public String getRouteOrigin() {
        return searchKey.getOrigin();
    }

    public AsBlockRange getAsBlockRangeOrNull() {
        return searchKey.getAsBlockRangeOrNull();
    }

    public String getProxy() {
        return getOptionValue(QueryFlag.CLIENT);
    }

    public boolean hasProxy() {
        return hasOption(QueryFlag.CLIENT);
    }

    public boolean hasProxyWithIp() {
        return getProxyIp() != null;
    }

    public boolean hasKeepAlive() {
        return hasOption(QueryFlag.PERSISTENT_CONNECTION);
    }

    public boolean isShortHand() {
        return hasOption(QueryFlag.BRIEF);
    }

    public boolean hasOnlyKeepAlive() {
        return hasKeepAlive() && (queryLength() == 2 || originalStringQuery.equals("--persistent-connection"));
    }

    public int queryLength() {
        return originalStringQuery.length();
    }

    public boolean isProxyValid() {
        if (!hasProxy()) {
            return true;
        }

        String[] proxyArray = StringUtils.split(getProxy(), ',');

        if (proxyArray.length > 2) {
            return false;
        }

        if (proxyArray.length == 2) {
            return InetAddresses.isInetAddress(proxyArray[1]);
        }

        return true;
    }

    public String getProxyIp() {
        if (!hasProxy()) {
            return null;
        }

        String[] proxyArray = StringUtils.split(getProxy(), ',');

        if (proxyArray.length == 2) {
            return proxyArray[1];
        }

        return null;
    }

    public boolean hasSources() {
        return !sources.isEmpty();
    }

    public Set<String> getSources() {
        return sources;
    }

    private Set<ObjectType> parseSuppliedObjectTypes() {
        final Set<String> objectTypesOptions = getOptionValues(QueryFlag.SELECT_TYPES);
        final Set<ObjectType> objectTypes = Sets.newHashSet();

        if (!objectTypesOptions.isEmpty()) {
            for (final String objectType : objectTypesOptions) {
                try {
                    objectTypes.add(ObjectType.getByName(objectType));
                } catch (IllegalArgumentException e) {
                    throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.invalidObjectType(objectType));
                }
            }
        }
       return Collections.unmodifiableSet(objectTypes);
    }

    private Set<ObjectType> generateAndFilterObjectTypes() {
        final Set<ObjectType> response = Sets.newTreeSet(ObjectType.COMPARATOR);    // whois query results returned in correct order depends on this comparator

        if (suppliedObjectTypes.isEmpty()) {
            if (isLookupInBothDirections()) {
                response.addAll(DEFAULT_TYPES_LOOKUP_IN_BOTH_DIRECTIONS);
            } else {
                response.addAll(DEFAULT_TYPES_ALL);
            }
        } else {
            response.addAll(suppliedObjectTypes);
        }

        if (hasOption(QueryFlag.NO_PERSONAL)) {
            response.remove(ObjectType.PERSON);
            response.remove(ObjectType.ROLE);
        }

        if (hasOption(QueryFlag.RESOURCE)) {
            response.retainAll(GRS_LIMIT_TYPES);
        }

        if (hasOption(QueryFlag.ABUSE_CONTACT)) {
            response.retainAll(ABUSE_CONTACT_OBJECT_TYPES);
        }

        if (!isInverse()) {
            nextObjectType:
            for (Iterator<ObjectType> it = response.iterator(); it.hasNext(); ) {
                ObjectType objectType = it.next();
                for (final AttributeType attribute : ObjectTemplate.getTemplate(objectType).getLookupAttributes()) {
                    if (AttributeMatcher.fetchableBy(attribute, this)) {
                        continue nextObjectType;
                    }
                }
                it.remove();
            }
        }

        return Collections.unmodifiableSet(response);
    }

    private Set<String> parseSources() {
        final Set<String> optionValues = getOptionValues(QueryFlag.SOURCES);
        if (optionValues.isEmpty()) {
            return Collections.emptySet();
        }

        final Set<String> result = Sets.newLinkedHashSet();
        for (final String source : optionValues) {
            result.add(source.toUpperCase());
        }

        return Collections.unmodifiableSet(result);
    }

    private Set<AttributeType> parseAttributeTypes() {
        if (!isInverse()) {
            return Collections.emptySet();
        }

        final Set<String> attributeTypes = getOptionValues(QueryFlag.INVERSE);
        final Set<AttributeType> ret = Sets.newLinkedHashSet();
        for (final String attributeType : attributeTypes) {
            try {
                final AttributeType type = AttributeType.getByName(attributeType);
                if (AttributeType.PERSON.equals(type)) {
                    ret.addAll(Arrays.asList(AttributeType.ADMIN_C, AttributeType.TECH_C, AttributeType.ZONE_C, AttributeType.AUTHOR, AttributeType.PING_HDL));
                } else {
                    ret.add(type);
                }
            } catch (IllegalArgumentException e) {
                throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.invalidAttributeType(attributeType));
            }
        }

        return Collections.unmodifiableSet(ret);
    }

    public Set<String> getOptionValues(final QueryFlag queryFlag) {
        final Set<String> optionValues = Sets.newLinkedHashSet();
        for (final String flag : queryFlag.getFlags()) {
            if (options.has(flag)) {
                for (final Object optionArgument : options.valuesOf(flag)) {
                    for (final String splittedArgument : COMMA_SPLITTER.split(optionArgument.toString())) {
                        optionValues.add(splittedArgument);
                    }
                }
            }
        }

        return optionValues;
    }

    // TODO: [AH] only this CIString version should be used
    public Set<CIString> getOptionValuesCI(final QueryFlag queryFlag) {
        final Set<CIString> optionValues = Sets.newLinkedHashSet();
        for (final String flag : queryFlag.getFlags()) {
            if (options.has(flag)) {
                for (final Object optionArgument : options.valuesOf(flag)) {
                    for (final String splittedArgument : COMMA_SPLITTER.split(optionArgument.toString())) {
                        optionValues.add(ciString(splittedArgument));
                    }
                }
            }
        }

        return optionValues;
    }

    String getOptionValue(final QueryFlag queryFlag) {
        String optionValue = null;
        for (final String flag : queryFlag.getFlags()) {
            if (options.has(flag)) {
                try {
                    for (final Object optionArgument : options.valuesOf(flag)) {
                        if (optionValue == null) {
                            optionValue = optionArgument.toString();
                        } else {
                            throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.invalidMultipleFlags((flag.length() == 1 ? "-" : "--") + flag));
                        }
                    }
                } catch (OptionException e) {
                    throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.malformedQuery());
                }
            }
        }
        return optionValue;
    }

    private MatchOperation parseMatchOperations() {
        MatchOperation result = null;

        for (final Query.MatchOperation matchOperation : Query.MatchOperation.values()) {
            if (matchOperation.hasFlag() && hasOption(matchOperation.getQueryFlag())) {
                if (result == null) {
                    result = matchOperation;
                } else {
                    throw new QueryException(QueryCompletionInfo.PARAMETER_ERROR, QueryMessages.duplicateIpFlagsPassed());
                }
            }
        }
        return result;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + options.hashCode();
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Query other = (Query) obj;
        return options.equals(other.options);
    }

    @Override
    public String toString() {
        return originalStringQuery;
    }

    public boolean matchesObjectTypeAndAttribute(final ObjectType objectType, final AttributeType attributeType) {
        return ObjectTemplate.getTemplate(objectType).getLookupAttributes().contains(attributeType) && AttributeMatcher.fetchableBy(attributeType, this);
    }
}
