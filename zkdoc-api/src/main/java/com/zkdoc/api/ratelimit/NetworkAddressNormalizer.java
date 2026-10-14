package com.zkdoc.api.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility for normalizing IPv4 and IPv6 client network addresses.
 * Handles IPv4-mapped IPv6 addresses, trims port numbers, and formats canonical representation.
 */
public final class NetworkAddressNormalizer {

    private NetworkAddressNormalizer() {}

    public static String normalize(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return "unknown";
        }

        String cleaned = rawIp.trim();

        // If enclosed in brackets (e.g. [::1]:8080), strip port
        if (cleaned.startsWith("[") && cleaned.contains("]")) {
            cleaned = cleaned.substring(1, cleaned.indexOf(']'));
        } else if (cleaned.contains(":") && cleaned.indexOf(':') == cleaned.lastIndexOf(':')) {
            // IPv4 with port (e.g. 192.168.1.1:8080)
            cleaned = cleaned.substring(0, cleaned.indexOf(':'));
        }

        try {
            InetAddress addr = InetAddress.getByName(cleaned);
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            // Safe fallback sanitized alphanumeric
            return cleaned.replaceAll("[^A-Za-z0-9.:_-]", "_");
        }
    }
}
