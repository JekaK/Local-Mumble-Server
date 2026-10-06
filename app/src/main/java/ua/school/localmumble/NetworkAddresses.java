package ua.school.localmumble;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

final class NetworkAddresses {
    private NetworkAddresses() {}

    static List<String> localIpv4Addresses() {
        List<Address> found = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && (address.isSiteLocalAddress() || address.isLinkLocalAddress())
                            && priority(network.getName()) < 2) {
                        found.add(new Address(network.getName(), address.getHostAddress()));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        Collections.sort(found, Comparator.comparingInt(a -> priority(a.interfaceName)));
        List<String> result = new ArrayList<>();
        for (Address address : found) result.add(address.value);
        return result;
    }

    private static int priority(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("ap") || normalized.contains("wlan") || normalized.contains("swlan")) return 0;
        if (normalized.contains("eth")) return 1;
        return 2;
    }

    private static final class Address {
        final String interfaceName;
        final String value;

        Address(String interfaceName, String value) {
            this.interfaceName = interfaceName;
            this.value = value;
        }
    }
}
