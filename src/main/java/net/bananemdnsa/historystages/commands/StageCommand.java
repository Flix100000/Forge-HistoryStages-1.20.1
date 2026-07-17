package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageUnlockHelper;
import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.StageEvent;
import net.minecraft.commands.arguments.EntityArgument;
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

                // --- TEMPORARY (unlock-count / timer management) ---
                .then(Commands.literal("temporary")
                        .then(Commands.literal("global")
                                .then(Commands.literal("info")
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(temporaryStageNames(false), b))
                                                .executes(ctx -> handleTempGlobalInfo(ctx.getSource(), StringArgumentType.getString(ctx, "stage")))))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(temporaryStageNames(false), b))
                                                .executes(ctx -> handleTempGlobalSetCount(ctx.getSource(), StringArgumentType.getString(ctx, "stage"), 0, true))))
                                .then(Commands.literal("setcount")
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(temporaryStageNames(false), b))
                                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                                        .executes(ctx -> handleTempGlobalSetCount(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "stage"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"), false))))))
                        .then(Commands.literal("individual")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.literal("info")
                                                .then(Commands.argument("stage", StringArgumentType.word())
                                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(temporaryStageNames(true), b))
                                                        .executes(ctx -> {
                                                            String stage = StringArgumentType.getString(ctx, "stage");
                                                            int r = 0;
                                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                                r += handleTempIndividualInfo(ctx.getSource(), p, stage);
                                                            return r;
                                                        })))
                                        .then(Commands.literal("reset")
                                                .then(Commands.argument("stage", StringArgumentType.word())
                                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(temporaryStageNames(true), b))
                                                        .executes(ctx -> {
                                                            String stage = StringArgumentType.getString(ctx, "stage");
                                                            int r = 0;
                                                            for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                                r += handleTempIndividualSetCount(ctx.getSource(), p, stage, 0, true);
                                                            return r;
                                                        })))
                                        .then(Commands.literal("setcount")
                                                .then(Commands.argument("stage", StringArgumentType.word())
                                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(temporaryStageNames(true), b))
                                                        .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                                                .executes(ctx -> {
                                                                    String stage = StringArgumentType.getString(ctx, "stage");
                                                                    int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
                                                                    int r = 0;
                                                                    for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players"))
                                                                        r += handleTempIndividualSetCount(ctx.getSource(), p, stage, count, false);
                                                                    return r;
                                                                })))))))

                // --- RELOAD ---
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            StageManager.reloadStages();
                            DebugLogger.runtime("Reload", ctx.getSource().getTextName(), "Reloaded stage configurations (" + StageManager.getStages().size() + " stages)");
                            // Push new definitions to all clients so creative tab / lock decorators reflect mode changes
                            PacketHandler.sendDefinitionsToAll(
                                    new net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket(StageManager.getStages()));
                            return syncAndReload(ctx.getSource(), StageData.get(ctx.getSource().getLevel()), "Configuration reloaded!", false);
                        }))

                // NOTE: --- DEBUG --- subcommands are registered client-side in ClientDebugCommand
        );
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
        if (!entry.getEntities().getInteractionlock().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§c▶ Entities (Interactionlock):"), false);
            entry.getEntities().getInteractionlock().forEach(e -> {
                String suffix = e.hasLockActions() ? " §8[" + String.join(", ", e.getLockActions()) + "]" : "";
                source.sendSuccess(() -> Component.literal("  §8• §7" + e.getId() + suffix), false);
            });
        }
        if (!entry.getEntities().getSpawnlock().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§c▶ Entities (Spawnlock):"), false);
            entry.getEntities().getSpawnlock().forEach(e -> {
                String suffix = e.hasLockSources() ? " §8[" + String.join(", ", e.getLockSources()) + "]" : "";
                source.sendSuccess(() -> Component.literal("  §8• §7" + e.getId() + suffix), false);
            });
        }
        return 1;
    }

    // =============================================
    // TEMPORARY-mode management
    // =============================================

    /** Ids of temporary-mode stages (individual or global), for command suggestions. */
    private static List<String> temporaryStageNames(boolean individual) {
        var map = individual ? StageManager.getIndividualStages() : StageManager.getStages();
        List<String> out = new ArrayList<>();
        for (var e : map.entrySet()) {
            if (e.getValue().getMode() == net.bananemdnsa.historystages.data.StageMode.TEMPORARY) out.add(e.getKey());
        }
        return out;
    }

    private static String formatTicks(long ticks) {
        long totalSec = ticks / 20;
        long d = totalSec / 86400; long h = (totalSec % 86400) / 3600;
        long m = (totalSec % 3600) / 60; long sec = totalSec % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0 || d > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0 || d > 0) sb.append(m).append("m ");
        sb.append(sec).append("s");
        return sb.toString();
    }

    private static String maxLabel(net.bananemdnsa.historystages.data.temporary.TemporaryConfig cfg) {
        if (cfg == null) return "1";
        return cfg.isUnlimited() ? "unlimited" : String.valueOf(cfg.getMaxTriggers());
    }

    private static int handleTempGlobalInfo(CommandSourceStack source, String stage) {
        var entry = StageManager.getStages().get(stage);
        if (entry == null || entry.getMode() != net.bananemdnsa.historystages.data.StageMode.TEMPORARY) {
            source.sendFailure(Component.literal("Global stage '" + stage + "' not found or not mode=temporary!"));
            return 0;
        }
        var cfg = entry.getTemporary();
        var temp = net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(source.getLevel());
        int count = temp.getGlobalCount(stage);
        long active = temp.globalActiveTicks(stage);
        long cd = temp.globalCooldownTicks(stage);

        source.sendSuccess(() -> Component.literal("§6--- Temporary Info: §e" + stage + " §6(global) ---"), false);
        source.sendSuccess(() -> Component.literal("§9▶ Unlocks: §f" + count + " §7/ §f" + maxLabel(cfg)), false);
        if (active > 0) source.sendSuccess(() -> Component.literal("§a▶ Currently unlocked, re-locks in §f" + formatTicks(active)), false);
        else if (cd > 0) source.sendSuccess(() -> Component.literal("§e▶ On cooldown, re-triggerable in §f" + formatTicks(cd)), false);
        else {
            boolean spent = cfg != null && !cfg.isUnlimited() && count >= cfg.getMaxTriggers();
            source.sendSuccess(() -> Component.literal(spent ? "§c▶ Spent (no unlocks left)" : "§7▶ Idle, ready to trigger"), false);
        }
        return 1;
    }

    private static int handleTempGlobalSetCount(CommandSourceStack source, String stage, int count, boolean isReset) {
        var entry = StageManager.getStages().get(stage);
        if (entry == null || entry.getMode() != net.bananemdnsa.historystages.data.StageMode.TEMPORARY) {
            source.sendFailure(Component.literal("Global stage '" + stage + "' not found or not mode=temporary!"));
            return 0;
        }
        var level = source.getLevel();
        var temp = net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(level);
        if (isReset) {
            // Re-lock first if it's currently unlocked, otherwise clearing the timer
            // would leave it unlocked forever.
            if (temp.globalActiveTicks(stage) > 0) StageUnlockHelper.relockGlobal(stage, level);
            temp.clearGlobal(stage);
            DebugLogger.runtime("Temporary", source.getTextName(), "Reset temporary state for global stage '" + stage + "'");
            source.sendSuccess(() -> Component.literal("§7[HistoryStages] Reset temporary state for '" + stage + "'."), true);
        } else {
            temp.setGlobalCount(stage, count);
            DebugLogger.runtime("Temporary", source.getTextName(), "Set unlock count of global stage '" + stage + "' to " + count);
            source.sendSuccess(() -> Component.literal("§7[HistoryStages] Set unlock count of '" + stage + "' to " + count + "."), true);
        }
        return 1;
    }

    private static int handleTempIndividualInfo(CommandSourceStack source, ServerPlayer player, String stage) {
        var entry = StageManager.getIndividualStages().get(stage);
        if (entry == null || entry.getMode() != net.bananemdnsa.historystages.data.StageMode.TEMPORARY) {
            source.sendFailure(Component.literal("Individual stage '" + stage + "' not found or not mode=temporary!"));
            return 0;
        }
        var cfg = entry.getTemporary();
        var temp = net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(player.serverLevel());
        var uuid = player.getUUID();
        int count = temp.getIndividualCount(uuid, stage);
        long active = temp.individualActiveTicks(uuid, stage);
        long cd = temp.individualCooldownTicks(uuid, stage);

        source.sendSuccess(() -> Component.literal("§6--- Temporary Info: §e" + stage + " §6(" + player.getName().getString() + ") ---"), false);
        source.sendSuccess(() -> Component.literal("§9▶ Unlocks: §f" + count + " §7/ §f" + maxLabel(cfg)), false);
        if (active > 0) source.sendSuccess(() -> Component.literal("§a▶ Currently unlocked, re-locks in §f" + formatTicks(active)), false);
        else if (cd > 0) source.sendSuccess(() -> Component.literal("§e▶ On cooldown, re-triggerable in §f" + formatTicks(cd)), false);
        else {
            boolean spent = cfg != null && !cfg.isUnlimited() && count >= cfg.getMaxTriggers();
            source.sendSuccess(() -> Component.literal(spent ? "§c▶ Spent (no unlocks left)" : "§7▶ Idle, ready to trigger"), false);
        }
        return 1;
    }

    private static int handleTempIndividualSetCount(CommandSourceStack source, ServerPlayer player,
                                                    String stage, int count, boolean isReset) {
        var entry = StageManager.getIndividualStages().get(stage);
        if (entry == null || entry.getMode() != net.bananemdnsa.historystages.data.StageMode.TEMPORARY) {
            source.sendFailure(Component.literal("Individual stage '" + stage + "' not found or not mode=temporary!"));
            return 0;
        }
        var level = player.serverLevel();
        var temp = net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(level);
        var uuid = player.getUUID();
        if (isReset) {
            if (temp.individualActiveTicks(uuid, stage) > 0) StageUnlockHelper.relockIndividual(stage, player);
            temp.clearIndividual(uuid, stage);
            DebugLogger.runtime("Temporary", source.getTextName(), "Reset temporary state for individual stage '" + stage + "' (" + player.getName().getString() + ")");
            source.sendSuccess(() -> Component.literal("§7[HistoryStages] Reset temporary state for '" + stage + "' (" + player.getName().getString() + ")."), true);
        } else {
            temp.setIndividualCount(uuid, stage, count);
            DebugLogger.runtime("Temporary", source.getTextName(), "Set unlock count of individual stage '" + stage + "' to " + count + " (" + player.getName().getString() + ")");
            source.sendSuccess(() -> Component.literal("§7[HistoryStages] Set unlock count of '" + stage + "' to " + count + " (" + player.getName().getString() + ")."), true);
        }
        return 1;
    }

    private static int handleUnlock(CommandSourceStack source, String s) {
        String executor = source.getTextName();
        StageData d = StageData.get(source.getLevel());
        // NOTE: intentionally inline (not routed through StageUnlockHelper) — the "*" path
        // emits a single combined broadcast/toast/event instead of one per stage. Future
        // fixes to StageUnlockHelper.unlockGlobal / unlockIndividual must consider whether
        // the change should be mirrored here for consistency.
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
            boolean changed = StageUnlockHelper.unlockGlobal(s, source.getLevel());
            if (!changed) return 0;
            var entry = StageManager.getStages().get(s);
            String displayName = entry != null ? entry.getDisplayName() : s;
            DebugLogger.runtime("Stage Unlock", executor, "Unlocked stage '" + s + "' (" + displayName + ")");
            source.sendSuccess(() -> Component.literal("§7[HistoryStages] Unlocked: " + s), true);
            source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds());
            return 1;
        }
    }

    private static int handleLock(CommandSourceStack source, String s) {
        String executor = source.getTextName();
        StageData d = StageData.get(source.getLevel());
        // NOTE: intentionally inline (not routed through StageUnlockHelper) — the "*" path
        // emits a single combined broadcast/toast/event instead of one per stage. Future
        // fixes to StageUnlockHelper.unlockGlobal / unlockIndividual must consider whether
        // the change should be mirrored here for consistency.
        if (s.equals("*")) {
            if (d.getUnlockedStages().isEmpty()) {
                source.sendFailure(Component.literal("No active stages found to lock!"));
                return 0;
            }

            int count = d.getUnlockedStages().size();
            List<String> toRemove = new ArrayList<>(d.getUnlockedStages());
            for (String stageId : toRemove) {
                d.removeStage(stageId);
                AutoTriggerManager.clearProgress(stageId, source.getLevel());
                net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(source.getLevel()).clearGlobal(stageId);
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
            AutoTriggerManager.clearProgress(s, source.getLevel());
            net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(source.getLevel()).clearGlobal(s);
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
            PacketHandler.sendToastToAll(new net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket(name, iconId));
        }
    }

    private static int syncAndReload(CommandSourceStack source, StageData data, String msg) {
        return syncAndReload(source, data, msg, true);
    }

    private static int syncAndReload(CommandSourceStack source, StageData data, String msg, boolean broadcast) {
        data.setDirty();
        StageData.refreshCache(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        source.sendSuccess(() -> Component.literal("§7[HistoryStages] " + msg), broadcast);
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
        if (!entry.getEntities().getInteractionlock().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§c▶ Entities (Interactionlock):"), false);
            entry.getEntities().getInteractionlock().forEach(e -> {
                String suffix = e.hasLockActions() ? " §8[" + String.join(", ", e.getLockActions()) + "]" : "";
                source.sendSuccess(() -> Component.literal("  §8• §7" + e.getId() + suffix), false);
            });
        }
        return 1;
    }

    // NOTE: intentionally inline (not routed through StageUnlockHelper) — the "*" path
    // emits a single combined broadcast/toast/event instead of one per stage. Future
    // fixes to StageUnlockHelper.unlockGlobal / unlockIndividual must consider whether
    // the change should be mirrored here for consistency.
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

    // NOTE: intentionally inline (not routed through StageUnlockHelper) — the "*" path
    // emits a single combined broadcast/toast/event instead of one per stage. Future
    // fixes to StageUnlockHelper.unlockGlobal / unlockIndividual must consider whether
    // the change should be mirrored here for consistency.
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
            AutoTriggerManager.clearProgress(stageId, true, target, target.serverLevel());
            net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(target.serverLevel())
                    .clearIndividual(target.getUUID(), stageId);
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

        boolean changed = StageUnlockHelper.unlockIndividual(stageId, target);
        if (!changed) return 0;

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
        AutoTriggerManager.clearProgress(stageId, true, target, target.serverLevel());
        net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(target.serverLevel())
                .clearIndividual(target.getUUID(), stageId);

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
