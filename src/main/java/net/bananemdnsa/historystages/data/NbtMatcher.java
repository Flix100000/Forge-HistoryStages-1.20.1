package net.bananemdnsa.historystages.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Matches ItemStack NBT data against JSON-defined NBT criteria.
 * In MC 1.21+, uses DataComponents.CUSTOM_DATA instead of direct stack.getTag().
 * Supports exact matching and numeric ranges (e.g., "1-4" matches 1, 2, 3, 4).
 */
public class NbtMatcher {

    /**
     * Checks if an ItemStack's custom data matches the given criteria.
     * All keys in the criteria must be present and match in the item's data.
     */
    public static boolean matches(ItemStack stack, JsonObject nbtCriteria) {
        if (nbtCriteria == null || nbtCriteria.size() == 0) return true;

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        // Try matching against custom data
        if (!tag.isEmpty() && matchesCompound(tag, nbtCriteria)) return true;

        return false;
    }

    private static boolean matchesCompound(CompoundTag tag, JsonObject criteria) {
        for (var entry : criteria.entrySet()) {
            String key = entry.getKey();
            JsonElement expected = entry.getValue();

            if (!tag.contains(key)) return false;

            Tag nbtValue = tag.get(key);
            if (!matchesElement(nbtValue, expected)) return false;
        }
        return true;
    }

    private static boolean matchesElement(Tag nbtValue, JsonElement expected) {
        if (expected.isJsonObject()) {
            if (!(nbtValue instanceof CompoundTag compound)) return false;
            return matchesCompound(compound, expected.getAsJsonObject());
        }

        if (expected.isJsonArray()) {
            if (!(nbtValue instanceof ListTag list)) return false;
            return matchesArray(list, expected.getAsJsonArray());
        }

        if (expected.isJsonPrimitive()) {
            JsonPrimitive prim = expected.getAsJsonPrimitive();

            // Range support for numeric values: "1-4" matches 1, 2, 3, 4
            if (prim.isString()) {
                String str = prim.getAsString();
                if (str.matches("\\d+-\\d+") && nbtValue instanceof NumericTag numeric) {
                    return matchesRange(numeric.getAsInt(), str);
                }
                // String comparison
                if (nbtValue instanceof StringTag) {
                    return nbtValue.getAsString().equals(str);
                }
                return false;
            }

            if (prim.isNumber()) {
                if (nbtValue instanceof NumericTag numeric) {
                    return numeric.getAsNumber().doubleValue() == prim.getAsDouble();
                }
                return false;
            }

            if (prim.isBoolean()) {
                if (nbtValue instanceof ByteTag byteTag) {
                    return (byteTag.getAsByte() != 0) == prim.getAsBoolean();
                }
                return false;
            }
        }

        return false;
    }

    private static boolean matchesArray(ListTag list, JsonArray criteria) {
        // Each element in the criteria array must match at least one element in the NBT list
        for (JsonElement expected : criteria) {
            boolean found = false;
            for (Tag nbtElement : list) {
                if (matchesElement(nbtElement, expected)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean matchesRange(int value, String rangeStr) {
        String[] parts = rangeStr.split("-");
        int min = Integer.parseInt(parts[0]);
        int max = Integer.parseInt(parts[1]);
        return value >= min && value <= max;
    }
}
