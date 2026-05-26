package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.Networking;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class StageCommand {
    private StageCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("history")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("global")
                        .then(Commands.literal("unlock")
                                .then(Commands.literal("*")
                                        .executes(context -> unlockGlobal(context.getSource(), "*")))
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(StageManager.getStages().keySet(), builder))
                                        .executes(context -> unlockGlobal(context.getSource(),
                                                StringArgumentType.getString(context, "stage")))))
                        .then(Commands.literal("lock")
                                .then(Commands.literal("*")
                                        .executes(context -> lockGlobal(context.getSource(), "*")))
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(StageManager.getStages().keySet(), builder))
                                        .executes(context -> lockGlobal(context.getSource(),
                                                StringArgumentType.getString(context, "stage")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(StageManager.getStages().keySet(), builder))
                                        .executes(context -> showInfo(context.getSource(),
                                                StringArgumentType.getString(context, "stage"), false))))
                        .then(Commands.literal("list")
                                .executes(context -> listGlobal(context.getSource()))))
                .then(Commands.literal("individual")
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.literal("*")
                                                .executes(context -> unlockIndividualAll(context.getSource(),
                                                        EntityArgument.getPlayers(context, "players"))))
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(StageManager.getIndividualStages().keySet(), builder))
                                                .executes(context -> unlockIndividual(context.getSource(),
                                                        EntityArgument.getPlayers(context, "players"),
                                                        StringArgumentType.getString(context, "stage"))))))
                        .then(Commands.literal("lock")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.literal("*")
                                                .executes(context -> lockIndividualAll(context.getSource(),
                                                        EntityArgument.getPlayers(context, "players"))))
                                        .then(Commands.argument("stage", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(StageManager.getIndividualStages().keySet(), builder))
                                                .executes(context -> lockIndividual(context.getSource(),
                                                        EntityArgument.getPlayers(context, "players"),
                                                        StringArgumentType.getString(context, "stage"))))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(StageManager.getIndividualStages().keySet(), builder))
                                        .executes(context -> showInfo(context.getSource(),
                                                StringArgumentType.getString(context, "stage"), true))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(context -> listIndividual(context.getSource(),
                                                EntityArgument.getPlayers(context, "players"))))))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            StageManager.reloadStages();
                            Networking.syncAll(context.getSource().getServer());
                            context.getSource().sendSuccess(() ->
                                    Component.literal("Reloaded " + StageManager.getStages().size() + " global and "
                                            + StageManager.getIndividualStages().size() + " individual stages."), true);
                            return 1;
                        })));
    }

    private static int listGlobal(CommandSourceStack source) {
        StageData data = StageData.get(source.getLevel());
        source.sendSuccess(() -> Component.literal("--- Global Stages ---"), false);
        for (MapEntry entry : getSortedEntries(StageManager.getStages())) {
            boolean unlocked = data.hasStage(entry.id());
            source.sendSuccess(() -> Component.literal((unlocked ? "[unlocked] " : "[locked] ") + entry.id()), false);
        }
        return 1;
    }

    private static int listIndividual(CommandSourceStack source, Collection<ServerPlayer> players) {
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        for (ServerPlayer player : players) {
            source.sendSuccess(() -> Component.literal("--- Individual Stages For " + player.getGameProfile().getName() + " ---"), false);
            for (MapEntry entry : getSortedEntries(StageManager.getIndividualStages())) {
                boolean unlocked = data.hasStage(player.getUUID(), entry.id());
                source.sendSuccess(() -> Component.literal((unlocked ? "[unlocked] " : "[locked] ") + entry.id()), false);
            }
        }
        return 1;
    }

    private static int unlockGlobal(CommandSourceStack source, String stageId) {
        StageData data = StageData.get(source.getLevel());
        if ("*".equals(stageId)) {
            boolean changed = false;
            for (String id : StageManager.getStages().keySet()) {
                if (!data.hasStage(id)) {
                    data.addStage(id);
                    changed = true;
                }
            }
            if (!changed) {
                source.sendFailure(Component.literal("All global stages are already unlocked."));
                return 0;
            }
            Networking.syncAll(source.getServer());
            source.sendSuccess(() -> Component.literal("Unlocked all global stages."), true);
            return 1;
        }
        if (!StageManager.getStages().containsKey(stageId)) {
            source.sendFailure(Component.literal("Unknown global stage '" + stageId + "'."));
            return 0;
        }
        if (data.hasStage(stageId)) {
            source.sendFailure(Component.literal("Global stage '" + stageId + "' is already unlocked."));
            return 0;
        }
        data.addStage(stageId);
        Networking.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Unlocked global stage '" + stageId + "'."), true);
        return 1;
    }

    private static int lockGlobal(CommandSourceStack source, String stageId) {
        StageData data = StageData.get(source.getLevel());
        if ("*".equals(stageId)) {
            if (data.getUnlockedStages().isEmpty()) {
                source.sendFailure(Component.literal("No global stages are currently unlocked."));
                return 0;
            }
            for (String id : new ArrayList<>(data.getUnlockedStages())) {
                data.removeStage(id);
            }
            Networking.syncAll(source.getServer());
            source.sendSuccess(() -> Component.literal("Locked all global stages."), true);
            return 1;
        }
        if (!StageManager.getStages().containsKey(stageId)) {
            source.sendFailure(Component.literal("Unknown global stage '" + stageId + "'."));
            return 0;
        }
        if (!data.hasStage(stageId)) {
            source.sendFailure(Component.literal("Global stage '" + stageId + "' is not unlocked."));
            return 0;
        }
        data.removeStage(stageId);
        Networking.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Locked global stage '" + stageId + "'."), true);
        return 1;
    }

    private static int unlockIndividualAll(CommandSourceStack source, Collection<ServerPlayer> players) {
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        int changed = 0;
        for (ServerPlayer player : players) {
            for (String stageId : StageManager.getIndividualStages().keySet()) {
                if (!data.hasStage(player.getUUID(), stageId)) {
                    data.addStage(player.getUUID(), stageId);
                    changed++;
                }
            }
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("The selected players already have all individual stages."));
            return 0;
        }
        Networking.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Unlocked all individual stages for " + players.size() + " player(s)."), true);
        return 1;
    }

    private static int lockIndividualAll(CommandSourceStack source, Collection<ServerPlayer> players) {
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        int changed = 0;
        for (ServerPlayer player : players) {
            for (String stageId : new ArrayList<>(data.getUnlockedStages(player.getUUID()))) {
                data.removeStage(player.getUUID(), stageId);
                if (Config.COMMON.individualDropOnRevoke) {
                    StageLockHelper.dropLockedItemsForPlayer(player, stageId);
                }
                changed++;
            }
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("The selected players have no unlocked individual stages."));
            return 0;
        }
        Networking.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Locked all individual stages for " + players.size() + " player(s)."), true);
        return 1;
    }

    private static int unlockIndividual(CommandSourceStack source, Collection<ServerPlayer> players, String stageId) {
        if (!StageManager.getIndividualStages().containsKey(stageId)) {
            source.sendFailure(Component.literal("Unknown individual stage '" + stageId + "'."));
            return 0;
        }
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        int changed = 0;
        for (ServerPlayer player : players) {
            if (!data.hasStage(player.getUUID(), stageId)) {
                data.addStage(player.getUUID(), stageId);
                changed++;
            }
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("That individual stage is already unlocked for the selected players."));
            return 0;
        }
        Networking.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Unlocked individual stage '" + stageId + "' for " + players.size() + " player(s)."), true);
        return 1;
    }

    private static int lockIndividual(CommandSourceStack source, Collection<ServerPlayer> players, String stageId) {
        if (!StageManager.getIndividualStages().containsKey(stageId)) {
            source.sendFailure(Component.literal("Unknown individual stage '" + stageId + "'."));
            return 0;
        }
        IndividualStageData data = IndividualStageData.get(source.getLevel());
        int changed = 0;
        for (ServerPlayer player : players) {
            if (data.hasStage(player.getUUID(), stageId)) {
                data.removeStage(player.getUUID(), stageId);
                if (Config.COMMON.individualDropOnRevoke) {
                    StageLockHelper.dropLockedItemsForPlayer(player, stageId);
                }
                changed++;
            }
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("That individual stage is not unlocked for the selected players."));
            return 0;
        }
        Networking.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Locked individual stage '" + stageId + "' for " + players.size() + " player(s)."), true);
        return 1;
    }

    private static int showInfo(CommandSourceStack source, String stageId, boolean individual) {
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId) : StageManager.getStages().get(stageId);
        if (entry == null) {
            source.sendFailure(Component.literal((individual ? "Individual" : "Global") + " stage '" + stageId + "' not found."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("--- " + (individual ? "Individual" : "Global") + " Stage Info: " + stageId + " ---"), false);
        source.sendSuccess(() -> Component.literal("Display Name: " + entry.getDisplayName()), false);
        source.sendSuccess(() -> Component.literal("Research Time: " + (entry.getResearchTime() > 0 ? entry.getResearchTime() + "s" : Config.COMMON.researchTimeInSeconds + "s (default)")), false);
        sendList(source, "Items", entry.getAllItemIds());
        sendList(source, "Mods", entry.getMods());
        sendList(source, "Recipes", entry.getRecipes());
        sendList(source, "Dimensions", entry.getDimensions());
        sendList(source, "Structures", entry.getStructures());
        sendList(source, "Entities (Attacklock)", entry.getEntities().getAttacklock());
        sendList(source, "Entities (Spawnlock)", entry.getEntities().getSpawnlock());
        return 1;
    }

    private static void sendList(CommandSourceStack source, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        source.sendSuccess(() -> Component.literal(label + ":"), false);
        for (String value : values) {
            source.sendSuccess(() -> Component.literal(" - " + value), false);
        }
    }

    private static List<MapEntry> getSortedEntries(Map<String, StageEntry> entries) {
        List<MapEntry> sorted = new ArrayList<>();
        for (Map.Entry<String, StageEntry> entry : entries.entrySet()) {
            sorted.add(new MapEntry(entry.getKey(), entry.getValue()));
        }
        sorted.sort(java.util.Comparator.comparing(MapEntry::id));
        return sorted;
    }

    private record MapEntry(String id, StageEntry value) {
    }
}
