package net.bananemdnsa.historystages.client.editor.nbt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Answers "what is this component's value supposed to look like" by showing a real one.
 *
 * <p>A codec cannot be asked about its own shape at runtime, and writing the shapes down by hand
 * would mean guessing for every modded component and going stale on every vanilla change. Encoding
 * an actual value sidesteps both: the string this produces is exactly what {@code NbtMatcher}
 * compares a criterion against, because it comes out of the same encoder.
 */
public final class ComponentShapes {

    private ComponentShapes() {}

    /** Encodes one component off a stack, or null when the stack lacks it or it has no codec. */
    public static <T> JsonElement encode(ItemStack stack, DataComponentType<T> type) {
        if (type.codec() == null) return null;
        T value = stack.get(type);
        if (value == null) return null;
        return type.codec().encodeStart(ops(), value).result().orElse(null);
    }

    /**
     * A concrete value for this component, taken from an item that carries one — the player's
     * inventory first, then any item whose defaults include it. Null when nothing in the game has
     * one to show.
     *
     * <p>Scans rather than caches: the result depends on what the player is carrying and which
     * registries the current world loaded, and a cached shape outliving either of those is the
     * kind of thing that only turns up as "it was right before the restart".
     */
    public static String exampleFor(String componentId) {
        ResourceLocation id = ResourceLocation.tryParse(componentId);
        if (id == null) return null;
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        if (type == null || type.codec() == null) return null;

        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (ItemStack stack : player.getInventory().items) {
                String encoded = encodeToString(stack, type);
                if (encoded != null) return encoded;
            }
        }

        for (Item item : BuiltInRegistries.ITEM) {
            String encoded = encodeToString(new ItemStack(item), type);
            if (encoded != null) return encoded;
        }
        return null;
    }

    /**
     * The shapes worth trying on a codec that will not describe itself, simplest first.
     *
     * <p>The point is not that one of these is the intended value — it is that a codec answers
     * yes or no, and the first yes settles whether the property wants a string, a list, a number
     * or an object. That question has no good answer anywhere else: the vanilla format is barely
     * documented and a mod's is not documented at all.
     */
    private static final List<String> CANDIDATES = List.of(
            "{}", "\"\"", "[]", "0", "false", "[{}]", "[\"\"]", "\"minecraft:stone\"");

    /**
     * The smallest value this component's codec accepts, or null when trial does not find one.
     *
     * <p>Tries the plain shapes first. If none is accepted, the codec's complaint names a field it
     * insists on, so that field is added and the shapes are tried again inside it — repeating
     * while the complaint keeps moving to a new name. What comes back is a real value the codec
     * validated, not a guess about one.
     */
    public static String skeletonFor(String componentId) {
        Codec<?> codec = codecOf(componentId);
        if (codec == null) return null;

        for (String candidate : CANDIDATES) {
            JsonElement parsed = parseJson(candidate);
            if (parsed != null && accepts(codec, parsed)) return candidate;
        }

        JsonObject built = new JsonObject();
        for (int round = 0; round < 4; round++) {
            String missing = missingKey(errorOf(codec, built));
            if (missing == null || built.has(missing)) return null;

            boolean filled = false;
            for (String candidate : CANDIDATES) {
                JsonElement value = parseJson(candidate);
                if (value == null) continue;
                built.add(missing, value);

                if (accepts(codec, built)) return built.toString();

                String next = missingKey(errorOf(codec, built));
                if (next != null && !next.equals(missing)) {
                    // The complaint moved on, so this field is settled — keep it and carry on.
                    filled = true;
                    break;
                }
                built.remove(missing);
            }
            if (!filled) return null;
        }
        return null;
    }

    /** True when {@code {}} is a valid value, i.e. the criterion can just ask "is this set". */
    public static boolean acceptsEmptyObject(String componentId) {
        Codec<?> codec = codecOf(componentId);
        return codec != null && accepts(codec, new JsonObject());
    }

    private static Codec<?> codecOf(String componentId) {
        ResourceLocation id = ResourceLocation.tryParse(componentId);
        if (id == null) return null;
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        return type == null ? null : type.codec();
    }

    private static boolean accepts(Codec<?> codec, JsonElement value) {
        try {
            return codec.parse(ops(), value).result().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private static String errorOf(Codec<?> codec, JsonElement value) {
        try {
            return codec.parse(ops(), value).error().map(error -> error.message()).orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    private static String missingKey(String errorMessage) {
        return CodecErrorText.missingKey(errorMessage);
    }

    private static JsonElement parseJson(String raw) {
        try {
            return JsonParser.parseString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * What the codec says is missing when handed an empty object, or null when it accepts one.
     *
     * <p>The last resort for a component no item in the game carries — {@code can_break} and the
     * other adventure-mode ones exist only on items a pack author builds by hand, so there is
     * nothing to read a shape off. Failing to decode {@code {}} makes the codec name the fields it
     * insists on, which is derived from the real thing rather than written down and left to rot.
     */
    public static String requirementHint(String componentId) {
        ResourceLocation id = ResourceLocation.tryParse(componentId);
        if (id == null) return null;
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        if (type == null || type.codec() == null) return null;

        try {
            return type.codec().parse(ops(), new JsonObject()).error()
                    .map(error -> error.message())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String encodeToString(ItemStack stack, DataComponentType<?> type) {
        if (stack.isEmpty()) return null;
        JsonElement encoded = encode(stack, type);
        return encoded == null || encoded.isJsonNull() ? null : encoded.toString();
    }

    /**
     * The same ops {@code NbtMatcher.matchOps} uses. Components backed by a registry reference only
     * encode through ops that know about that registry; plain {@link JsonOps} makes their codec
     * fail silently.
     */
    private static DynamicOps<JsonElement> ops() {
        var level = Minecraft.getInstance().level;
        return level != null
                ? RegistryOps.create(JsonOps.INSTANCE, level.registryAccess())
                : JsonOps.INSTANCE;
    }
}
