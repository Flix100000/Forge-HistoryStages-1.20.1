package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.events.StageEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

public class StageCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("history")
                .requires(source -> source.hasPermission(2))

                // --- UNLOCK ---
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

                // --- LOCK ---
                .then(Commands.literal("lock")
                        .then(Commands.literal("*")
                                .executes(ctx -> handleLock(ctx.getSource(), "*")))
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    StageData d = StageData.get(ctx.getSource().getLevel());
                                    return SharedSuggestionProvider.suggest(d.getUnlockedStages().stream(), b);
                                })
                                .executes(ctx -> handleLock(ctx.getSource(), StringArgumentType.getString(ctx, "stage")))))

                // --- INFO ---
                .then(Commands.literal("info")
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(StageManager.getStages().keySet(), b))
                                .executes(ctx -> {
                                    String stageName = StringArgumentType.getString(ctx, "stage");
                                    var entry = StageManager.getStages().get(stageName);
                                    if (entry == null) {
                                        ctx.getSource().sendFailure(Component.literal("Stage '" + stageName + "' not found!"));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("§6--- Stage Info: §e" + stageName + " §6---"), false);

                                    // Research Time
                                    int researchTime = entry.getResearchTime();
                                    if (researchTime > 0) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§9▶ Research Time: §f" + researchTime + "s §7(custom)"), false);
                                    } else {
                                        int defaultTime = Config.COMMON.researchTimeInSeconds.get();
                                        ctx.getSource().sendSuccess(() -> Component.literal("§9▶ Research Time: §f" + defaultTime + "s §7(global default)"), false);
                                    }

                                    if (!entry.getItems().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§b▶ Items:"), false);
                                        entry.getItems().forEach(i -> ctx.getSource().sendSuccess(() -> Component.literal("  §8• §7" + i), false));
                                    }

                                    if (!entry.getMods().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§a▶ Mods:"), false);
                                        entry.getMods().forEach(m -> ctx.getSource().sendSuccess(() -> Component.literal("  §8• §7" + m), false));
                                    }

                                    if (!entry.getRecipes().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§e▶ Recipes:"), false);
                                        entry.getRecipes().forEach(r -> ctx.getSource().sendSuccess(() -> Component.literal("  §8• §7" + r), false));
                                    }

                                    if (!entry.getDimensions().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§d▶ Dimensions:"), false);
                                        entry.getDimensions().forEach(d -> ctx.getSource().sendSuccess(() -> Component.literal("  §8• §7" + d), false));
                                    }

                                    if (!entry.getEntities().getAttacklock().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§c▶ Entities (Attacklock):"), false);
                                        entry.getEntities().getAttacklock().forEach(e -> ctx.getSource().sendSuccess(() -> Component.literal("  §8• §7" + e), false));
                                    }

                                    if (!entry.getEntities().getSpawnlock().isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§c▶ Entities (Spawnlock):"), false);
                                        entry.getEntities().getSpawnlock().forEach(e -> ctx.getSource().sendSuccess(() -> Component.literal("  §8• §7" + e), false));
                                    }

                                    return 1;
                                })))

                // --- LIST ---
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            StageData d = StageData.get(ctx.getSource().getLevel());
                            ctx.getSource().sendSuccess(() -> Component.literal("§6--- Registered Stages ---"), false);
                            StageManager.getStages().keySet().forEach(s -> {
                                String color = d.getUnlockedStages().contains(s) ? "§a" : "§c";
                                ctx.getSource().sendSuccess(() -> Component.literal(color + "- " + s), false);
                            });
                            return 1;
                        }))

                // --- RELOAD ---
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            StageManager.reloadStages();
                            return syncAndReload(ctx.getSource(), StageData.get(ctx.getSource().getLevel()), "Configuration reloaded!");
                        }))
        );
    }

    private static int handleUnlock(CommandSourceStack source, String s) {
        StageData d = StageData.get(source.getLevel());
        if (s.equals("*")) {
            boolean changed = false;
            for (String id : StageManager.getStages().keySet()) {
                if (!d.getUnlockedStages().contains(id)) {
                    d.addStage(id);
                    var entry = StageManager.getStages().get(id);
                    String displayName = entry != null ? entry.getDisplayName() : id;
                    MinecraftForge.EVENT_BUS.post(new StageEvent.Unlocked(id, displayName));
                    changed = true;
                }
            }
            if (!changed) {
                source.sendFailure(Component.literal("All stages are already unlocked!"));
                return 0;
            }
            broadcastEffect(source, "*", true);
            return syncAndReload(source, d, "All stages unlocked.");
        } else {
            if (!StageManager.getStages().containsKey(s)) return 0;
            d.addStage(s);
            var entry = StageManager.getStages().get(s);
            String displayName = entry != null ? entry.getDisplayName() : s;
            MinecraftForge.EVENT_BUS.post(new StageEvent.Unlocked(s, displayName));
            broadcastEffect(source, s, true);
            return syncAndReload(source, d, "Unlocked: " + s);
        }
    }

    private static int handleLock(CommandSourceStack source, String s) {
        StageData d = StageData.get(source.getLevel());
        if (s.equals("*")) {
            if (d.getUnlockedStages().isEmpty()) {
                source.sendFailure(Component.literal("No active stages found to lock!"));
                return 0;
            }

            List<String> toRemove = new ArrayList<>(d.getUnlockedStages());
            for (String stageId : toRemove) {
                d.removeStage(stageId);
                var entry = StageManager.getStages().get(stageId);
                String displayName = entry != null ? entry.getDisplayName() : stageId;
                MinecraftForge.EVENT_BUS.post(new StageEvent.Locked(stageId, displayName));
            }

            d.getUnlockedStages().clear();
            d.setDirty();
            StageData.SERVER_CACHE.clear();

            broadcastEffect(source, "*", false);
            return syncAndReload(source, d, "All stages locked.");
        } else {
            if (!d.getUnlockedStages().contains(s)) return 0;
            d.removeStage(s);
            var lockEntry = StageManager.getStages().get(s);
            String lockDisplayName = lockEntry != null ? lockEntry.getDisplayName() : s;
            MinecraftForge.EVENT_BUS.post(new StageEvent.Locked(s, lockDisplayName));
            broadcastEffect(source, s, false);
            return syncAndReload(source, d, "Locked: " + s);
        }
    }

    private static void broadcastEffect(CommandSourceStack source, String stageID, boolean isUnlock) {
        if (!Config.COMMON.broadcastChat.get() && !Config.COMMON.useActionbar.get() && !Config.COMMON.useSounds.get() && !Config.COMMON.useToasts.get()) return;

        String name = stageID.equals("*") ? "All Progress" : (StageManager.getStages().containsKey(stageID) ? StageManager.getStages().get(stageID).getDisplayName() : stageID);

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
            PacketHandler.sendToastToAll(new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(name));
        }
    }

    private static int syncAndReload(CommandSourceStack source, StageData data, String msg) {
        data.setDirty();
        StageData.SERVER_CACHE.clear();
        StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));

        source.sendSuccess(() -> Component.literal("§7[HistoryStages] " + msg), true);
        source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds());

        return 1;
    }
}