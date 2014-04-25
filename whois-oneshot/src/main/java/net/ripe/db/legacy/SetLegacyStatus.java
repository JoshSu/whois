package net.ripe.db.legacy;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.sun.istack.NotNull;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.ripe.db.whois.api.rest.client.RestClient;
import net.ripe.db.whois.api.rest.client.RestClientException;
import net.ripe.db.whois.api.rest.mapper.AttributeMapper;
import net.ripe.db.whois.api.rest.mapper.DirtyClientAttributeMapper;
import net.ripe.db.whois.api.rest.mapper.FormattedClientAttributeMapper;
import net.ripe.db.whois.api.rest.mapper.WhoisObjectMapper;
import net.ripe.db.whois.common.ip.Ipv4Resource;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.rpsl.RpslObjectBuilder;
import net.ripe.db.whois.common.rpsl.RpslObjectFilter;
import net.ripe.db.whois.common.rpsl.attrs.InetnumStatus;
import net.ripe.db.whois.query.QueryFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Set Legacy Status script - set the status of the specified inetnums to LEGACY
 *
 * Command-line options:
 *
 *      --override-username : the override user
 *      --override-password : the override password
 *      --filename          : the input filename containing inetnums (csv format)
 *      --rest-api-url      : the REST API url (for lookup/search/update operations)
 *      --source            : the source name (RIPE or TEST)
 *
 * For example:
 *
 *      --override-username dbint --override-password dbint --filename DBlist20140407.csv --rest-api-url https://rest.db.ripe.net --source RIPE
 *
 */
