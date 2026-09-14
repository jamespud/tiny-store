package com.github.spud.tinystore.gateway.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Parses an IP address literal, and nothing else.
 *
 * <p>Deliberately not {@link InetAddress#getByName(String)}: that falls through to a DNS lookup for
 * anything it cannot parse, and this class is fed attacker-controlled header values (see
 * {@link ClientIpResolver}). Parsing is done here instead, so an unparseable value is simply null --
 * never a network round trip, and never a name that resolves to an address the caller did not send.
 *
 * <p>Accepted forms: four-part dotted IPv4, and IPv6 with {@code ::} compression and an optional
 * trailing embedded IPv4 part ({@code ::ffff:203.0.113.9}).
 */
final class InetLiteral {

	private InetLiteral() {
	}

	/** The 4 or 16 address bytes, or null when the value is not an IP literal. */
	static byte[] parse(String value) {
		if (value == null) {
			return null;
		}
		String candidate = value.trim();
		if (candidate.isEmpty()) {
			return null;
		}
		int colon = candidate.indexOf(':');
		return normalizeMapped(colon < 0 ? parseIpv4(candidate) : parseIpv6(candidate));
	}

	/**
	 * An IPv4-mapped address ({@code ::ffff:203.0.113.9}) is the same host as {@code 203.0.113.9}
	 * and JDK prints it that way, so both are reduced to the 4-byte form here: one address must not
	 * produce two identities, nor fail an IPv4 CIDR comparison.
	 */
	private static byte[] normalizeMapped(byte[] address) {
		if (address == null || address.length != 16) {
			return address;
		}
		for (int i = 0; i < 10; i++) {
			if (address[i] != 0) {
				return address;
			}
		}
		if (address[10] != (byte) 0xff || address[11] != (byte) 0xff) {
			return address;
		}
		return new byte[] { address[12], address[13], address[14], address[15] };
	}

	/**
	 * Strips the {@code :port} suffix or {@code [..]} brackets some proxies add, so a header entry
	 * that carries a port still yields an address. Returns null when the result is not an IP literal.
	 */
	static String hostPart(String value) {
		if (value == null) {
			return null;
		}
		String candidate = value.trim();
		if (candidate.startsWith("[")) {
			int end = candidate.indexOf(']');
			return end > 1 ? candidate.substring(1, end) : null;
		}
		int colon = candidate.lastIndexOf(':');
		if (colon > 0 && candidate.indexOf(':') == colon) {
			String port = candidate.substring(colon + 1);
			if (!port.isEmpty() && port.chars().allMatch(Character::isDigit)) {
				return candidate.substring(0, colon);
			}
		}
		return candidate;
	}

	/** Canonical text form of parsed bytes (never does a lookup, unlike getByName). */
	static String canonical(byte[] address) {
		try {
			return InetAddress.getByAddress(address).getHostAddress();
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException("unreachable: the bytes came from an address literal", ex);
		}
	}

	private static byte[] parseIpv4(String candidate) {
		String[] parts = candidate.split("\\.", -1);
		if (parts.length != 4) {
			return null;
		}
		byte[] address = new byte[4];
		for (int i = 0; i < 4; i++) {
			String part = parts[i];
			if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
				return null;
			}
			int octet = Integer.parseInt(part);
			if (octet > 255) {
				return null;
			}
			address[i] = (byte) octet;
		}
		return address;
	}

	private static byte[] parseIpv6(String candidate) {
		String input = candidate;
		// A zone id ("fe80::1%eth0") is a local detail; keep the address, drop the scope.
		int zone = input.indexOf('%');
		if (zone >= 0) {
			input = input.substring(0, zone);
		}
		if (input.indexOf(':') < 0) {
			return null;
		}

		byte[] tail = null;
		int lastColon = input.lastIndexOf(':');
		String afterLastColon = input.substring(lastColon + 1);
		if (afterLastColon.contains(".")) {
			tail = parseIpv4(afterLastColon);
			if (tail == null) {
				return null;
			}
			// Drop the ':' that separated the embedded IPv4 part; the colon left behind by an
			// expression like "::ffff:203.0.113.9" would otherwise look like an empty group.
			input = input.substring(0, lastColon);
		}

		String[] halves = input.split("::", -1);
		if (halves.length > 2) {
			return null;
		}
		int[] head = parseGroups(halves[0]);
		int[] back = halves.length == 2 ? parseGroups(halves[1]) : new int[0];
		if (head == null || back == null) {
			return null;
		}

		int tailGroups = tail == null ? 0 : 2;
		int missing = 8 - head.length - back.length - tailGroups;
		if (halves.length == 1) {
			if (missing != 0) {
				return null;
			}
		}
		else if (missing < 1) {
			// "::" must stand for at least one group.
			return null;
		}

		int[] groups = new int[8];
		System.arraycopy(head, 0, groups, 0, head.length);
		for (int i = 0; i < back.length; i++) {
			groups[8 - tailGroups - back.length + i] = back[i];
		}
		if (tail != null) {
			groups[6] = (tail[0] & 0xFF) << 8 | (tail[1] & 0xFF);
			groups[7] = (tail[2] & 0xFF) << 8 | (tail[3] & 0xFF);
		}

		byte[] address = new byte[16];
		for (int i = 0; i < 8; i++) {
			address[i * 2] = (byte) (groups[i] >> 8);
			address[i * 2 + 1] = (byte) groups[i];
		}
		return address;
	}

	/** Hex groups separated by single colons; null when any group is malformed. */
	private static int[] parseGroups(String half) {
		if (half.isEmpty()) {
			return new int[0];
		}
		String[] parts = half.split(":", -1);
		int[] groups = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.isEmpty() || part.length() > 4 || !part.chars().allMatch(InetLiteral::isHexDigit)) {
				return null;
			}
			groups[i] = Integer.parseInt(part, 16);
		}
		return groups;
	}

	private static boolean isHexDigit(int character) {
		return Character.digit(character, 16) >= 0;
	}
}
