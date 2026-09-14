package com.github.spud.tinystore.gateway.security;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import com.github.spud.tinystore.gateway.config.GatewayTrustedProxyProperties;

/**
 * The trust boundary for "who is this caller" (C16).
 *
 * <p>{@code X-Forwarded-For} is a client header. Keying the rate limiter on it -- as the gateway did
 * -- let a caller bypass the limiter by rotating the header (measured: 200 requests, 0x429) and let a
 * caller spend somebody else's quota by claiming their address. This resolver only believes the
 * header when the *socket peer* is a configured trusted proxy, and then only the hop that proxy is
 * responsible for:
 *
 * <ul>
 * <li>peer not trusted -> identity = peer address, {@code X-Forwarded-For} ignored completely;</li>
 * <li>peer trusted -> identity = the right-most chain entry that is not itself a trusted proxy, i.e.
 * the first hop the proxy could see; anything the client prepended to the left of it is discarded;</li>
 * <li>no usable peer address -> empty, and the caller decides what an unknown identity means.</li>
 * </ul>
 *
 * <p>Entries that are not IP literals are dropped rather than becoming limiter keys, so a malformed
 * header cannot mint a fresh identity. Parsing never resolves names (see {@link InetLiteral}), so a
 * caller cannot turn a request into a DNS lookup either.
 */
@Component
public class ClientIpResolver {

	private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

	private static final String FORWARDED_FOR = "X-Forwarded-For";

	private final List<CidrBlock> trustedProxies;

	public ClientIpResolver(GatewayTrustedProxyProperties properties) {
		List<CidrBlock> blocks = new ArrayList<>();
		for (String entry : properties.getTrustedProxies()) {
			if (!StringUtils.hasText(entry)) {
				continue;
			}
			blocks.add(CidrBlock.parse(entry.trim()));
		}
		this.trustedProxies = List.copyOf(blocks);
		log.info("Trusted proxies: {} (empty means X-Forwarded-For is ignored and the socket peer is "
			+ "used as the caller identity)", trustedProxies.isEmpty() ? "none" : trustedProxies);
	}

	/** Configured trusted-proxy blocks, in declaration order (diagnostics and tests). */
	public List<CidrBlock> getTrustedProxies() {
		return trustedProxies;
	}

	public Optional<String> resolveIdentity(ServerWebExchange exchange) {
		String peer = peerAddress(exchange);
		if (peer == null) {
			return Optional.empty();
		}
		if (!isTrusted(peer)) {
			return Optional.of(peer);
		}

		List<String> chain = forwardedChain(exchange);
		for (int i = chain.size() - 1; i >= 0; i--) {
			String candidate = chain.get(i);
			if (!isTrusted(candidate)) {
				return Optional.of(candidate);
			}
		}
		// The whole chain (or the whole header) is made of proxies we already know, so the peer
		// address is the most specific answer available.
		return Optional.of(peer);
	}

	private String peerAddress(ServerWebExchange exchange) {
		InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
		if (remoteAddress == null || remoteAddress.getAddress() == null) {
			return null;
		}
		return InetLiteral.canonical(remoteAddress.getAddress().getAddress());
	}

	/**
	 * Every {@code X-Forwarded-For} value flattened into single hops, left to right: the header may
	 * legally be repeated, and each value may carry a comma-separated list.
	 */
	private List<String> forwardedChain(ServerWebExchange exchange) {
		List<String> hops = new ArrayList<>();
		for (String header : exchange.getRequest().getHeaders().getOrDefault(FORWARDED_FOR, List.of())) {
			if (!StringUtils.hasText(header)) {
				continue;
			}
			for (String entry : header.split(",")) {
				String hop = canonicalOrNull(entry);
				if (hop == null) {
					log.debug("Ignoring unparseable {} entry '{}'", FORWARDED_FOR, entry);
					continue;
				}
				hops.add(hop);
			}
		}
		return hops;
	}

	private boolean isTrusted(String address) {
		byte[] parsed = address == null ? null : InetLiteral.parse(InetLiteral.hostPart(address));
		if (parsed == null) {
			return false;
		}
		for (CidrBlock block : trustedProxies) {
			if (block.contains(parsed)) {
				return true;
			}
		}
		return false;
	}

	private static String canonicalOrNull(String value) {
		byte[] parsed = InetLiteral.parse(InetLiteral.hostPart(value));
		return parsed == null ? null : InetLiteral.canonical(parsed);
	}

	/** A CIDR block, or a single address when no prefix length was given. */
	public static final class CidrBlock {

		private final byte[] network;
		private final int prefixLength;

		private CidrBlock(byte[] network, int prefixLength) {
			this.network = network;
			this.prefixLength = prefixLength;
		}

		static CidrBlock parse(String spec) {
			int slash = spec.lastIndexOf('/');
			String addressPart = slash < 0 ? spec : spec.substring(0, slash);
			byte[] address = InetLiteral.parse(InetLiteral.hostPart(addressPart));
			if (address == null) {
				throw new IllegalStateException("gateway.trusted-proxies entry '" + spec
					+ "' is not an IP address or CIDR block");
			}
			int bits = address.length * 8;
			int prefix = bits;
			if (slash >= 0) {
				try {
					prefix = Integer.parseInt(spec.substring(slash + 1).trim());
				}
				catch (NumberFormatException ex) {
					throw new IllegalStateException("gateway.trusted-proxies entry '" + spec
						+ "' has a non-numeric prefix length");
				}
			}
			if (prefix < 0 || prefix > bits) {
				throw new IllegalStateException("gateway.trusted-proxies entry '" + spec + "' has prefix length "
					+ prefix + ", expected 0.." + bits);
			}
			return new CidrBlock(address, prefix);
		}

		boolean contains(byte[] candidate) {
			if (candidate.length != network.length) {
				return false;
			}
			int fullBytes = prefixLength / 8;
			int remainingBits = prefixLength % 8;
			for (int i = 0; i < fullBytes; i++) {
				if (candidate[i] != network[i]) {
					return false;
				}
			}
			if (remainingBits == 0) {
				return true;
			}
			int mask = 0xFF << (8 - remainingBits);
			return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
		}

		@Override
		public String toString() {
			return InetLiteral.canonical(network) + "/" + prefixLength;
		}
	}
}
