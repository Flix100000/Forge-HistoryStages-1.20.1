package net.bananemdnsa.historystages.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

/**
 * The entry point KubeJS finds through {@code kubejs.plugins.txt}. Nothing in HistoryStages
 * references this class, so without KubeJS installed it is never loaded.
 */
public class HistoryStagesKubePlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(HistoryStagesKubeEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type() == ScriptType.SERVER) {
            bindings.add("HistoryStages", HistoryStagesBindings.class);
        } else if (bindings.type() == ScriptType.CLIENT) {
            bindings.add("HistoryStages", HistoryStagesClientBindings.class);
        }
        // Startup scripts get nothing on purpose: they run before a world exists, so every
        // answer about stage state would be a lie rather than a "no".
    }
}
