package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.events.lock.StructureLockHandler;

import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntitySubMode;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

/**
 * Single Forge-bus listener that forwards relevant world events into
 * {@link AutoTriggerManager#process} so AUTO-mode stages can auto-unlock.
 *
 * <p>Registered on {@code MinecraftForge.EVENT_BUS} from {@code HistoryStages}'s
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
        ResourceLocation advLoc = event.getAdvancement().getId();
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
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(placed.getBlock());
        if (key == null) return;
        String blockId = key.toString();
        AutoTriggerManager.process(
                "block_place",
                t -> (t instanceof BlockPlaceTrigger bp) && blockId.equals(bp.id()),
                player);
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (!AutoTriggerManager.hasType("item")) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem().getItem();
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
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (key == null) continue;
            String itemId = key.toString();
            if (!seen.add(itemId)) continue;
            fireItemTriggerForId(player, itemId);
        }
    }

    private static void fireItemTrigger(ServerPlayer player, ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
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
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
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
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
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
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(event.getTarget().getType());
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

    /** Called every server tick from {@code HistoryStages.onServerTick}. */
    public static void pollPlayers(MinecraftServer server, int tickCounter) {
        if (server == null) return;
        boolean tickBiome     = tickCounter % 20  == 0 && AutoTriggerManager.hasType("biome");
        boolean tickStructure = tickCounter % 20  == 0 && AutoTriggerManager.hasType("structure");
        boolean tickInventory = tickCounter % 20  == 0 && AutoTriggerManager.hasType("item");
        boolean tickPlaytime  = tickCounter % 100 == 0 && AutoTriggerManager.hasType("playtime");
        if (!tickBiome && !tickStructure && !tickInventory && !tickPlaytime) return;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (tickBiome) pollBiome(p);
            if (tickStructure) pollStructure(p);
            if (tickInventory) scanInventoryForItemTriggers(p);
            if (tickPlaytime) pollPlaytime(p);
        }
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
        int padding = net.bananemdnsa.historystages.Config.COMMON.structureLockPadding.get();
        int clusterDistance = net.bananemdnsa.historystages.Config.COMMON.structureClusterDistance.get();
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
}
