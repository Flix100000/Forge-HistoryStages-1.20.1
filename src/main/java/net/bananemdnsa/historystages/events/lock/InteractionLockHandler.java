package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.clientbound.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.trading.Merchant;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blocks right-click interactions with locked entities (breeding, mounting, trading, naming,
 * leashing, etc.). Attacking/killing and spawning are unaffected — those stay allowed.
 *
 * <p>Unlike {@link MobLockHandler} (which handles a damage event with no client prediction), a
 * {@link PlayerInteractEvent} is predicted on the client — e.g. milking swaps the bucket locally
 * before the server replies. The event must therefore be cancelled on BOTH sides, checking the
 * client stage cache on the client and the server cache on the server (mirrors
 * {@link ItemUseLockHandler} / {@link EntityItemLockHandler}).
 *
 * <p>Each interaction is classified into one of the recognised actions (see
 * {@link net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry#ALL_ACTIONS}); a stage
 * only blocks it when its per-entry action filter covers that action. Classification is a
 * best-effort heuristic based on the held item and the target entity type.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class InteractionLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleInteract(event.getEntity(), event.getTarget(), event.getItemStack(), event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleInteract(event.getEntity(), event.getTarget(), event.getItemStack(), event);
    }

    private static void handleInteract(Player interactor, Entity target, ItemStack held, ICancellableEvent event) {
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (entityType == null) return;

        String entityId = entityType.toString();
        String action = classifyAction(target, held);

        List<String> globalStageIds = StageManager.getAllStagesForInteractionLockedEntity(entityId, action, held);
        List<String> individualStageIds = StageManager.getAllIndividualStagesForInteractionLockedEntity(entityId, action, held);
        if (globalStageIds.isEmpty() && individualStageIds.isEmpty()) return;

        boolean isClient = interactor.level().isClientSide();

        List<String> lockedStages = new ArrayList<>();
        if (isClient) {
            for (String stageId : globalStageIds) {
                if (!ClientStageCache.isStageUnlocked(stageId)) lockedStages.add(stageId);
            }
            for (String stageId : individualStageIds) {
                if (!ClientIndividualStageCache.isStageUnlocked(stageId)) lockedStages.add(stageId);
            }
        } else {
            for (String stageId : globalStageIds) {
                if (!StageData.SERVER_CACHE.contains(stageId)) lockedStages.add(stageId);
            }
            if (!individualStageIds.isEmpty()) {
                Set<String> playerStages = IndividualStageData.SERVER_CACHE.getOrDefault(interactor.getUUID(), Collections.emptySet());
                for (String stageId : individualStageIds) {
                    if (!playerStages.contains(stageId)) lockedStages.add(stageId);
                }
            }
        }

        if (lockedStages.isEmpty()) return;

        // Cancel on both sides so the client does not keep its predicted result (e.g. a milk bucket).
        event.setCanceled(true);

        if (isClient || !(interactor instanceof ServerPlayer player)) return;

        DebugLogger.runtimeThrottled("Entity Interaction Lock", "entity_interact_" + player.getUUID() + "_" + entityId,
                "<" + player.getName().getString() + "> Interaction [" + action + "] with '" + entityId + "' blocked — missing stages: " + lockedStages);

        long now = System.currentTimeMillis();
        Long lastMessage = MESSAGE_COOLDOWNS.get(player.getUUID());
        if (lastMessage != null && (now - lastMessage) < COOLDOWN_MS) return;
        MESSAGE_COOLDOWNS.put(player.getUUID(), now);

        List<String> displayNames = new ArrayList<>(lockedStages.size());
        for (String stageId : lockedStages) {
            StageEntry stageEntry = StageManager.getStages().get(stageId);
            displayNames.add(stageEntry != null ? stageEntry.getDisplayName() : stageId);
        }

        PacketHandler.sendLockFeedbackToPlayer(
                new LockFeedbackPacket(LockFeedbackPacket.KIND_MOB, displayNames),
                player
        );
    }

    /**
     * Best-effort classification of a right-click interaction into one recognised action.
     * Item-driven actions (name tag, lead, shears, bucket, saddle/armor) take precedence, then
     * breeding (feeding the target its food), trading (merchants), mounting (empty-hand on a
     * rideable), and finally the {@code other} catch-all.
     */
    private static String classifyAction(Entity target, ItemStack held) {
        Item item = held.getItem();
        if (item == Items.NAME_TAG) return "name";
        if (item == Items.LEAD) return "leash";
        if (item == Items.SHEARS || item instanceof ShearsItem) return "shear";
        if (item == Items.BUCKET) return "milk";
        // Armor stands: every interaction (placing, swapping or removing gear) is an equip action.
        if (target instanceof ArmorStand) return "equip";
        // Mob tack / wearable gear on living entities (saddle, horse/wolf armor, llama carpet).
        if (item == Items.SADDLE || item instanceof AnimalArmorItem || held.is(ItemTags.WOOL_CARPETS)) return "equip";
        if (target instanceof Animal animal && !held.isEmpty() && animal.isFood(held)) return "breed";
        if (target instanceof Merchant) return "trade";
        if (held.isEmpty() && (target instanceof AbstractHorse || target instanceof Saddleable)) return "mount";
        return "other";
    }
}
