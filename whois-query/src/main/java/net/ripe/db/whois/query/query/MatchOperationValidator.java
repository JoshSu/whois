package net.ripe.db.whois.query.query;

import net.ripe.db.whois.common.Messages;
import net.ripe.db.whois.common.domain.IpInterval;
import net.ripe.db.whois.common.domain.Ipv4Resource;
import net.ripe.db.whois.common.domain.Ipv6Resource;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.query.domain.QueryMessages;

import java.util.Set;

class MatchOperationValidator implements QueryValidator {
    @Override
    public void validate(final Query query, final Messages messages) {
        final Set<Query.MatchOperation> matchOperations = query.matchOperations();

        if (matchOperations.size() > 1) {
            messages.add(QueryMessages.duplicateIpFlagsPassed());
            return;
        }

        if (query.hasIpFlags() && query.getIpKeyOrNull() == null) {
            messages.add(QueryMessages.uselessIpFlagPassed());
            return;
        }

        if (matchOperations.contains(Query.MatchOperation.MATCH_FIRST_LEVEL_MORE_SPECIFIC) || matchOperations.contains(Query.MatchOperation.MATCH_ALL_LEVELS_MORE_SPECIFIC)) {
            final IpInterval<?> ipKey = query.getIpKeyOrNull();
            if (ipKey == null) {
                return;
            }

            final IpInterval<?> maxRange = AttributeType.INETNUM.equals(ipKey.getAttributeType()) ? Ipv4Resource.MAX_RANGE : Ipv6Resource.MAX_RANGE;
            if (maxRange.equals(ipKey)) {
                messages.add(QueryMessages.illegalRange());
            }
        }
    }
}
