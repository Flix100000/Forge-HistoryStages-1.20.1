package net.bananemdnsa.historystages.data.auto;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

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

        Class<? extends TriggerCondition> conditionClass = TriggerTypes.classFor(type);
        if (conditionClass == null) {
            // Not ours and not any loaded addon's. Keeping the object verbatim means the trigger
            // comes back when its mod does; the old code dropped it here, so editing a stage
            // without that mod installed destroyed it silently.
            return new UnknownTrigger(type, obj.deepCopy());
        }
        return ctx.deserialize(obj, conditionClass);
    }

    @Override
    public JsonElement serialize(AutoTrigger src, Type typeOfSrc, JsonSerializationContext ctx) {
        JsonObject out = new JsonObject();
        if (src.getRawMode() != null) out.addProperty("mode", src.getRawMode());
        JsonArray arr = new JsonArray();
        for (TriggerCondition t : src.getTriggers()) {
            if (t instanceof UnknownTrigger unknown) {
                // Written back exactly as it was read, fields this build never understood included.
                arr.add(unknown.raw().deepCopy());
                continue;
            }
            JsonObject inner = ctx.serialize(t).getAsJsonObject();
            // Ensure "type" is always present (records may not auto-include it).
            inner.addProperty("type", t.type());
            arr.add(inner);
        }
        out.add("triggers", arr);
        return out;
    }
}
