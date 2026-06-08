package net.bananemdnsa.historystages.events;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
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
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemEntity().getItem();
        if (stack.isEmpty()) return;
        fireItemTrigger(player, stack);
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        scanInventoryForItemTriggers(player);
    }

    private static void scanInventoryForItemTriggers(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) fireItemTrigger(player, s);
        }
    }

    private static void fireItemTrigger(ServerPlayer player, ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return;
        String itemId = key.toString();
        AutoTriggerManager.process(
                "item",
                t -> (t instanceof ItemTrigger it) && itemId.equals(it.id()),
                player);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
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

    /** Called every server tick from {@code HistoryStages.onServerTick}. */
    public static void pollPlayers(MinecraftServer server, int tickCounter) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (tickCounter % 20 == 0) {
                pollBiome(p);
                pollStructure(p);
            }
            if (tickCounter % 100 == 0) {
                pollPlaytime(p);
            }
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
        // Reuse the existing structure-detection helper used elsewhere in the codebase
        // (bounding-box scan over loaded chunks in a fixed radius around the player).
        java.util.List<String> coveringIds = StructureLockHandler.collectStructureIdsAt(sl, p.blockPosition());
        if (coveringIds.isEmpty()) return;
        Set<String> covering = new HashSet<>(coveringIds);
        AutoTriggerManager.process(
                "structure",
                t -> (t instanceof StructureTrigger st) && covering.contains(st.id()),
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
