package org.jobits.ottos.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The client's IP for the per-IP login limit. Behind the hosting's proxy, it comes from the header the proxy itself
 * sets (ottos.security.client-ip-header, X-Real-Ip on Railway). The leftmost X-Forwarded-For entry is never used: the
 * client controls it and could change it on every attempt. Without the header configured, the connection's address.
 */
@Component
public class ClientIp {

    private final String header;

    public ClientIp(@Value("${ottos.security.client-ip-header:}") String header) {
        this.header = header == null ? "" : header.trim();
    }

    public String of(HttpServletRequest request) {
        if (!header.isEmpty()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
