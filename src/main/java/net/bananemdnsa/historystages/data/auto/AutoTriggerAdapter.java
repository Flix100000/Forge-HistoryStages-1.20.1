package net.bananemdnsa.historystages.data.auto;

import com.google.gson.*;
import net.bananemdnsa.historystages.data.auto.conditions.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AutoTriggerAdapter
        implements JsonSerializer<AutoTrigger>, JsonDeserializer<AutoTrigger> {

    @Override
    public AutoTrigger deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
            throws JsonParseException {
        if (!json.isJsonObject()) return new AutoTrigger();
        JsonObject obj = json.getAsJsonObject();

        String mode = obj.has("mode") && !obj.get("mode").isJsonNull()
                ? obj.get("mode").getAsString() : null;

        List<TriggerCondition> triggers = new ArrayList<>();
        if (obj.has("triggers") && obj.get("triggers").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("triggers")) {
                TriggerCondition t = deserializeTrigger(el, ctx);
                if (t != null) triggers.add(t);
            }
        }
        return new AutoTrigger(mode, triggers);
    }

    private TriggerCondition deserializeTrigger(JsonElement el, JsonDeserializationContext ctx) {
        if (!el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();
        if (!obj.has("type") || obj.get("type").isJsonNull()) return null;
        String type = obj.get("type").getAsString();

        return switch (type) {
            case "biome"       -> ctx.deserialize(obj, BiomeTrigger.class);
            case "structure"   -> ctx.deserialize(obj, StructureTrigger.class);
            case "dimension"   -> ctx.deserialize(obj, DimensionTrigger.class);
            case "item"        -> ctx.deserialize(obj, ItemTrigger.class);
            case "entity"      -> ctx.deserialize(obj, EntityTrigger.class);
            case "block_place" -> ctx.deserialize(obj, BlockPlaceTrigger.class);
            case "block_break" -> ctx.deserialize(obj, BlockBreakTrigger.class);
            case "advancement" -> ctx.deserialize(obj, AdvancementTrigger.class);
            case "playtime"    -> ctx.deserialize(obj, PlaytimeTrigger.class);
            default            -> null; // unknown type — silently skip
        };
    }

    @Override
    public JsonElement serialize(AutoTrigger src, Type typeOfSrc, JsonSerializationContext ctx) {
        JsonObject out = new JsonObject();
        if (src.getRawMode() != null) out.addProperty("mode", src.getRawMode());
        JsonArray arr = new JsonArray();
        for (TriggerCondition t : src.getTriggers()) {
            JsonObject inner = ctx.serialize(t).getAsJsonObject();
            // Ensure "type" is always present (records may not auto-include it).
            inner.addProperty("type", t.type());
            arr.add(inner);
        }
        out.add("triggers", arr);
        return out;
    }
}
