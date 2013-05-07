package net.ripe.db.whois.nrtm.integration;

import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.support.DummyWhoisClient;
import net.ripe.db.whois.nrtm.NrtmServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.kubek2k.springockito.annotations.SpringockitoContextLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(loader = SpringockitoContextLoader.class, locations = {"classpath:applicationContext-nrtm-test.xml"}, inheritLocations = false)
@Category(IntegrationTest.class)
public class SimpleTestIntegration extends AbstractNrtmIntegrationBase {

    @Value("${nrtm.update.interval}") private String updateIntervalString;

    private int updateInterval;

    @Before
    public void before() throws Exception {
        updateInterval = Integer.valueOf(updateIntervalString);
        nrtmServer.start();
    }

    @After
    public void after() {
        nrtmServer.stop();
    }

    @Test
    public void versionQuery() throws Exception {
        final String response = DummyWhoisClient.query(NrtmServer.port, "-q version");

        assertThat(response, containsString("% nrtm-server"));
    }

    @Test
    public void sourcesQuery() throws Exception {
        final String response = DummyWhoisClient.query(NrtmServer.port, "-q sources");

        assertThat(response, containsString("TEST:3:X:0-0"));
    }

    @Test
    public void emptyQuery() throws Exception {
        final String response = DummyWhoisClient.query(NrtmServer.port, "\n");

        assertThat(response, containsString("no flags passed"));
    }

    @Test
    public void queryKeepaliveNoPreExistingObjects() throws Exception {
        final String response = DummyWhoisClient.query(NrtmServer.port, "-g TEST:3:1-2 -k", (updateInterval + 1) * 1000);

        assertThat(response, containsString("%ERROR:401: invalid range"));
    }

    @Test
    public void queryKeepAliveNoPreExistingObjectsOneNewObject() throws Exception {
        databaseHelper.addObject(RpslObject.parse("mntner:test"));
        DummyNrtmClient client = new DummyNrtmClient(NrtmServer.port, "-g TEST:3:1-1 -k", (updateInterval + 1));

        client.start();
        databaseHelper.addObject(RpslObject.parse("mntner:keepalive"));
        String response = client.end();

        assertThat(response, containsString("mntner:         keepalive"));
    }

    @Test
    public void queryKeepAliveOnePreExistingObjectsOneNewObject() throws Exception {
        databaseHelper.addObject(RpslObject.parse("mntner:testmntner\nmnt-by:testmntner"));
        DummyNrtmClient client = new DummyNrtmClient(NrtmServer.port, "-g TEST:3:1-LAST -k", (updateInterval + 1));

        client.start();
        super.databaseHelper.addObject(RpslObject.parse("mntner:keepalive"));
        String response = client.end();

        assertThat(response, containsString("mntner:         testmntner"));
        assertThat(response, containsString("mntner:         keepalive"));
    }

    @Test
    public void mirrorQueryOneSerialEntry() throws Exception {
        databaseHelper.addObject("aut-num:AS4294967207");

        final String response = DummyWhoisClient.query(NrtmServer.port, "-g TEST:3:1-1");

        assertThat(response, containsString("AS4294967207"));
    }

    @Test
    public void mirrorQueryMultipleSerialEntry() throws Exception {
        databaseHelper.addObject("aut-num:AS4294967207");
        databaseHelper.addObject("person:Denis Walker\nnic-hdl:DW-RIPE");
        databaseHelper.addObject("mntner:DEV-MNT");

        final String response = DummyWhoisClient.query(NrtmServer.port, "-g TEST:3:1-3");

        assertThat(response, containsString("ADD 1"));
        assertThat(response, containsString("AS4294967207"));
        assertThat(response, not(containsString("ADD 2")));
        assertThat(response, not(containsString("DW-RIPE")));
        assertThat(response, containsString("ADD 3"));
        assertThat(response, containsString("DEV-MNT"));
    }

    @Test
    public void mirrorQueryOutofRange() throws Exception {
        databaseHelper.addObject("aut-num:AS4294967207");

        final String response = DummyWhoisClient.query(NrtmServer.port, "-g TEST:3:2-4");

        assertThat(response, containsString("invalid range: Not within 1-1"));
    }

    @Test
    public void mirrorQueryWithLastKeyword() throws Exception {
        databaseHelper.addObject("aut-num:AS4294967207");
        databaseHelper.addObject("person:Denis Walker\nnic-hdl:DW-RIPE");
        databaseHelper.addObject("mntner:DEV-MNT");

        final String response = DummyWhoisClient.query(NrtmServer.port, "-g TEST:3:1-LAST");

        assertThat(response, containsString("ADD 3"));
        assertThat(response, containsString("DEV-MNT"));
    }
}