public class SetLegacyStatus {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetLegacyStatus.class);

    private static final Splitter COMMA_SEPARATED_LIST_SPLITTER = Splitter.on(',');

    public static final String ARG_OVERRIDE_USERNAME = "override-username";
    public static final String ARG_OVERRIDE_PASSWORD = "override-password";
    public static final String ARG_FILENAME = "filename";
    public static final String ARG_REST_API_URL = "rest-api-url";
    public static final String ARG_SOURCE = "source";
    public static final String ARG_DRY_RUN = "dryrun";

    private final String username;
    private final String password;
    private final boolean dryRun;
    private final RestClient restClient;
    private String filename;

    public static void main(String[] args) throws IOException {
        final OptionSet options = setupOptionParser().parse(args);
        new SetLegacyStatus(
                (String)options.valueOf(ARG_FILENAME),
                (String)options.valueOf(ARG_OVERRIDE_USERNAME),
                (String)options.valueOf(ARG_OVERRIDE_PASSWORD),
                (String)options.valueOf(ARG_REST_API_URL),
                (String)options.valueOf(ARG_SOURCE),
                (Boolean)options.valueOf(ARG_DRY_RUN)).execute();
    }

    private static OptionParser setupOptionParser() {
        final OptionParser parser = new OptionParser();
        parser.accepts(ARG_OVERRIDE_USERNAME).withRequiredArg().required();
        parser.accepts(ARG_OVERRIDE_PASSWORD).withRequiredArg().required();
        parser.accepts(ARG_FILENAME).withRequiredArg().required();
        parser.accepts(ARG_REST_API_URL).withRequiredArg().required();
        parser.accepts(ARG_SOURCE).withRequiredArg().required();
        parser.accepts(ARG_DRY_RUN).withOptionalArg().ofType(Boolean.class).defaultsTo(true);
        return parser;
    }

    public SetLegacyStatus(
            @NotNull final String filename,
            @NotNull final String username,
            @NotNull final String password,
            @NotNull final String restApiUrl,
            @NotNull final String source,
            @NotNull boolean dryRun) {
        this.filename = filename;
        this.username = username;
        this.password = password;
        this.restClient = createRestClient(restApiUrl, source);
        this.dryRun = dryRun;
    }

    public SetLegacyStatus(
            @NotNull final String filename,
            @NotNull final String username,
            @NotNull final String password,
            @NotNull final RestClient restClient,
            @NotNull boolean dryRun) {
        this.filename = filename;
        this.username = username;
        this.password = password;
        this.restClient = restClient;
        this.dryRun = dryRun;
    }

    private void execute() throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
        String line;
        while ((line = reader.readLine()) != null) {
            RpslObject rpslObject = null;
            if (line.contains(",")) {
                rpslObject = lookupTopLevelIpv4ResourceFromCsl(line);
            } else {
                if (line.contains("/")) {
                    rpslObject = lookupTopLevelIp4Resource(line);
                } else {
                    LOGGER.info("Skipping line: {}", line);
                }
            }

            if (rpslObject != null) {
                setAllLegacyStatusToIpv4Resource(rpslObject);
            }
        }
    }

    private void setAllLegacyStatusToIpv4Resource(final RpslObject rpslObject) {
        setLegacyStatusToIpv4Resource(rpslObject);
        for (RpslObject moreSpecificObject : searchMoreSpecificMatch(rpslObject.getKey().toString())) {
            setLegacyStatusToIpv4Resource(moreSpecificObject);
        }
    }

    private void setLegacyStatusToIpv4Resource(final RpslObject rpslObject) {
        if (!rpslObject.containsAttribute(AttributeType.STATUS)) {
            LOGGER.warn("inetnum {} has no status, skipping it.", rpslObject.getKey());
            return;
        }

        final RpslAttribute statusAttribute = rpslObject.findAttribute(AttributeType.STATUS);
        if (statusAttribute.getCleanValue().equals(InetnumStatus.LEGACY.toString())) {
            LOGGER.info("LEGACY status already set for inetnum {}", rpslObject.getKey());
            return;
        }

        RpslObject updatedObject = (new RpslObjectBuilder(rpslObject))
                .replaceAttribute(statusAttribute, new RpslAttribute(AttributeType.STATUS, InetnumStatus.LEGACY.toString()))
                .get();

        if (!dryRun) {
            try {
                final String override = String.format("%s,%s,set-legacy-status {notify=false}", username, password);
                updatedObject = restClient
                        .request()
                        .addParam("unformatted", "")
                        .addParam("override", override)
                        .update(updatedObject);
            } catch (RestClientException e) {
                LOGGER.warn("Error when updating inetnum: {}\nreason: {}", updatedObject.getKey(), e.toString());
            }
        }

        LOGGER.info("inetnum: {} status set to LEGACY\n{}",
                updatedObject.getKey(),
                RpslObjectFilter.diff(rpslObject, updatedObject));
    }


    @Nullable
    RpslObject lookupTopLevelIpv4ResourceFromCsl(final String line) {
        final Iterator<String> tokens = COMMA_SEPARATED_LIST_SPLITTER.split(line).iterator();
        if (!tokens.hasNext()) {
            LOGGER.error("line: {} does not contain comma separated list", line);
            return null;
        }

        Ipv4Resource nextAddress = Ipv4Resource.parse(tokens.next());
        final List<Ipv4Resource> addresses = Lists.newArrayList(nextAddress);
        long end = nextAddress.end();
        while (tokens.hasNext()) {
            nextAddress = createIpv4Address(end + 1, Integer.parseInt(tokens.next().substring(1)));
            addresses.add(nextAddress);
            end = nextAddress.end();
        }

        final Ipv4Resource concatenatedAddress = concatenateIpv4Resources(addresses);

        return validateIpv4Resource(concatenatedAddress);
    }

    @Nullable
    private RpslObject validateIpv4Resource(final Ipv4Resource concatenated) {
        final RpslObject object = searchExactMatch(concatenated);
        if (object == null) {
            return null;
        }

        final RpslAttribute inetnum = object.findAttribute(AttributeType.INETNUM);
        if (!inetnum.getCleanValue().equals(concatenated.toRangeString())) {
            LOGGER.warn("Search result from database: {} does not match concatenated inetnum: {}, skipping.", inetnum.getCleanValue().toString(), concatenated.toString());
            return null;
        }
        return object;
    }

    @Nullable
    private RpslObject searchExactMatch(final Ipv4Resource concatenated) {
        final Collection<RpslObject> objects;
        try {
            objects = restClient
                    .request()
                    .addParam("unformatted", "")
                    .addParam("query-string", concatenated.toRangeString())
                    .addParams("type-filter", ObjectType.INETNUM.getName())
                    .addParams("flags", QueryFlag.NO_REFERENCED.getName(), QueryFlag.EXACT.getName(), QueryFlag.NO_FILTERING.getName())
                    .search();
        } catch (RestClientException ex) {
            LOGGER.error("Unable to retrieve exact match for inetnum: {}\nreason: {}", concatenated.toRangeString(), ex.toString());
            return null;
        }

        switch(objects.size()) {
            case 0:
                LOGGER.warn("inetnum {} not found in database", concatenated.toRangeString());
                return null;
            case 1:
                // always expect only one result
                return objects.iterator().next();
            default:
                LOGGER.warn("More than one match for {} found in database, skipping", concatenated.toRangeString());
                return objects.iterator().next();
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<RpslObject> searchMoreSpecificMatch(final String inetnumString) {
        try {
            return restClient
                    .request()
                    .addParam("unformatted", "")
                    .addParam("query-string", inetnumString)
                    .addParams("type-filter", ObjectType.INETNUM.getName())
                    .addParams("flags", QueryFlag.ALL_MORE.getName(), QueryFlag.NO_REFERENCED.getName(), QueryFlag.NO_FILTERING.getName())
                    .search();
        } catch (RestClientException ex) {
            LOGGER.error("Unable to retrieve more specific matches for {}\nreason: {}", inetnumString, ex.toString());
            return Collections.EMPTY_LIST;
        }
    }

    @Nullable
    RpslObject lookupTopLevelIp4Resource(String line) {
        Ipv4Resource resource = Ipv4Resource.parse(line);
        RpslObject rpslObject = validateIpv4Resource(resource);
        if (rpslObject != null) {
            return rpslObject;
        } else {
            return null;
        }
    }

    private Ipv4Resource createIpv4Address(final long startAddress, final int prefixLength) {
        long length = ((long) Math.pow(2d, (32 - prefixLength))) - 1l;
        long endAddres = startAddress + length;
        return new Ipv4Resource(startAddress, endAddres);
    }

    private Ipv4Resource concatenateIpv4Resources(final List<Ipv4Resource> resources) {
        if (resources.isEmpty()) {
            throw new IllegalArgumentException();
        }

        for (int index = 1; index < resources.size(); index++) {
            if (resources.get(index).begin() != resources.get(index - 1).end() + 1) {
                throw new IllegalArgumentException("found gap after " + resources.get(index).toString());
            }
        }

        return new Ipv4Resource(resources.get(0).begin(), resources.get(resources.size() - 1).end());
    }

    private RestClient createRestClient(final String restApiUrl, final String source) {
        final RestClient restClient = new RestClient(restApiUrl, source);
        restClient.setWhoisObjectMapper(
                new WhoisObjectMapper(
                        restApiUrl,
                        new AttributeMapper[]{
                                new FormattedClientAttributeMapper(),
                                new DirtyClientAttributeMapper()
                        }));
        return restClient;
    }

}
