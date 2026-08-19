package net.bananemdnsa.historystages.data.lock.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;

/**
 * Decides whether a stage gates a given enchantment at a given level.
 *
 * <p>Enchantment locks are not a category of their own: they are expressed as a
 * {@code minecraft:enchanted_book} item entry whose NBT carries {@code StoredEnchantments}.
 * A missing {@code lvl} means every level is gated; a number means that exact level; a
 * {@code "min-max"} string means an inclusive range.
 */
public final class EnchantmentLockMatcher {

    private EnchantmentLockMatcher() {}

    public static boolean locksEnchantment(StageEntry stage, String enchantmentId, int level) {
        for (ItemEntry itemEntry : stage.getItemEntries()) {
            if (!itemEntry.hasNbt()) continue;
            if (!itemEntry.getId().equals("minecraft:enchanted_book")) continue;

            JsonObject nbt = itemEntry.getNbt();
            if (!nbt.has("StoredEnchantments") || !nbt.get("StoredEnchantments").isJsonArray()) continue;

            JsonArray enchantments = nbt.getAsJsonArray("StoredEnchantments");
            for (JsonElement el : enchantments) {
                if (!el.isJsonObject()) continue;
                JsonObject enchObj = el.getAsJsonObject();
                if (!enchObj.has("id")) continue;

                String lockedId = enchObj.get("id").getAsString();
                if (!lockedId.equals(enchantmentId)) continue;

                if (!enchObj.has("lvl")) return true; // no level restriction = all levels locked

                JsonElement lvlEl = enchObj.get("lvl");
                if (lvlEl.isJsonPrimitive()) {
                    if (lvlEl.getAsJsonPrimitive().isNumber()) {
                        if (lvlEl.getAsInt() == level) return true;
                    } else if (lvlEl.getAsJsonPrimitive().isString()) {
                        String lvlStr = lvlEl.getAsString();
                        if (lvlStr.matches("\\d+-\\d+")) {
                            String[] parts = lvlStr.split("-");
                            int min = Integer.parseInt(parts[0]);
                            int max = Integer.parseInt(parts[1]);
                            if (level >= min && level <= max) return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
