package net.ripe.db.whois.query.dao.jdbc;

import net.ripe.db.whois.common.domain.serials.Operation;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.query.dao.VersionDao;
import net.ripe.db.whois.query.dao.VersionInfo;
import net.ripe.db.whois.query.domain.VersionDateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static net.ripe.db.whois.common.dao.jdbc.JdbcRpslObjectOperations.loadScripts;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class JdbcVersionDaoTest extends AbstractQueryDaoTest {
    @Autowired VersionDao subject;

    @Before
    public void before() {
        loadScripts(databaseHelper.getWhoisTemplate(), "broken.sql");
    }

    @Test
    public void findNoObject() {
        List<VersionInfo> history = subject.findByKey(ObjectType.MNTNER, "MAINT-ANY");
        assertNotNull(history);
        assertThat(history, hasSize(0));
    }

    @Test
    public void noHistoryForObject() {
        List<VersionInfo> history = subject.findByKey(ObjectType.AS_SET, "AS-TEST");

        assertNotNull(history);
        assertThat(history, hasSize(1));
        assertThat(history.get(0).isInLast(), is(true));
        assertThat(history.get(0).getOperation(), is(Operation.UPDATE));
        assertThat(history.get(0).getObjectId(), is(824));
        assertThat(history.get(0).getSequenceId(), is(3));
        assertThat(history.get(0).getTimestamp(), is(new VersionDateTime(1032338056L)));
        assertThat(history.get(0).getObjectType(), is(ObjectType.AS_SET));
        assertThat(history.get(0).getKey(), is("AS-TEST"));

        final RpslObject rpslObject = subject.getRpslObject(history.get(0));

        assertThat(rpslObject, is(RpslObject.parse("" +
                "as-set: AS-TEST\n" +
                "descr:  Description\n" +
                "source: RIPE\n")));
    }

    @Test
    public void historyForDeletedObject() {
        List<VersionInfo> history = subject.findByKey(ObjectType.DOMAIN, "test.sk");

        assertNotNull(history);
        assertThat(history, hasSize(2));
        assertThat(history.get(1).getOperation(), is(Operation.DELETE));

        databaseHelper.addObject("domain:test.sk\ndescr:description1\nsource:RIPE\n");
        databaseHelper.updateObject("domain:test.sk\ndescr:description2\nsource:RIPE\n");
        databaseHelper.updateObject("domain:test.sk\ndescr:description3\nsource:RIPE\n");

        List<VersionInfo> recreated = subject.findByKey(ObjectType.DOMAIN, "test.sk");
        assertThat(recreated.size(), is(5));
        for (int i = 2; i < recreated.size(); i++) {
            assertThat(recreated.get(i).getOperation(), is(Operation.UPDATE));
        }
    }

    @Test
    public void longHistory() {
        List<VersionInfo> history = subject.findByKey(ObjectType.AUT_NUM, "AS20507");

        assertNotNull(history);
        assertThat(history, hasSize(4));

        isMatching(history.get(0), new VersionInfo(false, 4709, 81, 1032341936L, Operation.UPDATE, ObjectType.AUT_NUM, "AS20507"));
        isMatching(history.get(2), new VersionInfo(false, 4709, 83, 1034602217L, Operation.UPDATE, ObjectType.AUT_NUM, "AS20507"));
        isMatching(history.get(3), new VersionInfo(false, 4709, 84, 1034685022L, Operation.UPDATE, ObjectType.AUT_NUM, "AS20507"));
    }

    public void isMatching(VersionInfo got, VersionInfo expected) {
        isMatching(null, got, expected);
    }

    public void isMatching(String message, VersionInfo got, VersionInfo expected) {
        assertThat(message, got.toString(), is(expected.toString()));
    }
}
