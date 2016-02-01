package net.ripe.db.whois.api.rest;

import com.google.common.base.Splitter;
import net.ripe.db.whois.api.rest.mapper.*;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;

public class RestServiceHelper {
    private static final Splitter AMPERSAND_SPLITTER = Splitter.on('&').omitEmptyStrings();
    private static final Splitter EQUALS_SPLITTER = Splitter.on('=').omitEmptyStrings();

    private RestServiceHelper() {
        // do not instantiate
    }

    public static String getRequestURL(final HttpServletRequest request) {
        final String queryString = request.getQueryString();
        final StringBuffer requestURL = request.getRequestURL();

        if (StringUtils.isBlank(queryString)) {
            return requestURL.toString();
        }

        final StringBuilder builder = new StringBuilder(requestURL);
        char separator = '?';

        for (String next : AMPERSAND_SPLITTER.split(queryString)) {
            final Iterator<String> iterator = EQUALS_SPLITTER.split(next).iterator();
            if (iterator.hasNext() && iterator.next().equalsIgnoreCase("password")) {
                continue;
            }

            builder.append(separator).append(next);
            separator = '&';
        }

        return builder.toString();
    }

    public static boolean isQueryParamSet(final String queryString, final String key) {
        if (queryString == null) {
            return false;
        }

        for (String next : AMPERSAND_SPLITTER.split(queryString)) {
            final Iterator<String> iterator = EQUALS_SPLITTER.split(next).iterator();
            if (iterator.hasNext()) {
                if (iterator.next().equals(key)) {
                    if (!iterator.hasNext()) return true;

                    String value = iterator.next();
                    if (StringUtils.isEmpty(value) || value.equalsIgnoreCase("true")) return true;
                }
            }
        }

        return false;
    }

    public static Class<? extends AttributeMapper> getServerAttributeMapper(String queryString){
        return isQueryParamSet(queryString, "unformatted") ?
                DirtyServerAttributeMapper.class : FormattedServerAttributeMapper.class;
    }

    public static Class<? extends AttributeMapper> getRestResponseAttributeMapper(String queryString){
        return isQueryParamSet(queryString, "unformatted") ?
                DirtySuppressChangedAttributeMapper.class : RegularSuppressChangedAttributeMapper.class;
    }

}
