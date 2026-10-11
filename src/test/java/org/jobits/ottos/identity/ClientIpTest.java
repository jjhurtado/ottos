package org.jobits.ottos.identity;

import org.jobits.ottos.identity.security.ClientIp;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    @Test
    void behindTheProxyTheIpComesFromItsOwnHeaderNeverFromTheForgeableForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-Ip", "190.6.1.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 190.6.1.1, 152.233.23.194");

        assertThat(new ClientIp("X-Real-Ip").of(request)).isEqualTo("190.6.1.1");
    }

    @Test
    void withoutTheHeaderItIsTheConnectionsAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(new ClientIp("").of(request)).isEqualTo("10.0.0.1");
        assertThat(new ClientIp("X-Real-Ip").of(request)).isEqualTo("10.0.0.1");
    }
}
