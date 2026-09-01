package net.bananemdnsa.historystages.events;
import net.bananemdnsa.historystages.events.lock.StructureLockHandler;

import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DayCountTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EffectTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntitySubMode;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StatCategory;
import net.bananemdnsa.historystages.data.auto.conditions.StatTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TimeOfDayTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.WeatherTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.XpLevelTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Single Forge-bus listener that forwards relevant world events into
 * {@link AutoTriggerManager#process} so AUTO-mode stages can auto-unlock.
 *
 * <p>Registered on {@code NeoForge.EVENT_BUS} from {@code HistoryStages}'s
 * constructor. Polled triggers (biome/structure/playtime) piggyback the server
 * tick via {@link #pollPlayers(net.minecraft.server.MinecraftServer, int)}.
 */
public final class AutoTriggerEventBridge {

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!AutoTriggerManager.hasType("dimension")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation toLoc = event.getTo().location();
        String dimId = toLoc.toString();
        AutoTriggerManager.process(
                "dimension",
                t -> (t instanceof DimensionTrigger dt) && dimId.equals(dt.id()),
                player);
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!AutoTriggerManager.hasType("advancement")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getAdvancement() == null) return;
        ResourceLocation advLoc = event.getAdvancement().id();
        if (advLoc == null) return;
        String advId = advLoc.toString();
        AutoTriggerManager.process(
                "advancement",
                t -> (t instanceof AdvancementTrigger at) && advId.equals(at.id()),
                player);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!AutoTriggerManager.hasType("block_place")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState placed = event.getPlacedBlock();
        if (placed == null) return;
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(placed.getBlock());
        if (key == null) return;
        String blockId = key.toString();
        AutoTriggerManager.process(
                "block_place",
                t -> (t instanceof BlockPlaceTrigger bp) && blockId.equals(bp.id()),
                player);
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!AutoTriggerManager.hasType("item")) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemEntity().getItem();
        if (stack.isEmpty()) return;
        fireItemTrigger(player, stack);
    }

    private static void scanInventoryForItemTriggers(ServerPlayer player) {
        var inv = player.getInventory();
        // Dedupe by item id — a player with a stack of cobblestone in 9 slots
        // shouldn't run the cobblestone trigger 9 times per container open.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (key == null) continue;
            String itemId = key.toString();
            if (!seen.add(itemId)) continue;
            fireItemTriggerForId(player, itemId);
        }
    }

    private static void fireItemTrigger(ServerPlayer player, ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return;
        fireItemTriggerForId(player, key.toString());
    }

    private static void fireItemTriggerForId(ServerPlayer player, String itemId) {
        AutoTriggerManager.process(
                "item",
                t -> (t instanceof ItemTrigger it) && itemId.equals(it.id()),
                player);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!AutoTriggerManager.hasType("block_break")) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        if (state == null) return;
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return;
        String blockId = key.toString();
        AutoTriggerManager.process(
                "block_break",
                t -> (t instanceof BlockBreakTrigger bb) && blockId.equals(bb.id()),
                player);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!AutoTriggerManager.hasType("entity")) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == null) return;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (key == null) return;
        String entityId = key.toString();
        AutoTriggerManager.process(
                "entity",
                t -> {
                    if (!(t instanceof EntityTrigger et)) return false;
                    if (!entityId.equals(et.id())) return false;
                    EntitySubMode mode = et.resolvedSubMode();
                    return mode == EntitySubMode.ANY || mode == EntitySubMode.KILL;
                },
                player);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!AutoTriggerManager.hasType("entity")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTarget() == null) return;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType());
        if (key == null) return;
        String entityId = key.toString();
        AutoTriggerManager.process(
                "entity",
                t -> {
                    if (!(t instanceof EntityTrigger et)) return false;
                    if (!entityId.equals(et.id())) return false;
                    EntitySubMode mode = et.resolvedSubMode();
                    return mode == EntitySubMode.ANY || mode == EntitySubMode.INTERACT;
                },
                player);
    }

    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (!AutoTriggerManager.hasType("effect")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
        if (key == null) return;
        String effectId = key.toString();
        AutoTriggerManager.process(
                "effect",
                t -> (t instanceof EffectTrigger et) && effectId.equals(et.id()),
                player);
    }

    /** Called every server tick from {@code HistoryStages.onServerTick}. */
    public static void pollPlayers(MinecraftServer server, int tickCounter) {
        if (server == null) return;
        // One pass over the flags before the loop, so a server whose pack uses none of the polled
        // types does no per-player work at all.
        if (!anyPollDue(tickCounter)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            pollPlayer(p, tickCounter);
        }
    }

    private static boolean anyPollDue(int tickCounter) {
        if (tickCounter % 20 == 0
                && (AutoTriggerManager.hasType("biome")
                || AutoTriggerManager.hasType("structure")
                || AutoTriggerManager.hasType("item")
                || AutoTriggerManager.hasType("stat")
                || AutoTriggerManager.hasType("xp_level")
                || AutoTriggerManager.hasType("weather")
                || AutoTriggerManager.hasType("world_time"))) {
            return true;
        }
        return tickCounter % 100 == 0
                && (AutoTriggerManager.hasType("playtime") || AutoTriggerManager.hasType("day_count"));
    }

    /**
     * One player's share of a polled tick.
     *
     * <p>Public because a GameTest drives it directly: the test player is built without going
     * through the login path, so it is not in {@code server.getPlayerList()} and
     * {@link #pollPlayers} would walk straight past it. The flags are re-derived per player rather
     * than hoisted out of the loop — nine map lookups against an index that is usually empty is
     * not worth a nine-argument signature.
     */
    public static void pollPlayer(ServerPlayer p, int tickCounter) {
        boolean second = tickCounter % 20 == 0;
        boolean fiveSeconds = tickCounter % 100 == 0;
        if (second && AutoTriggerManager.hasType("biome")) pollBiome(p);
        if (second && AutoTriggerManager.hasType("structure")) pollStructure(p);
        if (second && AutoTriggerManager.hasType("item")) scanInventoryForItemTriggers(p);
        if (second && AutoTriggerManager.hasType("stat")) pollStat(p);
        if (second && AutoTriggerManager.hasType("xp_level")) pollXpLevel(p);
        if (second && AutoTriggerManager.hasType("weather")) pollWeather(p);
        if (second && AutoTriggerManager.hasType("world_time")) pollWorldTime(p);
        if (fiveSeconds && AutoTriggerManager.hasType("playtime")) pollPlaytime(p);
        if (fiveSeconds && AutoTriggerManager.hasType("day_count")) pollDayCount(p);
    }

    private static void pollBiome(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl == null) return;
        Holder<Biome> biomeHolder = sl.getBiome(p.blockPosition());
        ResourceLocation biomeLoc = sl.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(biomeHolder.value());
        if (biomeLoc == null) return;
        String biomeId = biomeLoc.toString();
        AutoTriggerManager.process(
                "biome",
                t -> (t instanceof BiomeTrigger bt) && biomeId.equals(bt.id()),
                p);
    }

    private static void pollStructure(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl == null) return;
        net.minecraft.core.BlockPos pos = p.blockPosition();

        // Use the same cluster/shape detection as the structure lock so auto-unlock
        // fires exactly when the player enters a lock zone — and support #tag entries.
        int padding = net.bananemdnsa.historystages.Config.GAMEPLAY.structureLockPadding.get();
        int clusterDistance = net.bananemdnsa.historystages.Config.GAMEPLAY.structureClusterDistance.get();
        java.util.List<net.bananemdnsa.historystages.structure.StructureCluster> clusters =
                net.bananemdnsa.historystages.structure.ClusterBuilder.collectClustersNear(
                        sl, pos, StructureLockHandler.CHUNK_SCAN_RADIUS, padding, clusterDistance);
        if (clusters.isEmpty()) return;

        Set<String> presentIds = new HashSet<>();
        Set<String> presentTags = new HashSet<>();
        for (net.bananemdnsa.historystages.structure.StructureCluster c : clusters) {
            if (!c.contains(pos)) continue;
            Holder.Reference<net.minecraft.world.level.levelgen.structure.Structure> h = c.structure();
            h.unwrapKey().ifPresent(k -> presentIds.add(k.location().toString()));
            h.tags().forEach(tag -> presentTags.add(tag.location().toString()));
        }
        if (presentIds.isEmpty() && presentTags.isEmpty()) return;

        AutoTriggerManager.process(
                "structure",
                t -> {
                    if (!(t instanceof StructureTrigger st)) return false;
                    String id = st.id();
                    if (id == null || id.isEmpty()) return false;
                    return id.startsWith("#")
                            ? presentTags.contains(id.substring(1))
                            : presentIds.contains(id);
                },
                p);
    }

    private static void pollPlaytime(ServerPlayer p) {
        int playTicks = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        AutoTriggerManager.process(
                "playtime",
                t -> (t instanceof PlaytimeTrigger pt) && playTicks >= pt.requiredTicks(),
                p);
    }

    private static void pollStat(ServerPlayer p) {
        AutoTriggerManager.process(
                "stat",
                t -> {
                    if (!(t instanceof StatTrigger st)) return false;
                    Stat<?> stat = resolveStat(st);
                    // An id that is not in its registry, or a category this build does not know.
                    // Neither can be produced by the editor, so this is a hand-edited file — and
                    // the answer has to be "never fires", not an exception in the server tick.
                    if (stat == null) return false;
                    return st.matches(p.getStats().getValue(stat));
                },
                p);
    }

    @Nullable
    private static Stat<?> resolveStat(StatTrigger trigger) {
        StatCategory category = trigger.resolvedCategory();
        if (category == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(trigger.id());
        if (id == null) return null;
        return switch (category) {
            case CUSTOM -> {
                ResourceLocation custom = BuiltInRegistries.CUSTOM_STAT.get(id);
                yield custom == null ? null : Stats.CUSTOM.get(custom);
            }
            // BLOCK and ITEM are defaulted registries: an unknown id comes back as air rather than
            // null, so the key has to be checked instead of the value.
            case MINED -> blockStat(Stats.BLOCK_MINED, id);
            case CRAFTED -> itemStat(Stats.ITEM_CRAFTED, id);
            case USED -> itemStat(Stats.ITEM_USED, id);
            case BROKEN -> itemStat(Stats.ITEM_BROKEN, id);
            case PICKED_UP -> itemStat(Stats.ITEM_PICKED_UP, id);
            case DROPPED -> itemStat(Stats.ITEM_DROPPED, id);
            case KILLED -> entityStat(Stats.ENTITY_KILLED, id);
            case KILLED_BY -> entityStat(Stats.ENTITY_KILLED_BY, id);
        };
    }

    @Nullable
    private static Stat<Block> blockStat(StatType<Block> type, ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id)
                ? type.get(BuiltInRegistries.BLOCK.get(id)) : null;
    }

    @Nullable
    private static Stat<Item> itemStat(StatType<Item> type, ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                ? type.get(BuiltInRegistries.ITEM.get(id)) : null;
    }

    @Nullable
    private static Stat<EntityType<?>> entityStat(StatType<EntityType<?>> type, ResourceLocation id) {
        EntityType<?> entity = BuiltInRegistries.ENTITY_TYPE.get(id);
        return entity == null ? null : type.get(entity);
    }

    private static void pollXpLevel(ServerPlayer p) {
        int level = p.experienceLevel;
        AutoTriggerManager.process(
                "xp_level",
                t -> (t instanceof XpLevelTrigger xt) && xt.matches(level),
                p);
    }

    private static void pollWeather(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl == null) return;
        boolean raining = sl.isRaining();
        boolean thundering = sl.isThundering();
        AutoTriggerManager.process(
                "weather",
                t -> (t instanceof WeatherTrigger wt) && wt.matches(raining, thundering),
                p);
    }

    private static void pollWorldTime(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl == null) return;
        long dayTime = sl.getDayTime();
        AutoTriggerManager.process(
                "world_time",
                t -> (t instanceof TimeOfDayTrigger tt) && tt.matches(dayTime),
                p);
    }

    private static void pollDayCount(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (sl == null) return;
        long dayTime = sl.getDayTime();
        AutoTriggerManager.process(
                "day_count",
                t -> (t instanceof DayCountTrigger dt) && dt.matches(dayTime),
                p);
    }
}
