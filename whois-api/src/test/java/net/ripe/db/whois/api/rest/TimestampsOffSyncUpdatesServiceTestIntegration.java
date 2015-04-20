package net.ripe.db.whois.api.rest;

import net.ripe.db.whois.api.AbstractIntegrationTest;
import net.ripe.db.whois.api.RestTest;
import net.ripe.db.whois.api.syncupdate.SyncUpdateUtils;
import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.rpsl.TestTimestampsMode;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

@Category(IntegrationTest.class)
public class TimestampsOffSyncUpdatesServiceTestIntegration extends AbstractIntegrationTest {
    @Autowired private TestTimestampsMode testTimestampsMode;

    //TODO MG: Remove when timestamps always on.

    private static final RpslObject OWNER_MNT = RpslObject.parse("" +
            "mntner:      OWNER-MNT\n" +
            "descr:       Owner Maintainer\n" +
            "admin-c:     TP1-TEST\n" +
            "upd-to:      updnoreply@ripe.net\n" +
            "notify:      notify@ripe.net\n" +
            "auth:        MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
            "auth:        SSO person@net.net\n" +
            "mnt-by:      OWNER-MNT\n" +
            "changed:     dbtest@ripe.net 20120101\n" +
            "source:      TEST");

    private static final RpslObject TEST_PERSON = RpslObject.parse("" +
            "person:    Test Person\n" +
            "address:   Singel 258\n" +
            "phone:     +31 6 12345678\n" +
            "nic-hdl:   TP1-TEST\n" +
            "mnt-by:    OWNER-MNT\n" +
            "changed:   dbtest@ripe.net 20120101\n" +
            "source:    TEST\n");

    private static final RpslObject TEST_ROLE = RpslObject.parse("" +
            "role:      Test Role\n" +
            "address:   Singel 258\n" +
            "phone:     +31 6 12345678\n" +
            "nic-hdl:   TR1-TEST\n" +
            "admin-c:   TP1-TEST\n" +
            "abuse-mailbox: abuse@test.net\n" +
            "mnt-by:    OWNER-MNT\n" +
            "changed:   dbtest@ripe.net 20120101\n" +
            "source:    TEST\n");

    @Before
    public void setup() {
        databaseHelper.addObject("person: Test Person\nnic-hdl: TP1-TEST");
        databaseHelper.addObject("role: Test Role\nnic-hdl: TR1-TEST");
        databaseHelper.addObject(OWNER_MNT);
        databaseHelper.updateObject(TEST_PERSON);
        databaseHelper.updateObject(TEST_ROLE);
        testDateTimeProvider.setTime(LocalDateTime.parse("2001-02-04T17:00:00"));
    }

    @Test
    public void delete_with_timestamps_org_contains_no_timestamps_when_timestamps_off() {
        databaseHelper.updateObject("" +
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "source:    TEST\n");
        testTimestampsMode.setTimestampsOff(true);

        String response = RestTest.target(getPort(), "whois/syncupdates/test?" +
                "DATA=" + SyncUpdateUtils.encode(""+
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "created:   2001-02-04T10:00:00Z\n" + // not allowed in old mode
                "last-modified: 2001-02-04T15:00:00Z\n" + // not allowed in old mode
                "source:    TEST\n" +
                "delete: reason\n\n" +
                "password: test"))
                .request()
                .get(String.class);

        assertThat(response, containsString("Delete FAILED: [role] TR1-TEST   Test Role"));
        assertThat(response, containsString("***Error:   \"created\" is not a known RPSL attribute"));
        assertThat(response, containsString("***Error:   \"last-modified\" is not a known RPSL attribute"));
    }

    @Test
    public void delete_without_timestamps_org_contains_no_timestamps_when_timestamps_off() {
        databaseHelper.updateObject("" +
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "source:    TEST\n");
        testTimestampsMode.setTimestampsOff(true);

        String response = RestTest.target(getPort(), "whois/syncupdates/test?" +
                "DATA=" + SyncUpdateUtils.encode(""+
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "source:    TEST\n" +
                "delete: reason\n\n" +
                "password: test"))
                .request()
                .get(String.class);

        assertThat(response, containsString("Delete SUCCEEDED: [role] TR1-TEST   Test Role"));
    }

//    @Ignore
    @Test
    public void delete_with_timestamps_org_contains_timestamps_when_timestamps_off() {
        databaseHelper.updateObject("" +
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "created:   2001-02-04T10:00:00Z\n" +
                "last-modified: 2001-02-04T15:00:00Z\n" +
                "source:    TEST\n");
        testTimestampsMode.setTimestampsOff(true);

        String response = RestTest.target(getPort(), "whois/syncupdates/test?" +
                "DATA=" + SyncUpdateUtils.encode(""+
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "created:   2001-02-04T10:00:00Z\n" + // not allowed in old mode
                "last-modified: 2001-02-04T15:00:00Z\n" +  // not allowed in old mode
                "source:    TEST\n" +
                "delete: reason\n\n" +
                "password: test"))
                .request()
                .get(String.class);

        assertThat(response, containsString("Delete FAILED: [role] TR1-TEST   Test Role"));
        assertThat(response, containsString("***Error:   \"created\" is not a known RPSL attribute"));
        assertThat(response, containsString("***Error:   \"last-modified\" is not a known RPSL attribute"));
    }

    @Test
    public void delete_without_timestamps_org_contains_timestamps_when_timestamps_off() {
        databaseHelper.updateObject("" +
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "created:   2001-02-04T10:00:00Z\n" +
                "last-modified: 2001-02-04T15:00:00Z\n" +
                "source:    TEST\n");
        testTimestampsMode.setTimestampsOff(true);

        String response = RestTest.target(getPort(), "whois/syncupdates/test?" +
                "DATA=" + SyncUpdateUtils.encode(""+
                "role:      Test Role\n" +
                "address:   Singel 258\n" +
                "phone:     +31 6 12345678\n" +
                "nic-hdl:   TR1-TEST\n" +
                "admin-c:   TP1-TEST\n" +
                "abuse-mailbox: abuse@test.net\n" +
                "mnt-by:    OWNER-MNT\n" +
                "changed:   dbtest@ripe.net 20120101\n" +
                "source:    TEST\n" +
                "delete: reason\n\n" +
                "password: test"))
                .request()
                .get(String.class);

        System.err.println("resp:" + response);
        assertThat(response, containsString("Delete SUCCEEDED: [role] TR1-TEST   Test Role"));
    }

}
