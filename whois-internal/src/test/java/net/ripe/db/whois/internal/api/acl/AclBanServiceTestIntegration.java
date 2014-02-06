package net.ripe.db.whois.internal.api.acl;

import net.ripe.db.whois.api.RestTest;
import net.ripe.db.whois.api.rest.RestClientUtils;
import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.domain.BlockEvent;
import net.ripe.db.whois.internal.AbstractInternalTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category(IntegrationTest.class)
public class AclBanServiceTestIntegration extends AbstractInternalTest {
    private static final String BANS_PATH = "api/acl/bans";

    @Before
    public void setUp() throws Exception {
        databaseHelper.insertAclIpDenied("10.0.0.0/32");
        databaseHelper.insertApiKey(apiKey, "/api/acl", "acl api key");
    }

    @Test
    public void bans() throws Exception {
        databaseHelper.insertAclIpDenied("10.0.0.1/32");
        databaseHelper.insertAclIpDenied("10.0.0.2/32");
        databaseHelper.insertAclIpDenied("2001::/64");

        assertThat(getBans(), hasSize(4));
    }

    @Test
    public void createBan() throws Exception {
        final Ban ban = RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "test"), MediaType.APPLICATION_JSON_TYPE), Ban.class);

        assertThat(ban.getPrefix(), is("10.0.0.1/32"));
        assertThat(ban.getComment(), is("test"));

        final List<Ban> bans = getBans();
        assertThat(bans, hasSize(2));

        final List<BanEvent> banEvents = getBanEvents("10.0.0.1/32");
        assertThat(banEvents, hasSize(1));
        final BanEvent banEvent = banEvents.get(0);
        assertThat(banEvent.getPrefix(), is("10.0.0.1/32"));
        assertThat(banEvent.getType(), is(BlockEvent.Type.BLOCK_PERMANENTLY));
    }

    @Test
    public void createBanWithIPv6AfterNormalisingPrefix() throws Exception {

        final Ban ban = RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("2001:db8:1f15:d79:1511:ed4a:b5bc:4420/64", "test"), MediaType.APPLICATION_JSON_TYPE), Ban.class);

        assertThat(ban.getPrefix(), is("2001:db8:1f15:d79::/64"));
        assertThat(ban.getComment(), is("test"));

        final List<Ban> bans = getBans();
        assertThat(bans, hasSize(2));

        final List<BanEvent> banEvents = getBanEvents("2001:db8:1f15:d79::/64");
        assertThat(banEvents, hasSize(1));
        final BanEvent banEvent = banEvents.get(0);
        assertThat(banEvent.getPrefix(), is("2001:db8:1f15:d79::/64"));
        assertThat(banEvent.getType(), is(BlockEvent.Type.BLOCK_PERMANENTLY));
    }

    @Test
    public void createBanWithoutPrefixLength() throws Exception {
        final Ban ban = RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1", "test"), MediaType.APPLICATION_JSON_TYPE), Ban.class);

        assertThat(ban.getPrefix(), is("10.0.0.1/32"));
        assertThat(ban.getComment(), is("test"));
    }

    @Test
    public void createBanWithInvalidPrefixLength() throws Exception {
        try {
            RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.1.2.0/24", "comment", new Date()), MediaType.APPLICATION_JSON_TYPE), String.class);
            fail();
        } catch (BadRequestException e) {
            assertThat(e.getResponse().readEntity(String.class), is("IPv4 must be a single address"));
        }
    }

    @Test
    public void createBanWithInvalidDateDoesNotReturnStackTrace() throws Exception {
        String banEntity = "{\n" +
                "  \"prefix\" : \"10.1.2.1/32\",\n" +
                "  \"comment\" : \"Just Testing\",\n" +
                "  \"since\" : \"invalid\"\n" +
                "}";
        try {
            RestTest.target(getPort(), BANS_PATH, null, apiKey)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.entity(banEntity, MediaType.APPLICATION_JSON), String.class);
            fail();
        } catch (BadRequestException e) {
            assertThat(e.getResponse().readEntity(String.class), containsString("Can not construct instance of java.util.Date from String value 'invalid': not a valid representation"));
        }
    }

    @Test
    public void updateBan() throws Exception {
        RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "test"), MediaType.APPLICATION_JSON_TYPE));

        final Ban ban = RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "updated"), MediaType.APPLICATION_JSON_TYPE), Ban.class);

        assertThat(ban.getPrefix(), is("10.0.0.1/32"));
        assertThat(ban.getComment(), is("updated"));

        final List<Ban> bans = getBans();
        assertThat(bans, hasSize(2));

        final List<BanEvent> banEvents = getBanEvents("10.0.0.1/32");
        assertThat(banEvents, hasSize(1));
        final BanEvent banEvent = banEvents.get(0);
        assertThat(banEvent.getPrefix(), is("10.0.0.1/32"));
        assertThat(banEvent.getType(), is(BlockEvent.Type.BLOCK_PERMANENTLY));
    }

    @Test
    public void updateBanWithoutPrefixLength() throws Exception {
        RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "test"), MediaType.APPLICATION_JSON_TYPE));

        final Ban ban = RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1", "updated"), MediaType.APPLICATION_JSON_TYPE), Ban.class);

        assertThat(ban.getPrefix(), is("10.0.0.1/32"));
        assertThat(ban.getComment(), is("updated"));
    }

    @Test
    public void deleteBan() throws Exception {
        final Ban ban = RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "test"), MediaType.APPLICATION_JSON_TYPE), Ban.class);

        plusOneDay();

        final Ban deletedBan = RestTest.target(getPort(), BANS_PATH, ban.getPrefix(), null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .delete(Ban.class);

        assertThat(deletedBan.getPrefix(), is("10.0.0.1/32"));
        assertThat(deletedBan.getComment(), is("test"));

        final List<Ban> bans = getBans();
        assertThat(bans, hasSize(1));

        final List<BanEvent> banEvents = getBanEvents("10.0.0.1/32");
        assertThat(banEvents, hasSize(2));
        assertThat(banEvents.get(0).getType(), is(BlockEvent.Type.UNBLOCK));
        assertThat(banEvents.get(1).getType(), is(BlockEvent.Type.BLOCK_PERMANENTLY));
    }

    @Test
    public void deleteBanWithoutPrefixLength() throws Exception {
        RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "test"), MediaType.APPLICATION_JSON_TYPE));

        plusOneDay();

        final Ban deletedBan = RestTest.target(getPort(), BANS_PATH, "10.0.0.1", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .delete(Ban.class);

        assertThat(deletedBan.getPrefix(), is("10.0.0.1/32"));
        assertThat(deletedBan.getComment(), is("test"));

        assertThat(getBans(), hasSize(1));
    }

    @Test
    public void deleteBanUnencodedPrefix() throws Exception {
        databaseHelper.insertAclIpDenied("2a01:488:67:1000::/64");

        RestTest.target(getPort(), BANS_PATH + "/2a01:488:67:1000::/64", null, apiKey).request().delete();

        assertThat(getBans(), hasSize(1));
    }

    @Test
    public void deleteBanUnencodedPrefixWithExtension() throws Exception {
        databaseHelper.insertAclIpDenied("2a01:488:67:1000::/64");

        RestTest.target(getPort(), BANS_PATH + "/2a01:488:67:1000::/64.json", null, apiKey).request().delete();

        assertThat(getBans(), hasSize(1));
    }

    @Test
    public void getBan() throws Exception {
        final Ban ban = RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "test"), MediaType.APPLICATION_JSON_TYPE), Ban.class);

        plusOneDay();

        final Ban createdBan = RestTest.target(getPort(), BANS_PATH, ban.getPrefix(), null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(Ban.class);

        assertThat(createdBan.getPrefix(), is("10.0.0.1/32"));
        assertThat(createdBan.getComment(), is("test"));
    }

    @Test
    public void getBanWithoutPrefixLength() throws Exception {
        RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(new Ban("10.0.0.1/32", "test"), MediaType.APPLICATION_JSON_TYPE));


        plusOneDay();

        final Ban createdBan = RestTest.target(getPort(), BANS_PATH, "10.0.0.1", null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(Ban.class);

        assertThat(createdBan.getPrefix(), is("10.0.0.1/32"));
        assertThat(createdBan.getComment(), is("test"));
    }

    @SuppressWarnings("unchecked")
    private List<Ban> getBans() {
        return RestTest.target(getPort(), BANS_PATH, null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(new GenericType<List<Ban>>() {});
    }

    @SuppressWarnings("unchecked")
    private List<BanEvent> getBanEvents(final String prefix) {
        return RestTest.target(getPort(), String.format("%s/%s/events", BANS_PATH, RestClientUtils.encode(prefix)), null, apiKey)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(new GenericType<List<BanEvent>>() {
                });
    }

    private void plusOneDay() {
        testDateTimeProvider.setTime(testDateTimeProvider.getCurrentDateTime().plusDays(1));
    }
}
