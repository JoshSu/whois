package net.ripe.db.whois.internal.api.acl;

import com.google.common.collect.Lists;
import net.ripe.db.whois.common.ip.IpInterval;
import net.ripe.db.whois.common.etree.IntersectingIntervalException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AclLimitServiceTest {
    @Mock AclServiceDao aclServiceDao;
    @InjectMocks AclLimitService subject;

    @Test
    public void getLimits() {
        List<Limit> limits = Lists.newArrayList(new Limit());
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final List<Limit> list = subject.getLimits();
        assertThat(list, is(limits));
    }

    @Test
    public void getLimit_exact() {
        List<Limit> limits = Lists.newArrayList(new Limit("0/0", "", 1000, false));
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Response response = subject.getLimit("0/0");
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(response.getEntity(), instanceOf(Limit.class));
        assertThat(((Limit) response.getEntity()).getPrefix(), is("0.0.0.0/0"));
    }

    @Test
    public void getLimit_parent() {
        List<Limit> limits = Lists.newArrayList(new Limit("0/0", "", 1000, false));
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Response response = subject.getLimit("10.0.0.0/32");
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(response.getEntity(), instanceOf(Limit.class));
        assertThat(((Limit) response.getEntity()).getPrefix(), is("0.0.0.0/0"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getLimit_invalid() {
        List<Limit> limits = Lists.newArrayList(new Limit("0/0", "", 1000, false));
        when(aclServiceDao.getLimits()).thenReturn(limits);

        subject.getLimit("0");
    }

    @Test
    public void saveLimit_create() {
        List<Limit> limits = Lists.newArrayList(new Limit("0/0", "", 1000, false));
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Limit limit = new Limit("10.0.0.0/32", "some", 1000, false);
        final Response response = subject.saveLimit(limit);
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(response.getEntity(), instanceOf(Limit.class));
        assertThat(((Limit) response.getEntity()).getPrefix(), is("10.0.0.0/32"));

        verify(aclServiceDao).createLimit(limit);
    }

    @Test(expected = IntersectingIntervalException.class)
    public void saveLimit_create_intersection() {
        List<Limit> limits = Lists.newArrayList(
                new Limit("0/0", "", 1000, false),
                new Limit("10.0.0.0 - 10.0.0.10", "", 1000, true)
        );
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Limit limit = new Limit("10.0.0.2 - 10.0.0.11", "some", 1000, false);
        subject.saveLimit(limit);
    }

    @Test
    public void saveLimit_modify() {
        List<Limit> limits = Lists.newArrayList(
                new Limit("0/0", "", 1000, false),
                new Limit("10.0.0.0/32", "", 1000, true)
        );
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Limit limit = new Limit("10.0.0.0/32", "some", 10000, false);
        final Response response = subject.saveLimit(limit);
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(response.getEntity(), instanceOf(Limit.class));
        assertThat(((Limit) response.getEntity()).getPrefix(), is("10.0.0.0/32"));

        verify(aclServiceDao).updateLimit(limit);
    }

    @Test
    public void deleteLimit_root_IPv4() {
        List<Limit> limits = Lists.newArrayList(
                new Limit("0/0", "", 1000, false),
                new Limit("10.0.0.0/32", "", 1000, true)
        );
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Response response = subject.deleteLimit("0/0");
        assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));

        verifyZeroInteractions(aclServiceDao);
    }

    @Test
    public void deleteLimit_root_IPv6() {
        List<Limit> limits = Lists.newArrayList(new Limit("::0/0", "", 1000, false));
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Response response = subject.deleteLimit("::0/0");
        assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));

        verifyZeroInteractions(aclServiceDao);
    }

    @Test
    public void deleteLimit_unknown() {
        List<Limit> limits = Lists.newArrayList(new Limit("0/0", "", 1000, false));
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Response response = subject.deleteLimit("10.0.0.0/32");
        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));

        verify(aclServiceDao, never()).deleteLimit(any(IpInterval.class));
    }

    @Test
    public void deleteLimit() {
        List<Limit> limits = Lists.newArrayList(
                new Limit("0/0", "", 1000, false),
                new Limit("10.0.0.0/32", "", 1000, true)
        );
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Response response = subject.deleteLimit("10.0.0.0/32");
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

        verify(aclServiceDao).deleteLimit(IpInterval.parse("10.0.0.0/32"));
    }

    @Test
    public void deleteLimitWithEncodedURL() {
        List<Limit> limits = Lists.newArrayList(
                new Limit("10.0.0.0/32", "", 100, false)
        );
        when(aclServiceDao.getLimits()).thenReturn(limits);

        final Response response = subject.deleteLimit("10.0.0.0%2F32");
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

        verify(aclServiceDao).deleteLimit(IpInterval.parse("10.0.0.0/32"));
    }
}
