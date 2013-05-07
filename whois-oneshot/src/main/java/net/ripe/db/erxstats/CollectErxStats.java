package net.ripe.db.erxstats;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.domain.Ipv4Resource;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.RpslObject;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.annotation.CheckForNull;
import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static net.ripe.db.whois.common.domain.CIString.ciString;

/* Snippet from confluence (aka specs)

Start with the list of resources from RS.

    If it has RIPE-NCC-LOCKED-MNT count and discard...we know it is NOT being maintained by the user.
    NEXT
    If it still has an auto-generated MNTNER of the form ERX-NET-138-81-MNT as "mnt-by:"
        look in history/last tables
        find first instance of object with this MNTNER as "mnt-by:"
        if it has never changed since, count and discard...we know it is NOT being maintained by the user.
        NEXT
        if it has changed, in any way, keep list of IP address and last changed date...it MAY have been changed by user or by us
        NEXT
    the remainder are not locked and do not have auto generated MNTNER
    look in history table, did it ever have an auto generated MNTNER of the form ERX-NET-138-81-MNT as "mnt-by:"
    if yes,
        the MNTNER has been changed..the user has changed, or requested a change to, the data
        start another list of IP address and last changed date
        NEXT
    the remainder are not locked and never had an auto-generated MNTNER
    this is the merged ARIN/RIPE data....the swamp
        does current version still have both these lines:

            remarks:      **** INFORMATION FROM ARIN OBJECT ****

            remarks:      **** INFORMATION FROM RIPE OBJECT ****

        if either is missing, count and discard...the user has changed the data
        NEXT
        look in history/last tables, find first instance that had both these lines
            if it has never changed since, count and discard....we know it is NOT being maintained by the user.
            NEXT
            those that have changed, start another list of IP address and last changed date...changed by user or us
            NEXT

We don't want to dig any deeper at this stage into the lists that may have been changed by a user or by us. For that we have to start looking at log files or the actual changes to the object.

We can give the counts to the community as well as some summary of when some categories were last changed. That may be enough.

*/
public class CollectErxStats {

    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    private static final EnumMap<State, Set<String>> results = Maps.newEnumMap(State.class);
    private static final String RIPE_NCC_LOCKED_MNT = "RIPE-NCC-LOCKED-MNT";
    private static final Pattern AUTOGENERATED_MNT_PATTERN = Pattern.compile("(?i)^ERX-NET-(\\d+-)+MNT$");
    private static final List<String> ERX_MERGE_REMARKS_LINES = ImmutableList.of("**** INFORMATION FROM ARIN OBJECT ****", "**** INFORMATION FROM RIPE OBJECT ****");

    public static void main(String[] args) throws Exception {
        dataSource = new SimpleDriverDataSource(new com.mysql.jdbc.Driver(), "jdbc:mysql://dbc-whois5.ripe.net/WHOIS_UPDATE_RIPE", "rdonly", "s8f,4U");
        jdbcTemplate = new JdbcTemplate(dataSource);

        for (State state: State.values()) {
            results.put(state, new HashSet());
        }

        for (String prefix: Resources.readLines(Resources.getResource("alllegacies.csv"), Charset.defaultCharset())) {
            String range = prefix;
            List<ErxObject> erxObjects;

            try {
                range = Ipv4Resource.parse(prefix).toRangeString();
                erxObjects = collectObjects(range);
            } catch (Exception e) {
                System.err.println("Internal error: " + prefix);
                e.printStackTrace();
                results.get(State.ERROR).add(range);
                continue;
            }

            if (erxObjects == null) {
                results.get(State.DELETED).add(range);
                continue;
            }

            // TODO: has ERX-MNT, but changed by user since, currently filed under unmaintained
            // TODO: has ERX remarks lines, but changed by user since, currently filed under rest
            if (hasLockedMnt(erxObjects)) {
                results.get(State.HAS_LOCKED_MNT).add(range);
                results.get(State.UNMAINTAINED_TOTAL).add(range);
            } else if (hasAutogeneratedMntInLast(erxObjects)) {
                results.get(State.HAS_AUTOGENERATED_MNT).add(range);
                results.get(State.UNMAINTAINED_TOTAL).add(range);
            } else if (hasAutogeneratedMntInHistory(erxObjects)) {
                results.get(State.HAS_ERX_MNT_REMOVED).add(range);
                results.get(State.MAINTAINED_TOTAL).add(range);
            } else if (!hasErxMergeRemarksLines(erxObjects)) {
                results.get(State.HAS_ERX_REMARKS_REMOVED).add(range);
                results.get(State.MAINTAINED_TOTAL).add(range);
            } else {
                results.get(State.REST).add(range);
            }
        }

        for (State state: State.values()) {
            System.out.println(state + ": " + results.get(state).size());
        }

        for (State state: State.values()) {
            System.out.println(state + ": " + results.get(state).size());
            for (String range : results.get(state)) {
                System.out.println(range);
            }
            System.out.println();
        }
    }

