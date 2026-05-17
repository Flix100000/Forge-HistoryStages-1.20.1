package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.StageDefinitionsPayload;
import net.bananemdnsa.historystages.network.StructureRegistryPayload;
import net.bananemdnsa.historystages.network.UnlockedIndividualStagesPayload;
import net.bananemdnsa.historystages.network.UnlockedStagesPayload;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.HashSet;

public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StageDefinitionsPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    StageManager.applySyncedDefinitions(payload.globalStagesJson(), payload.individualStagesJson());
                    OptionalRecipeViewHooks.refreshAll();
                }));
        ClientPlayNetworking.registerGlobalReceiver(UnlockedStagesPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    ClientStageCache.setUnlockedStages(payload.stages());
                    OptionalRecipeViewHooks.refreshAll();
                }));
        ClientPlayNetworking.registerGlobalReceiver(UnlockedIndividualStagesPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() -> {
                    ClientIndividualStageCache.setUnlockedStages(new HashSet<>(payload.stages()));
                    OptionalRecipeViewHooks.refreshAll();
                }));
        ClientPlayNetworking.registerGlobalReceiver(StructureRegistryPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() ->
                        ClientStructureRegistry.set(payload.structureIds(), payload.structureTagIds())));
    }
}
