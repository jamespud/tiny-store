package com.github.spud.tinystore.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who is allowed to speak for someone else (C16).
 *
 * <pre>
 * gateway:
 *   trusted-proxies:
 *     - 172.16.0.0/12
 * </pre>
 *
 * <p>An empty list is the right default and the only safe setting for a deployment whose clients
 * reach the gateway directly: the gateway then ignores {@code X-Forwarded-For} entirely and keys the
 * rate limiter on the socket peer. See {@code ClientIpResolver}.
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayTrustedProxyProperties {

	private List<String> trustedProxies = new ArrayList<>();

	public List<String> getTrustedProxies() {
		return trustedProxies;
	}

	public void setTrustedProxies(List<String> trustedProxies) {
		this.trustedProxies = trustedProxies;
	}
}
