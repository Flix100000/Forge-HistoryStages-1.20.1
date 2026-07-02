package net.bananemdnsa.historystages.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.bananemdnsa.historystages.client.ClientRegistryAccessHelper;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Matches ItemStack NBT data against JSON-defined NBT criteria.
 * <p>
 * In MC 1.21+ this reads {@link DataComponents#CUSTOM_DATA} and additionally
 * synthesizes two virtual sub-trees so user-friendly JSON configs keep working:
 * <ul>
 *     <li>Legacy {@code Enchantments} / {@code StoredEnchantments} lists from
 *         the vanilla enchantment components.</li>
 *     <li>A {@code components} sub-object containing any data component the
 *         criteria references, encoded via its own codec. This lets users
 *         match against mod-defined components (e.g.
 *         {@code irons_spellbooks:spell_container}).</li>
 * </ul>
 * Supports exact matching and numeric ranges (e.g., "1-4" matches 1, 2, 3, 4).
 */
public class NbtMatcher {

    /**
     * Checks if an ItemStack's data matches the given criteria.
     * All keys in the criteria must be present and match in the item's data.
     */
    public static boolean matches(ItemStack stack, JsonObject nbtCriteria) {
        if (nbtCriteria == null || nbtCriteria.size() == 0) return true;

        CompoundTag tag = buildMatchTag(stack, nbtCriteria);
        return !tag.isEmpty() && matchesCompound(tag, nbtCriteria);
    }

    private static CompoundTag buildMatchTag(ItemStack stack, JsonObject criteria) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        // Legacy vanilla enchantment lists (kept for backward-compat with older
        // JSON configs that used the pre-1.20.5 NBT layout).
        if (criteria.has("Enchantments") && !tag.contains("Enchantments")) {
            ItemEnchantments active = stack.get(DataComponents.ENCHANTMENTS);
            if (active != null && !active.isEmpty()) {
                tag.put("Enchantments", toLegacyEnchantmentList(active));
            }
        }
        if (criteria.has("StoredEnchantments") && !tag.contains("StoredEnchantments")) {
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) {
                tag.put("StoredEnchantments", toLegacyEnchantmentList(stored));
            }
        }

        // Generic data-component matching: only encode the components the
        // criteria actually references, to avoid serializing the entire
        // component map on every match.
        if (criteria.has("components") && criteria.get("components").isJsonObject()
                && !tag.contains("components")) {
            CompoundTag components = encodeReferencedComponents(stack, criteria.getAsJsonObject("components"));
            if (!components.isEmpty()) tag.put("components", components);
        }
        return tag;
    }

    private static CompoundTag encodeReferencedComponents(ItemStack stack, JsonObject componentCriteria) {
        CompoundTag out = new CompoundTag();
        for (String componentId : componentCriteria.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(componentId);
            if (rl == null) continue;
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
            if (type == null) continue; // unknown component (mod not loaded or typo)
            Tag encoded = encodeComponent(stack, type);
            if (encoded != null) out.put(componentId, encoded);
        }
        return out;
    }

    private static <T> Tag encodeComponent(ItemStack stack, DataComponentType<T> type) {
        T value = stack.get(type);
        if (value == null) return null;
        if (type.codec() == null) return null; // transient component, no codec
        TypedDataComponent<T> typed = new TypedDataComponent<>(type, value);
        return typed.encodeValue(matchOps())
                .resultOrPartial(err -> DebugLogger.runtimeThrottled(
                        "NBT Matching",
                        "encode_" + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type),
                        "Could not encode component "
                                + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)
                                + " for matching: " + err))
                .orElse(null);
    }

    /**
     * Data components backed by a registry reference (e.g. a mod's
     * {@code RegistryFixedCodec<Holder<T>>}, such as Iron's Jewelry's
     * {@code stored_pattern}) can only encode through a {@link RegistryOps}
     * that knows about that registry — plain {@link NbtOps#INSTANCE} makes
     * their codec fail silently. Wrap with whatever registry access is
     * available; components that don't need it are unaffected.
     */
    private static DynamicOps<Tag> matchOps() {
        HolderLookup.Provider registries = currentRegistries();
        return registries != null ? RegistryOps.create(NbtOps.INSTANCE, registries) : NbtOps.INSTANCE;
    }

    /**
     * Resolves whichever registry set is authoritative for the world that's
     * actually running. A server (dedicated or the integrated one backing a
     * singleplayer world) is checked first and is safe to read from any
     * thread; only a client with no local server (i.e. connected to a remote
     * server) falls back to the client's synced level registries.
     */
    private static HolderLookup.Provider currentRegistries() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        if (FMLEnvironment.dist == Dist.CLIENT) return ClientRegistryAccessHelper.get();
        return null;
    }

    private static ListTag toLegacyEnchantmentList(ItemEnchantments enchantments) {
        ListTag list = new ListTag();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            ResourceLocation id = entry.getKey().unwrapKey().map(ResourceKey::location).orElse(null);
            if (id == null) continue;
            CompoundTag e = new CompoundTag();
            e.putString("id", id.toString());
            e.putInt("lvl", entry.getIntValue());
            list.add(e);
        }
        return list;
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
