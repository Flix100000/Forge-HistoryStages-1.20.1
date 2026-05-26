package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.StageDefinitionsPayload;
import net.bananemdnsa.historystages.network.StructureRegistryPayload;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.network.LockFeedbackPayload;
import net.bananemdnsa.historystages.network.StageUnlockedToastPayload;
import net.bananemdnsa.historystages.network.SyncConfigPayload;
import net.bananemdnsa.historystages.network.SyncDependencyStatusPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.bananemdnsa.historystages.network.UnlockedIndividualStagesPayload;
import net.bananemdnsa.historystages.network.UnlockedStagesPayload;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
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
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() ->
                        Config.applyEditorValues(null, payload.commonValues())));
        ClientPlayNetworking.registerGlobalReceiver(SyncDependencyStatusPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() ->
                        ClientDependencyCache.put(payload.stageId(), payload.decode())));
        ClientPlayNetworking.registerGlobalReceiver(LockFeedbackPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() -> handleLockFeedback(payload)));
        ClientPlayNetworking.registerGlobalReceiver(StageUnlockedToastPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().getToasts().addToast(
                                new StageUnlockedToast(payload.stageName(), resolveToastIcon(payload.iconId())))));
    }

    private static void handleLockFeedback(LockFeedbackPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean isDimension = payload.kind() == LockFeedbackPayload.KIND_DIMENSION;
        boolean showChat = isDimension ? Config.CLIENT.dimShowChat : Config.CLIENT.mobShowChat;
        boolean showStagesInChat = isDimension ? Config.CLIENT.dimShowStagesInChat : Config.CLIENT.mobShowStagesInChat;
        boolean useActionbar = isDimension ? Config.CLIENT.dimUseActionbar : Config.CLIENT.mobUseActionbar;
        String chatKey = isDimension ? "message.historystages.dimension_locked" : "message.historystages.mob_locked";
        String actionbarKey = isDimension ? "message.historystages.dimension_unknown" : "message.historystages.mob_unknown";
        if (showChat) {
            MutableComponent chat = Component.translatable(chatKey);
            if (showStagesInChat) {
                for (String name : payload.displayNames()) {
                    chat.append(Component.translatable("message.historystages.locked_stage", name));
                }
            }
            mc.player.sendSystemMessage(chat);
        }
        if (useActionbar) {
            mc.player.displayClientMessage(Component.translatable(actionbarKey)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        }
    }

    private static ItemStack resolveToastIcon(String iconId) {
        String id = iconId != null && !iconId.isEmpty()
                ? iconId
                : Config.COMMON.defaultStageIcon;
        if (id != null && !id.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
        }
        return new ItemStack(ModItems.RESEARCH_SCROLL);
    }
}
