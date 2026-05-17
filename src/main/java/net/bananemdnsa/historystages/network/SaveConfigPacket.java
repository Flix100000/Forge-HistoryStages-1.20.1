package net.bananemdnsa.historystages.network;

import java.util.Map;

public record SaveConfigPacket(Map<String, String> clientValues, Map<String, String> commonValues) {
}
