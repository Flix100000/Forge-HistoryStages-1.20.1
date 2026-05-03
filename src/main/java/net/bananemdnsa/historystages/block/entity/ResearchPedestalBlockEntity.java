package net.bananemdnsa.historystages.block.entity;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.bananemdnsa.historystages.network.Networking;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

public class ResearchPedestalBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedScreenHandlerFactory<BlockPos> {
    private static final int[] TOP_SLOTS = new int[]{0};
    private static final int[] SIDE_SLOTS = new int[]{1};
    private static final int[] BOTTOM_SLOTS = new int[]{0, 1};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int progress = 0;
    private int finishDelay = 0;
    private int depositDelay = 0;
    private boolean dependenciesMet = true;
    private UUID ownerUUID;
    private UUID lastInteractingPlayer;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> getMaxProgressForCurrentStage();
                case 2 -> finishDelay;
                case 3 -> isCurrentScrollIndividual() ? 1 : 0;
                case 4 -> dependenciesMet ? 1 : 0;
                case 5 -> depositDelay;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 2 -> finishDelay = value;
                case 4 -> dependenciesMet = value == 1;
                case 5 -> depositDelay = value;
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public ResearchPedestalBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.RESEARCH_PEDESTAL, pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchPedestalBlockEntity entity) {
        if (level.isClientSide()) {
            return;
        }

        ItemStack scroll = entity.getItem(0);
        ItemStack deposit = entity.getItem(1);
        boolean hasValidScroll = entity.hasValidScroll(scroll);
        boolean researching = false;

        if (!deposit.isEmpty() && entity.isItemNeeded(deposit)) {
            entity.depositDelay++;
            if (entity.depositDelay >= 20) {
                entity.tryProcessDeposit(deposit);
                entity.depositDelay = 0;
            }
        } else {
            entity.depositDelay = 0;
        }

        if (hasValidScroll) {
            String stageId = entity.getStageId(scroll);
            boolean creative = ModItems.CREATIVE_STAGE_ID.equals(stageId);
            boolean individual = !creative && StageManager.isIndividualStage(stageId);
            boolean alreadyUnlocked = creative ? false : entity.isAlreadyUnlocked(stageId, individual);

            if (!alreadyUnlocked) {
                boolean met = entity.checkDependencies(scroll, stageId, individual, creative);
                entity.dependenciesMet = met;
                if (met) {
                    researching = true;
                    int maxProgress = entity.getMaxProgressForCurrentStage();
                    if (entity.progress < maxProgress) {
                        entity.progress++;
                        entity.writeProgressToScroll(scroll, maxProgress);
                    } else {
                        entity.finishDelay++;
                        if (entity.finishDelay >= 20) {
                            entity.finishResearch(scroll, stageId, individual, creative);
                        }
                    }
                }
            } else {
                entity.progress = 0;
                entity.finishDelay = 0;
            }
        } else {
            entity.progress = 0;
            entity.finishDelay = 0;
            entity.dependenciesMet = true;
        }

        if (state.getBlock() instanceof net.bananemdnsa.historystages.block.ResearchPedestalBlock) {
            boolean workingState = hasValidScroll;
            boolean litState = researching;
            if (state.getValue(net.bananemdnsa.historystages.block.ResearchPedestalBlock.WORKING) != workingState
                    || state.getValue(net.bananemdnsa.historystages.block.ResearchPedestalBlock.LIT) != litState) {
                level.setBlock(pos, state
                        .setValue(net.bananemdnsa.historystages.block.ResearchPedestalBlock.WORKING, workingState)
                        .setValue(net.bananemdnsa.historystages.block.ResearchPedestalBlock.LIT, litState), 3);
            }
        }

        entity.setChanged();
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.historystages.research_pedestal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        lastInteractingPlayer = player.getUUID();
        return new net.bananemdnsa.historystages.screen.ResearchPedestalMenu(containerId, playerInventory, this, data);
    }

    public ItemStack getScrollStack() {
        return getItem(0);
    }

    public boolean hasScrollWithDependencies() {
        ItemStack stack = getScrollStack();
        if (!hasValidScroll(stack)) {
            return false;
        }
        StageEntry entry = getCurrentStageEntry(stack);
        return entry != null && entry.hasDependencies();
    }

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return side == net.minecraft.core.Direction.UP ? TOP_SLOTS : (side == net.minecraft.core.Direction.DOWN ? BOTTOM_SLOTS : SIDE_SLOTS);
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable net.minecraft.core.Direction direction) {
        return canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, net.minecraft.core.Direction direction) {
        return true;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) {
            setChanged();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (slot == 0) {
            progress = 0;
            finishDelay = 0;
            if (hasValidScroll(stack) && isCurrentScrollIndividual()) {
                CompoundTag tag = getCustomTag(stack);
                if (tag.hasUUID("OwnerUUID")) {
                    ownerUUID = tag.getUUID("OwnerUUID");
                } else if (lastInteractingPlayer != null) {
                    ownerUUID = lastInteractingPlayer;
                    tag.putUUID("OwnerUUID", ownerUUID);
                    if (level != null && level.getServer() != null) {
                        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
                        if (owner != null) {
                            tag.putString("OwnerName", owner.getName().getString());
                        }
                    }
                    setCustomTag(stack, tag);
                }
            }
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(
                worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 0) {
            return stack.is(ModItems.RESEARCH_SCROLL) || stack.is(ModItems.CREATIVE_SCROLL);
        }
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("research.progress", progress);
        tag.putInt("research.finishDelay", finishDelay);
        tag.putInt("research.depositDelay", depositDelay);
        if (ownerUUID != null) {
            tag.putUUID("research.ownerUUID", ownerUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = tag.getInt("research.progress");
        finishDelay = tag.getInt("research.finishDelay");
        depositDelay = tag.getInt("research.depositDelay");
        if (tag.hasUUID("research.ownerUUID")) {
            ownerUUID = tag.getUUID("research.ownerUUID");
        }
    }

    private boolean hasValidScroll(ItemStack stack) {
        CompoundTag tag = getCustomTag(stack);
        return !stack.isEmpty() && (stack.is(ModItems.RESEARCH_SCROLL) || stack.is(ModItems.CREATIVE_SCROLL))
                && tag.contains("StageResearch");
    }

    private String getStageId(ItemStack stack) {
        return getCustomTag(stack).getString("StageResearch");
    }

    private boolean isAlreadyUnlocked(String stageId, boolean individual) {
        if (individual) {
            UUID owner = ownerUUID;
            return owner != null && IndividualStageData.hasStageCached(owner, stageId);
        }
        return StageData.SERVER_CACHE.contains(stageId);
    }

    private boolean checkDependencies(ItemStack scroll, String stageId, boolean individual, boolean creative) {
        if (creative) {
            return true;
        }
        StageEntry entry = getCurrentStageEntry(scroll);
        if (entry == null || !entry.hasDependencies()) {
            return true;
        }
        ServerPlayer player = resolveResearchingPlayer(individual);
        if (player == null) {
            return false;
        }
        CompoundTag tag = getCustomTag(scroll);
        CompoundTag deposited = tag.contains("DepositedDependencies") ? tag.getCompound("DepositedDependencies") : null;
        DependencyResult result = DependencyChecker.checkAll(entry, player, level, deposited);
        return result.isFulfilled();
    }

    private void tryProcessDeposit(ItemStack depositStack) {
        ItemStack scroll = getScrollStack();
        if (!hasValidScroll(scroll)) {
            return;
        }
        StageEntry entry = getCurrentStageEntry(scroll);
        if (entry == null || !entry.hasDependencies()) {
            return;
        }
        String depositId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(depositStack.getItem()).toString();
        CompoundTag tag = getCustomTag(scroll);
        CompoundTag deposited = tag.contains("DepositedDependencies") ? tag.getCompound("DepositedDependencies") : new CompoundTag();
        boolean changed = false;

        for (int i = 0; i < entry.getDependencies().size(); i++) {
            var group = entry.getDependencies().get(i);
            for (var reqItem : group.getItems()) {
                if (depositId.equals(reqItem.getId())) {
                    String key = "Group_" + i + "_Item_" + reqItem.getId();
                    int current = deposited.getInt(key);
                    int needed = reqItem.getCount() - current;
                    if (needed > 0) {
                        int toTake = Math.min(needed, depositStack.getCount());
                        depositStack.shrink(toTake);
                        deposited.putInt(key, current + toTake);
                        changed = true;
                        if (depositStack.isEmpty()) {
                            break;
                        }
                    }
                }
            }
            if (depositStack.isEmpty()) {
                break;
            }
        }

        if (changed) {
            tag.put("DepositedDependencies", deposited);
            setCustomTag(scroll, tag);
            setChanged();
        }
    }

    private void writeProgressToScroll(ItemStack scroll, int maxProgress) {
        CompoundTag tag = getCustomTag(scroll);
        tag.putInt("ResearchProgress", progress);
        tag.putInt("MaxProgress", maxProgress);
        setCustomTag(scroll, tag);
    }

    private void finishResearch(ItemStack scroll, String stageId, boolean individual, boolean creative) {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (creative) {
            finishCreativeResearch();
        } else if (individual) {
            finishIndividualResearch(stageId);
        } else {
            finishGlobalResearch(stageId);
        }

        progress = 0;
        finishDelay = 0;
        scroll.shrink(1);
        setChanged();
    }

    private void finishGlobalResearch(String stageId) {
        StageData data = StageData.get(level);
        if (!data.hasStage(stageId)) {
            data.addStage(stageId);
            notifyUnlock(stageId, false, null);
            Networking.syncAll(level.getServer());
        }
    }

    private void finishIndividualResearch(String stageId) {
        if (ownerUUID == null) {
            return;
        }
        IndividualStageData data = IndividualStageData.get(level);
        if (!data.hasStage(ownerUUID, stageId)) {
            data.addStage(ownerUUID, stageId);
            notifyUnlock(stageId, true, ownerUUID);
            Networking.syncAll(level.getServer());
        }
    }

    private void finishCreativeResearch() {
        StageData global = StageData.get(level);
        for (String stage : StageManager.getStages().keySet()) {
            global.addStage(stage);
        }
        IndividualStageData individual = IndividualStageData.get(level);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            for (String stage : StageManager.getIndividualStages().keySet()) {
                individual.addStage(player.getUUID(), stage);
            }
            player.sendSystemMessage(Component.translatable("command.historystages.unlocked_all"));
        }
        Networking.syncAll(level.getServer());
    }

    private void notifyUnlock(String stageId, boolean individual, UUID owner) {
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId) : StageManager.getStages().get(stageId);
        String displayName = entry != null ? entry.getDisplayName() : stageId;
        if (individual && owner != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                player.sendSystemMessage(Component.literal("Unlocked individual stage: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(displayName).withStyle(ChatFormatting.AQUA)));
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        } else {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                player.sendSystemMessage(Component.literal("Unlocked stage: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(displayName).withStyle(ChatFormatting.AQUA)));
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
            }
        }
    }

    private int getMaxProgressForCurrentStage() {
        ItemStack stack = getScrollStack();
        if (hasValidScroll(stack)) {
            String stageId = getStageId(stack);
            if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds * 20;
            }
            if (StageManager.isIndividualStage(stageId)) {
                return StageManager.getIndividualResearchTimeInTicks(stageId);
            }
            return StageManager.getResearchTimeInTicks(stageId);
        }
        return net.bananemdnsa.historystages.Config.COMMON.researchTimeInSeconds * 20;
    }

    public boolean isCurrentScrollIndividual() {
        ItemStack stack = getScrollStack();
        return hasValidScroll(stack) && StageManager.isIndividualStage(getStageId(stack));
    }

    private boolean isItemNeeded(ItemStack depositStack) {
        ItemStack scroll = getScrollStack();
        if (!hasValidScroll(scroll)) {
            return false;
        }
        StageEntry entry = getCurrentStageEntry(scroll);
        if (entry == null || !entry.hasDependencies()) {
            return false;
        }
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(depositStack.getItem()).toString();
        CompoundTag tag = getCustomTag(scroll);
        CompoundTag deposited = tag.contains("DepositedDependencies") ? tag.getCompound("DepositedDependencies") : new CompoundTag();
        for (int i = 0; i < entry.getDependencies().size(); i++) {
            var group = entry.getDependencies().get(i);
            for (var item : group.getItems()) {
                if (itemId.equals(item.getId())) {
                    String key = "Group_" + i + "_Item_" + item.getId();
                    if (deposited.getInt(key) < item.getCount()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private StageEntry getCurrentStageEntry(ItemStack stack) {
        if (!hasValidScroll(stack)) {
            return null;
        }
        String stageId = getStageId(stack);
        return StageManager.isIndividualStage(stageId) ? StageManager.getIndividualStages().get(stageId) : StageManager.getStages().get(stageId);
    }

    private ServerPlayer resolveResearchingPlayer(boolean individual) {
        if (level == null || level.getServer() == null) {
            return null;
        }
        UUID uuid = individual ? ownerUUID : lastInteractingPlayer;
        return uuid != null ? level.getServer().getPlayerList().getPlayer(uuid) : null;
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void setCustomTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
