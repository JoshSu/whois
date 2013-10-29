package net.ripe.db.whois.internal.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.google.common.collect.Lists;
import net.ripe.db.whois.api.whois.WhoisObjectMapper;
import net.ripe.db.whois.api.whois.domain.WhoisResources;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslObject;
import org.apache.commons.lang.StringUtils;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

@Component
public final class RestClient {
    private static final Client client;
    private String restApiUrl;
    private String sourceName;
    private WhoisObjectMapper whoisObjectMapper;

    static {
        final JacksonJaxbJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
        jsonProvider.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);
        jsonProvider.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        client = ClientBuilder.newBuilder()
                .register(MultiPartFeature.class)
                .register(jsonProvider)
                .build();
    }

    @Value("${api.rest.baseurl}")
    public void setRestApiUrl(final String restApiUrl) {
        this.restApiUrl = restApiUrl;
        this.whoisObjectMapper = new WhoisObjectMapper(null, restApiUrl);
    }

    @Value("${whois.source}")
    public void setSource(final String sourceName) {
        this.sourceName = sourceName;
    }

    public RpslObject create(final RpslObject rpslObject, final String override) {
        final WhoisResources whoisResources = client.target(String.format("%s/%s/%s%s",
                restApiUrl,
                sourceName,
                rpslObject.getType().getName(),
                StringUtils.isNotEmpty(override) ? String.format("?override=%s", override) : ""))
                .request()
                .post(Entity.entity(whoisObjectMapper.map(Lists.newArrayList(rpslObject)), MediaType.APPLICATION_XML), WhoisResources.class);
        return whoisObjectMapper.map(whoisResources.getWhoisObjects().get(0));
    }

    public RpslObject lookup(final ObjectType objectType, final String pkey) {
        final WhoisResources whoisResources = client.target(String.format("%s/%s/%s/%s?unfiltered",
                restApiUrl,
                sourceName,
                objectType.getName(),
                pkey)).request()
                .get(WhoisResources.class);
        return whoisObjectMapper.map(whoisResources.getWhoisObjects().get(0));
    }

    public RpslObject update(final RpslObject rpslObject, final String override) {
        final WhoisResources whoisResources = client.target(String.format("%s/%s/%s/%s%s",
                restApiUrl,
                sourceName,
                rpslObject.getType().getName(),
                rpslObject.getKey().toString(),
                StringUtils.isNotEmpty(override) ? String.format("?override=%s", override) : ""))
                .request()
                .put(Entity.entity(whoisObjectMapper.map(Lists.newArrayList(rpslObject)), MediaType.APPLICATION_XML), WhoisResources.class);
        return whoisObjectMapper.map(whoisResources.getWhoisObjects().get(0));
    }

    public void delete(final RpslObject rpslObject, final String override) {
        client.target(String.format("%s/%s/%s/%s%s",
                restApiUrl,
                sourceName,
                rpslObject.getType().getName(),
                rpslObject.getKey().toString(),
                StringUtils.isNotEmpty(override) ? String.format("?override=%s", override) : ""))
                .request()
                .delete(String.class);
    }

}
