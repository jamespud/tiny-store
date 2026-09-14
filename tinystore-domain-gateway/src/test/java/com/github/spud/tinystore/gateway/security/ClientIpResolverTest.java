package com.github.spud.tinystore.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.github.spud.tinystore.gateway.config.GatewayTrustedProxyProperties;

/**
 * C16: the rate limiter's identity must not be something the caller can choose. These cases are the
 * trust boundary itself -- an untrusted peer's header is ignored, a trusted proxy's header is
 * believed only up to the hop it is responsible for.
 */
@DisplayName("client ip resolution")
class ClientIpResolverTest {

    private static final String CLIENT = "203.0.113.9";

    @Test
    @DisplayName("an untrusted caller cannot choose its identity with X-Forwarded-For")
    void untrustedCallerHeaderIsIgnored() {
        ClientIpResolver resolver = resolver();  // no trusted proxies configured

        assertThat(identity(resolver, "198.51.100.7", CLIENT)).isEqualTo("198.51.100.7");
        assertThat(identity(resolver, "198.51.100.7", "10.0.0.1"))
            .withFailMessage("a client-supplied header was believed; rotating it would bypass the limiter")
            .isEqualTo("198.51.100.7");
        // The forged chain must not become a key either.
        assertThat(identity(resolver, "198.51.100.7", CLIENT + ", 10.0.0.1, 10.0.0.2"))
            .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("a trusted proxy's X-Forwarded-For is used, up to the first untrusted hop")
    void trustedProxyHeaderIsUsed() {
        ClientIpResolver resolver = resolver("10.0.0.0/8", "172.16.0.0/12");

        assertThat(identity(resolver, "10.0.0.5", CLIENT)).isEqualTo(CLIENT);
        // Client -> proxy -> gateway: the left-most entry is attacker-controlled and must be ignored.
        assertThat(identity(resolver, "10.0.0.5", "1.2.3.4, " + CLIENT)).isEqualTo(CLIENT);
        // Trusted proxies chain further; keep walking right-to-left past them.
        assertThat(identity(resolver, "10.0.0.5", "1.2.3.4, " + CLIENT + ", 172.16.3.9")).isEqualTo(CLIENT);
        // Header present but entirely made of known proxies -> fall back to the socket peer.
        assertThat(identity(resolver, "10.0.0.5", "172.16.3.9")).isEqualTo("10.0.0.5");
        // No header at all.
        assertThat(identity(resolver, "10.0.0.5", null)).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("the proxy can be named by CIDR, single address, or include its port")
    void trustedProxyMatching() {
        assertThat(identity(resolver("172.16.0.0/12"), "172.31.255.254", CLIENT)).isEqualTo(CLIENT);
        // 172.32.0.1 is outside 172.16.0.0/12 (172.16.0.0 - 172.31.255.255).
        assertThat(identity(resolver("172.16.0.0/12"), "172.32.0.1", CLIENT)).isEqualTo("172.32.0.1");
        assertThat(identity(resolver("10.10.10.10"), "10.10.10.10", CLIENT)).isEqualTo(CLIENT);
        // Proxies sometimes append the port to the forwarded address; it must still match and not
        // become part of the limiter key.
        assertThat(identity(resolver("10.10.10.0/24"), "10.10.10.77", CLIENT + ":51234")).isEqualTo(CLIENT);
        assertThat(identity(resolver("::1/128"), "[::1]", CLIENT)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("a malformed header entry cannot mint an identity")
    void malformedEntriesAreDropped() {
        ClientIpResolver resolver = resolver("10.0.0.0/8");

        // Not an IP literal (a hostname would need a DNS lookup to compare against a CIDR).
        assertThat(identity(resolver, "10.0.0.5", "attacker.example.com")).isEqualTo("10.0.0.5");
        assertThat(identity(resolver, "10.0.0.5", "999.1.1.1")).isEqualTo("10.0.0.5");
        assertThat(identity(resolver, "10.0.0.5", "not-an-ip, " + CLIENT)).isEqualTo(CLIENT);
        assertThat(identity(resolver, "10.0.0.5", "  ")).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("IPv6 peers and hops are canonicalised consistently")
    void ipv6IsCanonicalised() {
        ClientIpResolver resolver = resolver("10.0.0.0/8");

        assertThat(identity(resolver, "[::1]", null))
            .isEqualTo(identity(resolver, "10.0.0.5", "0:0:0:0:0:0:0:1"));
        // Two spellings of the same address must not mint two limiter keys.
        assertThat(identity(resolver, "10.0.0.5", "0:0:0:0:0:ffff:cb00:7109"))
            .isEqualTo(identity(resolver, "10.0.0.5", "::ffff:203.0.113.9"));
    }

    @Test
    @DisplayName("no socket address means no identity, not a guess")
    void missingPeerYieldsNoIdentity() {
        GatewayTrustedProxyProperties properties = new GatewayTrustedProxyProperties();
        properties.setTrustedProxies(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/order/trades").build());

        assertThat(resolver.resolveIdentity(exchange)).isEmpty();
    }

    @Test
    @DisplayName("a bad trusted-proxies setting fails at startup instead of silently trusting nobody")
    void invalidTrustedProxyConfigFailsFast() {
        assertThatThrownBy(() -> resolver("172.16.0.0/33"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("prefix length");
        assertThatThrownBy(() -> resolver("proxy.example.com"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not an IP address");
        assertThatThrownBy(() -> resolver("10.0.0.0/x"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-numeric prefix");
    }

    @Test
    @DisplayName("blank entries are ignored, so an unset environment variable is not an error")
    void blankEntriesAreIgnored() {
        assertThat(resolver("", "  ", "10.0.0.0/8").getTrustedProxies()).hasSize(1);
        assertThat(resolver("").getTrustedProxies()).isEmpty();
    }

    private static String identity(ClientIpResolver resolver, String peer, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/order/trades")
            .remoteAddress(new InetSocketAddress(peer.replace("[", "").replace("]", ""), 51234));
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return resolver.resolveIdentity(MockServerWebExchange.from(builder.build())).orElse(null);
    }

    private static ClientIpResolver resolver(String... trustedProxies) {
        GatewayTrustedProxyProperties properties = new GatewayTrustedProxyProperties();
        properties.setTrustedProxies(List.of(trustedProxies));
        return new ClientIpResolver(properties);
    }
}
