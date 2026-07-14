package net.bananemdnsa.historystages.util.lock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player actionbar feedback for lock events with a per-category cooldown.
 * Replaces the duplicated cooldown maps that previously lived in each lock handler.
 * Categories keep their own cooldown buckets so a block-lock message is not suppressed
 * by a recent item-lock message and vice versa.
 */
public final class LockFeedback {

    public static final long DEFAULT_COOLDOWN_MS = 2000;

    private static final Map<String, Map<UUID, Long>> COOLDOWNS = new HashMap<>();

    private LockFeedback() {}

    public static void sendActionbar(ServerPlayer player, String category, MutableComponent message) {
        if (!checkCooldown(player.getUUID(), category, DEFAULT_COOLDOWN_MS)) return;
        player.displayClientMessage(
                message.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                true
        );
    }

    /**
     * Returns true and records the timestamp if the cooldown for (player, category) has elapsed,
     * false otherwise. Exposed for callers that need to gate non-message side effects on the same
     * cooldown window.
     */
    public static boolean checkCooldown(UUID playerUuid, String category, long cooldownMs) {
        Map<UUID, Long> bucket = COOLDOWNS.computeIfAbsent(category, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long last = bucket.get(playerUuid);
        if (last != null && (now - last) < cooldownMs) return false;
        bucket.put(playerUuid, now);
        return true;
    }
}