    private static boolean hasErxMergeRemarksLines(List<ErxObject> erxObjects) {
        List<String> lookingFor = Lists.newArrayList(ERX_MERGE_REMARKS_LINES);
        RpslObject latest = erxObjects.get(erxObjects.size()-1).object;
        for (CIString remarks : latest.getValuesForAttribute(AttributeType.REMARKS)) {
            for (int i = 0; i < lookingFor.size(); i++) {
                if (remarks.toString().equals(lookingFor.get(i))) {
                    lookingFor.remove(i);
                    break;
                }
            }
        }
        return lookingFor.isEmpty();
    }

    private static boolean hasAutogeneratedMntInLast(List<ErxObject> erxObjects) {
        RpslObject latest = erxObjects.get(erxObjects.size()-1).object;
        return hasAutogeneratedMnt(latest);
    }

    private static boolean hasAutogeneratedMntInHistory(List<ErxObject> erxObjects) {
        for (ErxObject erxObject : erxObjects) {
            if (hasAutogeneratedMnt(erxObject.object)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAutogeneratedMnt(RpslObject latest) {
        for (CIString mntby : latest.getValuesForAttribute(AttributeType.MNT_BY)) {
            if (AUTOGENERATED_MNT_PATTERN.matcher(mntby.toString()).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLockedMnt(List<ErxObject> erxObjects) {
        RpslObject latest = erxObjects.get(erxObjects.size()-1).object;
        for (CIString mntby : latest.getValuesForAttribute(AttributeType.MNT_BY)) {
            if (mntby.toString().equals(RIPE_NCC_LOCKED_MNT)) {
                return true;
            }
        }
        return false;
    }

    @CheckForNull
    public static List<ErxObject> collectObjects(String pkey) {
        ErxObject lastObject;
        try {
            lastObject = jdbcTemplate.queryForObject("SELECT object_id, sequence_id, timestamp, object FROM last WHERE object != '' AND pkey = ?", new ErxObjectRowMapper(), pkey);
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        }

        if (lastObject == null) {
            return null;
        }

        final List<ErxObject> historyObjects = jdbcTemplate.query("SELECT object_id, sequence_id, timestamp, object FROM history WHERE pkey = ? ORDER BY timestamp", new ErxObjectRowMapper(), pkey);
        historyObjects.add(lastObject);

        return historyObjects;
    }

    public enum State {
        ERROR("Internal error while processing"),
        DELETED("Has been deleted"),
        HAS_LOCKED_MNT("Has RIPE-NCC-LOCKED-MNT as \"mnt-by:\""),
        HAS_AUTOGENERATED_MNT("Has an auto-generated MNTNER of the form ERX-NET-138-81-MNT as \"mnt-by:\""),
        HAS_ERX_MNT_REMOVED("Removed the auto-generated MNTNER of the form ERX-NET-138-81-MNT as \"mnt-by:\""),
        HAS_ERX_REMARKS_REMOVED("Removed the lines 'remarks: **** INFORMATION FROM ARIN/RIPE OBJECT ****'"),
        UNMAINTAINED_TOTAL("Total number of definitely unmaintained"),
        MAINTAINED_TOTAL("Total number of definitely maintained"),
        REST("None of the above/Cannot decide");

        private final String descr;

        State(String descr) {
            this.descr = descr;
        }

        public String toString() {
            return descr;
        }
    }

    static class ErxObject {
        int objectId;
        int sequenceId;
        int timestamp;
        RpslObject object;

        ErxObject(int objectId, int sequenceId, int timestamp, RpslObject object) {
            this.objectId = objectId;
            this.sequenceId = sequenceId;
            this.timestamp = timestamp;
            this.object = object;
        }
    }

    private static class ErxObjectRowMapper implements RowMapper<ErxObject> {
        @Override
        public ErxObject mapRow(ResultSet rs, int rowNum) throws SQLException {
            final String object = rs.getString(4);
            if (object == null || object.length() == 0) {
                return null;
            }
            return new ErxObject(rs.getInt(1), rs.getInt(2), rs.getInt(3), RpslObject.parse(object));
        }
    }
}
