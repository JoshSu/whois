package net.ripe.db.whois.update.dns;

import javax.annotation.concurrent.Immutable;

@Immutable
public class DnsCheckRequest {
    private final String domain;
    private final String glue;

    DnsCheckRequest(final String domain, final String glue) {
        this.domain = domain;
        this.glue = glue;
    }

    public String getDomain() {
        return domain;
    }

    public String getGlue() {
        return glue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DnsCheckRequest that = (DnsCheckRequest) o;

        return !(!domain.equals(that.domain) || !glue.equals(that.glue));
    }

    @Override
    public int hashCode() {
        int result = domain.hashCode();
        result = 31 * result + glue.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return domain + " " + glue;
    }
}
