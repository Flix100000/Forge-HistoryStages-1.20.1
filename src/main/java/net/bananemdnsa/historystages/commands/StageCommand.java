package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.events.StageEvent;
import net.bananemdnsa.historystages.events.StructureLockHandler;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

public class StageCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("history")
                .requires(source -> source.hasPermission(2))

                // --- GLOBAL ---
                .then(Commands.literal("global")
                        .then(Commands.literal("unlock")
                                .then(Commands.literal("*")
                                        .executes(ctx -> handleUnlock(ctx.getSource(), "*")))
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            StageData d = StageData.get(ctx.getSource().getLevel());
                                            return SharedSuggestionProvider.suggest(StageManager.getStages().keySet().stream()
                                                    .filter(s -> !d.getUnlockedStages().contains(s)), b);
                                        })
                                        .executes(ctx -> handleUnlock(ctx.getSource(), StringArgumentType.getString(ctx, "stage")))))
                        .then(Commands.literal("lock")
                                .then(Commands.literal("*")
                                        .executes(ctx -> handleLock(ctx.getSource(), "*")))
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            StageData d = StageData.get(ctx.getSource().getLevel());
                                            return SharedSuggestionProvider.suggest(d.getUnlockedStages().stream(), b);
                                        })
                                        .executes(ctx -> handleLock(ctx.getSource(), StringArgumentType.getString(ctx, "stage")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(StageManager.getStages().keySet(), b))
                                        .executes(ctx -> handleGlobalInfo(ctx.getSource(), StringArgumentType.getString(ctx, "stage")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    StageData d = StageData.get(ctx.getSource().getLevel());
                                    ctx.getSource().sendSuccess(() -> Component.literal("§6--- Global Stages ---"), false);
                                    StageManager.getStages().keySet().forEach(s -> {
                                        String color = d.getUnlockedStages().contains(s) ? "§a" : "§c";
                                        ctx.getSource().sendSuccess(() -> Component.literal(color + "- " + s), false);
                                    });
                                    return 1;
                                })))

                // --- INDIVIDUAL ---
                .then(Commands.literal("individual")
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.literal("*")
                                                .executes(ctx -> {
                                                    int result = 0;
                                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                        result += handleIndividualUnlockAll(ctx.getSource(), p);
                                                    return result;
                                                }))
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(StageManager.getIndividualStages().keySet(), b))
                                                .executes(ctx -> {
                                                    String stage = StringArgumentType.getString(ctx, "stage");
                                                    int result = 0;
                                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                        result += handleIndividualUnlock(ctx.getSource(), p, stage);
                                                    return result;
                                                }))))
                        .then(Commands.literal("lock")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.literal("*")
                                                .executes(ctx -> {
                                                    int result = 0;
                                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                        result += handleIndividualLockAll(ctx.getSource(), p);
                                                    return result;
                                                }))
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(StageManager.getIndividualStages().keySet(), b))
                                                .executes(ctx -> {
                                                    String stage = StringArgumentType.getString(ctx, "stage");
                                                    int result = 0;
                                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                        result += handleIndividualLock(ctx.getSource(), p, stage);
                                                    return result;
                                                }))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(StageManager.getIndividualStages().keySet(), b))
                                        .executes(ctx -> handleIndividualInfo(ctx.getSource(), StringArgumentType.getString(ctx, "stage")))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> {
                                            int result = 0;
                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                result += handleIndividualList(ctx.getSource(), p);
                                            return result;
                                        }))))

                // --- RELOAD ---
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            StageManager.reloadStages();
                            DebugLogger.runtime("Reload", ctx.getSource().getTextName(), "Reloaded stage configurations (" + StageManager.getStages().size() + " stages)");
                            return syncAndReload(ctx.getSource(), StageData.get(ctx.getSource().getLevel()), "Configuration reloaded!");
                        }))

                // --- DEBUG ---
                .then(Commands.literal("debug")
                        .then(Commands.literal("structure")
                                .executes(ctx -> handleDebugStructure(ctx.getSource())))
                        .then(Commands.literal("nbt")
                                .then(Commands.literal("preset")
                                        .executes(ctx -> DebugNbtCommand.handlePreset(ctx.getSource())))
                                .then(Commands.literal("custom")
                                        .executes(ctx -> DebugNbtCommand.handleCustom(ctx.getSource())))))
        );
    }

    private static int handleDebugStructure(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        ServerLevel level = player.serverLevel();
        var holders = StructureLockHandler.collectStructureHoldersAt(level, pos);

        source.sendSuccess(() -> Component.literal(
                "§6--- Structures at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " §6---"), false);

        if (holders.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  §7(not inside any structure)"), false);
            return 1;
        }

        for (var h : holders) {
            String id = h.unwrapKey().map(k -> k.location().toString()).orElse("<unknown>");
            source.sendSuccess(() -> Component.literal("  §8• §f" + id), false);
            h.tags().forEach(tag -> source.sendSuccess(
                    () -> Component.literal("      §8↳ §b#" + tag.location()), false));
        }
        return 1;
    }

    private static int handleGlobalInfo(CommandSourceStack source, String stageName) {
        var entry = StageManager.getStages().get(stageName);
        if (entry == null) {
            source.sendFailure(Component.literal("Stage '" + stageName + "' not found!"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6--- Stage Info: §e" + stageName + " §6---"), false);

        int researchTime = entry.getResearchTime();
        if (researchTime > 0) {
            source.sendSuccess(() -> Component.literal("§9▶ Research Time: §f" + researchTime + "s §7(custom)"), false);
        } else {
            int defaultTime = Config.COMMON.researchTimeInSeconds.get();
            source.sendSuccess(() -> Component.literal("§9▶ Research Time: §f" + defaultTime + "s §7(global default)"), false);
        }

        if (!entry.getAllItemIds().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§b▶ Items:"), false);
            entry.getAllItemIds().forEach(i -> source.sendSuccess(() -> Component.literal("  §8• §7" + i), false));
        }
        if (!entry.getMods().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§a▶ Mods:"), false);
            entry.getMods().forEach(m -> source.sendSuccess(() -> Component.literal("  §8• §7" + m), false));
        }
        if (!entry.getRecipes().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§e▶ Recipes:"), false);
            entry.getRecipes().forEach(r -> source.sendSuccess(() -> Component.literal("  §8• §7" + r), false));
        }
        if (!entry.getDimensions().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§d▶ Dimensions:"), false);
            entry.getDimensions().forEach(d -> source.sendSuccess(() -> Component.literal("  §8• §7" + d), false));
        }
        if (!entry.getStructures().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§5▶ Structures:"), false);
            entry.getStructures().forEach(s -> source.sendSuccess(() -> Component.literal("  §8• §7" + s), false));
        }
        if (!entry.getEntities().getAttacklock().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§c▶ Entities (Attacklock):"), false);
            entry.getEntities().getAttacklock().forEach(e -> source.sendSuccess(() -> Component.literal("  §8• §7" + e), false));
        }
        if (!entry.getEntities().getSpawnlock().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§c▶ Entities (Spawnlock):"), false);
            entry.getEntities().getSpawnlock().forEach(e -> source.sendSuccess(() -> Component.literal("  §8• §7" + e), false));
        }
        return 1;
    }

    private static int handleUnlock(CommandSourceStack source, String s) {
        String executor = source.getTextName();
        StageData d = StageData.get(source.getLevel());
        if (s.equals("*")) {
            boolean changed = false;
            for (String id : StageManager.getStages().keySet()) {
                if (!d.getUnlockedStages().contains(id)) {
                    d.addStage(id);
                    var entry = StageManager.getStages().get(id);
                    String displayName = entry != null ? entry.getDisplayName() : id;
                    NeoForge.EVENT_BUS.post(new StageEvent.Unlocked(id, displayName));
                    changed = true;
                }
            }
            if (!changed) {
                source.sendFailure(Component.literal("All stages are already unlocked!"));
                return 0;
            }
            DebugLogger.runtime("Stage Unlock", executor, "Unlocked ALL stages (" + StageManager.getStages().size() + " total)");
            broadcastEffect(source, "*", true);
            return syncAndReload(source, d, "All stages unlocked.");
        } else {
            if (!StageManager.getStages().containsKey(s)) return 0;
            d.addStage(s);
            var entry = StageManager.getStages().get(s);
            String displayName = entry != null ? entry.getDisplayName() : s;
            NeoForge.EVENT_BUS.post(new StageEvent.Unlocked(s, displayName));
            DebugLogger.runtime("Stage Unlock", executor, "Unlocked stage '" + s + "' (" + displayName + ")");
            broadcastEffect(source, s, true);
            return syncAndReload(source, d, "Unlocked: " + s);
        }
    }

    private static int handleLock(CommandSourceStack source, String s) {
        String executor = source.getTextName();
        StageData d = StageData.get(source.getLevel());
        if (s.equals("*")) {
            if (d.getUnlockedStages().isEmpty()) {
                source.sendFailure(Component.literal("No active stages found to lock!"));
                return 0;
            }

            int count = d.getUnlockedStages().size();
            List<String> toRemove = new ArrayList<>(d.getUnlockedStages());
            for (String stageId : toRemove) {
                d.removeStage(stageId);
                var entry = StageManager.getStages().get(stageId);
                String displayName = entry != null ? entry.getDisplayName() : stageId;
                NeoForge.EVENT_BUS.post(new StageEvent.Locked(stageId, displayName));
            }

            d.getUnlockedStages().clear();
            d.setDirty();
            StageData.refreshCache(d.getUnlockedStages());

            DebugLogger.runtime("Stage Lock", executor, "Locked ALL stages (" + count + " total)");
            broadcastEffect(source, "*", false);
            return syncAndReload(source, d, "All stages locked.");
        } else {
            if (!d.getUnlockedStages().contains(s)) return 0;
            d.removeStage(s);
            var lockEntry = StageManager.getStages().get(s);
            String lockDisplayName = lockEntry != null ? lockEntry.getDisplayName() : s;
            NeoForge.EVENT_BUS.post(new StageEvent.Locked(s, lockDisplayName));
            DebugLogger.runtime("Stage Lock", executor, "Locked stage '" + s + "' (" + lockDisplayName + ")");
            broadcastEffect(source, s, false);
            return syncAndReload(source, d, "Locked: " + s);
        }
    }

    private static void broadcastEffect(CommandSourceStack source, String stageID, boolean isUnlock) {
        if (!Config.COMMON.broadcastChat.get() && !Config.COMMON.useActionbar.get() && !Config.COMMON.useSounds.get() && !Config.COMMON.useToasts.get()) return;

        var stageEntry = StageManager.getStages().get(stageID);
        String name = stageID.equals("*") ? "All Progress" : (stageEntry != null ? stageEntry.getDisplayName() : stageID);
        String iconId = (!stageID.equals("*") && stageEntry != null && !stageEntry.getIcon().isEmpty())
                ? stageEntry.getIcon() : Config.COMMON.defaultStageIcon.get();

        // --- CHAT NACHRICHT LOGIK ---
        Component chatMsg;
        if (isUnlock && !stageID.equals("*")) {
            // Nutze die editierbare Nachricht aus der Config für einzelne Unlocks
            String rawMsg = Config.COMMON.unlockMessageFormat.get();
            String formattedMsg = rawMsg.replace("{stage}", name).replace("&", "§");
            chatMsg = Component.literal("[HistoryStages] ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(formattedMsg));
        } else if (isUnlock) {
            // Standard für "Alle freischalten"
            chatMsg = Component.literal("[HistoryStages] ").withStyle(ChatFormatting.GOLD).append(Component.literal("The world has entered the " + name + "!").withStyle(ChatFormatting.AQUA));
        } else {
            // Nachricht beim Sperren
            chatMsg = Component.literal("[HistoryStages] ").withStyle(ChatFormatting.RED).append(Component.literal("The knowledge of " + name + " has been forgotten...").withStyle(ChatFormatting.WHITE));
        }

        // --- ACTIONBAR LOGIK (Bleibt Standard wie gewünscht) ---
        Component actionMsg = isUnlock
                ? Component.literal("§6New Era: " + name + " §aUnlocked!")
                : Component.literal("§cStage Locked: " + name);

        source.getServer().getPlayerList().getPlayers().forEach(player -> {
            if (Config.COMMON.broadcastChat.get()) {
                player.sendSystemMessage(chatMsg);
            }

            if (Config.COMMON.useActionbar.get()) {
                player.displayClientMessage(actionMsg, true);
            }

            if (Config.COMMON.useSounds.get()) {
                player.playNotifySound(isUnlock ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        });

        // Toast notification
        if (isUnlock && Config.COMMON.useToasts.get()) {
            PacketHandler.sendToastToAll(new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(name, iconId));
        }
    }

    private static int syncAndReload(CommandSourceStack source, StageData data, String msg) {
        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        source.sendSuccess(() -> Component.literal("§7[HistoryStages] " + msg), true);
        source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds());

        return 1;
    }

    // =============================================
    // INDIVIDUAL STAGE COMMANDS
    // =============================================

    private static int handleIndividualInfo(CommandSourceStack source, String stageName) {
        var entry = StageManager.getIndividualStages().get(stageName);
        if (entry == null) {
            source.sendFailure(Component.literal("Individual stage '" + stageName + "' not found!"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§7--- Individual Stage Info: §f" + stageName + " §7---"), false);

        int researchTime = entry.getResearchTime();
        if (researchTime > 0) {
            source.sendSuccess(() -> Component.literal("§9▶ Research Time: §f" + researchTime + "s §7(custom)"), false);
        } else {
            int defaultTime = Config.COMMON.researchTimeInSeconds.get();
            source.sendSuccess(() -> Component.literal("§9▶ Research Time: §f" + defaultTime + "s §7(global default)"), false);
        }

        if (!entry.getAllItemIds().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§b▶ Items:"), false);
            entry.getAllItemIds().forEach(i -> source.sendSuccess(() -> Component.literal("  §8• §7" + i), false));
        }
        if (!entry.getMods().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§a▶ Mods:"), false);
            entry.getMods().forEach(m -> source.sendSuccess(() -> Component.literal("  §8• §7" + m), false));
        }
        if (!entry.getDimensions().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§d▶ Dimensions:"), false);
            entry.getDimensions().forEach(d -> source.sendSuccess(() -> Component.literal("  §8• §7" + d), false));
        }
        if (!entry.getStructures().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§5▶ Structures:"), false);
            entry.getStructures().forEach(s -> source.sendSuccess(() -> Component.literal("  §8• §7" + s), false));
        }
        if (!entry.getEntities().getAttacklock().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§c▶ Entities (Attacklock):"), false);
            entry.getEntities().getAttacklock().forEach(e -> source.sendSuccess(() -> Component.literal("  §8• §7" + e), false));
        }
        return 1;
    }

    private static int handleIndividualUnlockAll(CommandSourceStack source, ServerPlayer target) {
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        java.util.Set<String> alreadyUnlocked = data.getUnlockedStages(target.getUUID());
        boolean changed = false;

        for (String stageId : StageManager.getIndividualStages().keySet()) {
            if (!alreadyUnlocked.contains(stageId)) {
                data.addStage(target.getUUID(), stageId);
                var entry = StageManager.getIndividualStages().get(stageId);
                String displayName = entry != null ? entry.getDisplayName() : stageId;
                NeoForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stageId, displayName, target.getUUID()));
                changed = true;
            }
        }

        if (!changed) {
            source.sendFailure(Component.literal("Player " + target.getName().getString() + " already has all individual stages!"));
            return 0;
        }

        data.setDirty();
        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(target.getUUID())),
                target
        );

        if (Config.COMMON.useSounds.get()) {
            target.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
        }

        DebugLogger.runtime("Individual Unlock", source.getTextName(),
                "Unlocked ALL individual stages for " + target.getName().getString());
        source.sendSuccess(() -> Component.literal("§7[HistoryStages] Unlocked all individual stages for " + target.getName().getString()), true);
        return 1;
    }

    private static int handleIndividualLockAll(CommandSourceStack source, ServerPlayer target) {
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        java.util.Set<String> playerStages = data.getUnlockedStages(target.getUUID());

        if (playerStages.isEmpty()) {
            source.sendFailure(Component.literal("Player " + target.getName().getString() + " has no individual stages to lock!"));
            return 0;
        }

        int count = playerStages.size();
        List<String> toRemove = new ArrayList<>(playerStages);
        for (String stageId : toRemove) {
            data.removeStage(target.getUUID(), stageId);
            var entry = StageManager.getIndividualStages().get(stageId);
            String displayName = entry != null ? entry.getDisplayName() : stageId;
            NeoForge.EVENT_BUS.post(new StageEvent.IndividualLocked(stageId, displayName, target.getUUID()));
            StageLockHelper.dropLockedItemsForPlayer(target, stageId);
        }

        data.setDirty();
        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(target.getUUID())),
                target
        );

        DebugLogger.runtime("Individual Lock", source.getTextName(),
                "Locked ALL individual stages (" + count + ") for " + target.getName().getString());
        source.sendSuccess(() -> Component.literal("§7[HistoryStages] Locked all individual stages for " + target.getName().getString()), true);
        return 1;
    }

    private static int handleIndividualUnlock(CommandSourceStack source, ServerPlayer target, String stageId) {
        if (!StageManager.getIndividualStages().containsKey(stageId)) {
            source.sendFailure(Component.literal("Individual stage '" + stageId + "' not found!"));
            return 0;
        }

        IndividualStageData data = IndividualStageData.get(source.getLevel());
        if (data.hasStage(target.getUUID(), stageId)) {
            source.sendFailure(Component.literal("Player " + target.getName().getString() + " already has stage '" + stageId + "'!"));
            return 0;
        }

        data.addStage(target.getUUID(), stageId);
        data.setDirty();

        var entry = StageManager.getIndividualStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        NeoForge.EVENT_BUS.post(new StageEvent.IndividualUnlocked(stageId, displayName, target.getUUID()));

        // Sync to the target player
        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(target.getUUID())),
                target
        );

        // Notify the target player
        if (Config.COMMON.individualBroadcastChat.get()) {
            String configChat = Config.COMMON.individualUnlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", displayName)
                    .replace("{player}", target.getName().getString())
                    .replace("&", "§");
            target.sendSystemMessage(
                    Component.literal("[HistoryStages] ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(finalChat))
            );
        }
        if (Config.COMMON.individualUseActionbar.get()) {
            String configChat = Config.COMMON.individualUnlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", displayName)
                    .replace("{player}", target.getName().getString())
                    .replace("&", "§");
            target.displayClientMessage(Component.literal(finalChat), true);
        }
        if (Config.COMMON.individualUseSounds.get()) {
            target.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
        }
        if (Config.COMMON.individualUseToasts.get()) {
            String iconId = (!entry.getIcon().isEmpty()) ? entry.getIcon() : Config.COMMON.defaultStageIcon.get();
            PacketHandler.sendToastToPlayer(
                    new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(displayName, iconId),
                    target
            );
        }

        DebugLogger.runtime("Individual Unlock", source.getTextName(),
                "Unlocked individual stage '" + stageId + "' for " + target.getName().getString());
        source.sendSuccess(() -> Component.literal("§7[HistoryStages] Unlocked individual stage '" + stageId + "' for " + target.getName().getString()), true);
        return 1;
    }

    private static int handleIndividualLock(CommandSourceStack source, ServerPlayer target, String stageId) {
        if (!StageManager.getIndividualStages().containsKey(stageId)) {
            source.sendFailure(Component.literal("Individual stage '" + stageId + "' not found!"));
            return 0;
        }

        IndividualStageData data = IndividualStageData.get(source.getLevel());
        if (!data.hasStage(target.getUUID(), stageId)) {
            source.sendFailure(Component.literal("Player " + target.getName().getString() + " does not have stage '" + stageId + "'!"));
            return 0;
        }

        data.removeStage(target.getUUID(), stageId);
        data.setDirty();

        var entry = StageManager.getIndividualStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        NeoForge.EVENT_BUS.post(new StageEvent.IndividualLocked(stageId, displayName, target.getUUID()));

        // Drop locked items from the player's inventory
        StageLockHelper.dropLockedItemsForPlayer(target, stageId);

        // Sync to the target player
        PacketHandler.sendIndividualStagesToPlayer(
                new SyncIndividualStagesPacket(data.getUnlockedStages(target.getUUID())),
                target
        );

        // Notify the target player
        if (Config.COMMON.individualBroadcastChat.get()) {
            target.sendSystemMessage(
                    Component.literal("[HistoryStages] ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component.literal("The knowledge of " + displayName + " has been forgotten...").withStyle(ChatFormatting.WHITE))
            );
        }
        if (Config.COMMON.individualUseSounds.get()) {
            target.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.75F, 0.5F);
        }

        DebugLogger.runtime("Individual Lock", source.getTextName(),
                "Locked individual stage '" + stageId + "' for " + target.getName().getString());
        source.sendSuccess(() -> Component.literal("§7[HistoryStages] Locked individual stage '" + stageId + "' for " + target.getName().getString()), true);
        return 1;
    }

    private static int handleIndividualList(CommandSourceStack source, ServerPlayer target) {
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        java.util.Set<String> playerStages = data.getUnlockedStages(target.getUUID());

        source.sendSuccess(() -> Component.literal("§6--- Individual Stages for " + target.getName().getString() + " ---"), false);

        if (playerStages.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No individual stages unlocked."), false);
        } else {
            StageManager.getIndividualStages().keySet().forEach(s -> {
                String color = playerStages.contains(s) ? "§a" : "§c";
                source.sendSuccess(() -> Component.literal(color + "- " + s), false);
            });
        }
        return 1;
    }
}
