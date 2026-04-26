package net.bananemdnsa.historystages.block.entity;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncDependencyStatusPacket;
import net.bananemdnsa.historystages.network.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

public class ResearchPedestalBlockEntity extends BlockEntity implements MenuProvider {

    // Slot 0: Research Scroll, Slot 1: Deposit item
    private final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            if (slot == 0) {
                ItemStack stack = getStackInSlot(0);
                if (!stack.isEmpty()) {
                    loadProgressFromItem(stack);
                    // Set owner UUID for individual stages ONLY if not already set
                    if (isCurrentScrollIndividual()) {
                        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                        if (tag.hasUUID("OwnerUUID")) {
                            // Keep existing owner
                            ResearchPedestalBlockEntity.this.ownerUUID = tag.getUUID("OwnerUUID");
                        } else if (lastInteractingPlayer != null) {
                            // Assign new owner
                            ResearchPedestalBlockEntity.this.ownerUUID = lastInteractingPlayer;
                            tag.putUUID("OwnerUUID", ResearchPedestalBlockEntity.this.ownerUUID);
                            // Store owner name for client-side display
                            if (level != null && level.getServer() != null) {
                                net.minecraft.server.level.ServerPlayer ownerPlayer =
                                        level.getServer().getPlayerList().getPlayer(
                                                ResearchPedestalBlockEntity.this.ownerUUID);
                                if (ownerPlayer != null) {
                                    tag.putString("OwnerName", ownerPlayer.getName().getString());
                                }
                            }
                            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        }
                    }
                } else {
                    ResearchPedestalBlockEntity.this.ownerUUID = null;
                }
            } else if (slot == 1) {
                // Reset deposit delay when deposit slot changes
                depositDelay = 0;
            }
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0) {
                return stack.is(ModItems.RESEARCH_SCROLL.get()) || stack.is(ModItems.CREATIVE_SCROLL.get());
            }
            // Slot 1: all items are potentially valid for deposit
            return true;
        }
    };

    protected final ContainerData data;
    private int progress = 0;
    private int finishDelay = 0;
    private int depositDelay = 0;
    public static final int MAX_DEPOSIT_DELAY = 20; // 1 second
    private int syncTickDelay = -1;
    private UUID ownerUUID = null;
    private UUID lastInteractingPlayer = null;
    private boolean dependenciesMet = true;

    public ResearchPedestalBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.RESEARCH_PEDESTAL_BE.get(), pPos, pBlockState);
        this.data = new ContainerData() {
            @Override
            public int get(int pIndex) {
                return switch (pIndex) {
                    case 0 -> ResearchPedestalBlockEntity.this.progress;
                    case 1 -> ResearchPedestalBlockEntity.this.getMaxProgressForCurrentStage();
                    case 2 -> ResearchPedestalBlockEntity.this.finishDelay;
                    case 3 -> ResearchPedestalBlockEntity.this.isCurrentScrollIndividual() ? 1 : 0;
                    case 4 -> ResearchPedestalBlockEntity.this.dependenciesMet ? 1 : 0;
                    case 5 -> ResearchPedestalBlockEntity.this.depositDelay;
                    default -> 0;
                };
            }

            @Override
            public void set(int pIndex, int pValue) {
                switch (pIndex) {
                    case 0 -> ResearchPedestalBlockEntity.this.progress = pValue;
                    case 2 -> ResearchPedestalBlockEntity.this.finishDelay = pValue;
                    case 4 -> ResearchPedestalBlockEntity.this.dependenciesMet = pValue == 1;
                    case 5 -> ResearchPedestalBlockEntity.this.depositDelay = pValue;
                }
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    public ItemStack getScrollStack() {
        return this.itemHandler.getStackInSlot(0);
    }

    public boolean hasScrollWithDependencies() {
        ItemStack stack = getScrollStack();
        if (stack.isEmpty()) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("StageResearch")) return false;
        String stageId = tag.getString("StageResearch");
        StageEntry entry = StageManager.isIndividualStage(stageId)
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        return entry != null && entry.hasDependencies();
    }

    private void loadProgressFromItem(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("ResearchProgress")) {
            this.progress = tag.getInt("ResearchProgress");
        } else {
            this.progress = 0;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.historystages.research_pedestal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        this.lastInteractingPlayer = pPlayer.getUUID();
        return new ResearchPedestalMenu(pContainerId, pPlayerInventory, this, this.data);
    }

    /**
     * Try to consume items from the deposit slot (slot 1) into the scroll's
     * DepositedDependencies NBT.
     */
    private void tryProcessDeposit(ItemStack depositStack) {
        ItemStack scroll = getScrollStack();
        if (scroll.isEmpty()) return;
        CompoundTag scrollTag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!scrollTag.contains("StageResearch")) return;

        String stageId = scrollTag.getString("StageResearch");
        boolean isIndividual = StageManager.isIndividualStage(stageId);
        StageEntry entry = isIndividual
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (entry == null || entry.getDependencies() == null) return;

        ResourceLocation depositRl = BuiltInRegistries.ITEM.getKey(depositStack.getItem());
        if (depositRl == null) return;

        CompoundTag deposited = scrollTag.contains("DepositedDependencies")
                ? scrollTag.getCompound("DepositedDependencies")
                : new CompoundTag();
        boolean changed = false;

        outer:
        for (int i = 0; i < entry.getDependencies().size(); i++) {
            var group = entry.getDependencies().get(i);
            for (var reqItem : group.getItems()) {
                ResourceLocation reqRl = ResourceLocation.tryParse(reqItem.getId());
                if (reqRl != null && reqRl.equals(depositRl)) {
                    String key = "Group_" + i + "_Item_" + reqRl;
                    int current = deposited.getInt(key);
                    int needed = reqItem.getCount() - current;
                    if (needed > 0) {
                        int toTake = Math.min(needed, depositStack.getCount());
                        depositStack.shrink(toTake);
                        deposited.putInt(key, current + toTake);
                        changed = true;
                        if (depositStack.isEmpty()) break outer;
                    }
                }
            }
        }

        if (changed) {
            scrollTag.put("DepositedDependencies", deposited);
            scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(scrollTag));
            setChanged();

            // Push updated dependency status to the researching player immediately
            if (level != null && !level.isClientSide && level.getServer() != null) {
                UUID checkUUID = isCurrentScrollIndividual() ? this.ownerUUID : this.lastInteractingPlayer;
                if (checkUUID != null) {
                    var player = level.getServer().getPlayerList().getPlayer(checkUUID);
                    if (player != null) {
                        CompoundTag updatedDeposited = scrollTag.contains("DepositedDependencies")
                                ? scrollTag.getCompound("DepositedDependencies") : null;
                        var result = DependencyChecker.checkAll(entry, player, level, updatedDeposited);
                        PacketDistributor_sendToPlayer(player,
                                new SyncDependencyStatusPacket(stageId, result));
                    }
                }
            }
        }
    }

    /** Send a packet to a specific player (extracted to avoid inline import). */
    private static void PacketDistributor_sendToPlayer(net.minecraft.server.level.ServerPlayer player,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }

    private boolean isItemNeeded(ItemStack depositStack) {
        if (depositStack.isEmpty()) return false;
        ItemStack scroll = getScrollStack();
        if (scroll.isEmpty()) return false;
        CompoundTag scrollTag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!scrollTag.contains("StageResearch")) return false;

        String stageId = scrollTag.getString("StageResearch");
        StageEntry entry = StageManager.isIndividualStage(stageId)
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (entry == null || !entry.hasDependencies()) return false;

        ResourceLocation depositRl = BuiltInRegistries.ITEM.getKey(depositStack.getItem());
        if (depositRl == null) return false;

        CompoundTag depositedData = scrollTag.contains("DepositedDependencies")
                ? scrollTag.getCompound("DepositedDependencies") : new CompoundTag();

        for (int i = 0; i < entry.getDependencies().size(); i++) {
            var group = entry.getDependencies().get(i);
            for (var item : group.getItems()) {
                if (item.getId().equals(depositRl.toString())) {
                    String key = "Group_" + i + "_Item_" + item.getId();
                    int count = depositedData.getInt(key);
                    if (count < item.getCount()) return true;
                }
            }
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchPedestalBlockEntity entity) {
        if (level.isClientSide) return;

        // Handle item deposit (slot 1): wait MAX_DEPOSIT_DELAY ticks then process
        ItemStack depositSlot = entity.itemHandler.getStackInSlot(1);
        if (!depositSlot.isEmpty() && entity.isItemNeeded(depositSlot)) {
            entity.depositDelay++;
            if (entity.depositDelay >= MAX_DEPOSIT_DELAY) {
                entity.tryProcessDeposit(depositSlot);
                entity.depositDelay = 0;
            }
        } else {
            entity.depositDelay = 0;
        }

        // Sync delay timer
        if (entity.syncTickDelay > 0) {
            entity.syncTickDelay--;
        } else if (entity.syncTickDelay == 0) {
            entity.performGlobalSync();
            entity.syncTickDelay = -1;
        }

        ItemStack stack = entity.itemHandler.getStackInSlot(0);
        int maxProgress = entity.getMaxProgressForCurrentStage();

        CompoundTag stackTag = !stack.isEmpty()
                ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                : new CompoundTag();
        boolean hasValidBook = !stack.isEmpty() && stackTag.contains("StageResearch");
        boolean isResearching = false;

        if (hasValidBook) {
            String stageId = stackTag.getString("StageResearch");
            boolean isCreative = ModItems.CREATIVE_STAGE_ID.equals(stageId);
            boolean isIndividual = !isCreative && StageManager.isIndividualStage(stageId);
            boolean alreadyUnlocked;

            if (isCreative) {
                alreadyUnlocked = false;
            } else if (isIndividual) {
                UUID owner = entity.ownerUUID;
                if (owner == null && stackTag.hasUUID("OwnerUUID")) {
                    owner = stackTag.getUUID("OwnerUUID");
                    entity.ownerUUID = owner;
                }
                alreadyUnlocked = owner != null && IndividualStageData.hasStageCached(owner, stageId);
            } else {
                StageData data = StageData.get(level);
                alreadyUnlocked = data.getUnlockedStages().contains(stageId);
            }

            if (!alreadyUnlocked) {
                boolean metTotal;
                if (isCreative) {
                    // Creative always fulfills
                    metTotal = true;
                } else {
                    StageEntry stageEntry = isIndividual
                            ? StageManager.getIndividualStages().get(stageId)
                            : StageManager.getStages().get(stageId);

                    if (stageEntry != null) {
                        if (stageEntry.hasDependencies()) {
                            net.minecraft.server.level.ServerPlayer researchPlayer = null;
                            UUID checkUUID = isIndividual ? entity.ownerUUID : entity.lastInteractingPlayer;
                            if (checkUUID != null && level.getServer() != null) {
                                researchPlayer = level.getServer().getPlayerList().getPlayer(checkUUID);
                            }
                            if (researchPlayer != null) {
                                CompoundTag depositedTag = stackTag.contains("DepositedDependencies")
                                        ? stackTag.getCompound("DepositedDependencies")
                                        : null;
                                DependencyResult result = DependencyChecker.checkAll(stageEntry, researchPlayer, level,
                                        depositedTag);
                                metTotal = result.isFulfilled();
                            } else {
                                // No player available — pause research
                                metTotal = false;
                            }
                        } else {
                            // No dependencies defined — always fulfilled
                            metTotal = true;
                        }
                    } else {
                        metTotal = false;
                    }
                }

                entity.dependenciesMet = metTotal;
                if (metTotal) {
                    isResearching = true;
                    if (entity.progress < maxProgress) {
                        entity.progress++;
                        if (entity.progress % 10 == 0) {
                            CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                            nbt.putInt("ResearchProgress", entity.progress);
                            nbt.putInt("MaxProgress", maxProgress);
                            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                        }
                    } else {
                        entity.finishDelay++;
                        if (entity.finishDelay >= 20) {
                            entity.finishResearch(stack);
                        }
                    }
                }
                // If dependencies not met, research pauses (progress stays, no increment)
            } else {
                entity.progress = 0;
            }
        } else {
            entity.progress = 0;
            entity.finishDelay = 0;
        }

        if (state.getValue(ResearchPedestalBlock.WORKING) != hasValidBook
                || state.getValue(ResearchPedestalBlock.LIT) != isResearching) {
            level.setBlock(pos, state.setValue(ResearchPedestalBlock.WORKING, hasValidBook)
                    .setValue(ResearchPedestalBlock.LIT, isResearching), 3);
        }
        setChanged(level, pos, state);
    }

    private void finishResearch(ItemStack stack) {
        CompoundTag stackTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!level.isClientSide && stackTag.contains("StageResearch")) {
            String stageId = stackTag.getString("StageResearch");

            // Consuming items and XP is now handled when depositing into the scroll.

            if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                finishCreativeResearch();
            } else if (StageManager.isIndividualStage(stageId)) {
                finishIndividualResearch(stack, stageId);
            } else {
                finishGlobalResearch(stack, stageId);
            }
        }

        this.progress = 0;
        this.finishDelay = 0;
        stack.shrink(1);
        setChanged();
    }

    private void finishGlobalResearch(ItemStack stack, String stageId) {
        var stageEntry = StageManager.getStages().get(stageId);
        StageData data = StageData.get(level);

        if (!data.getUnlockedStages().contains(stageId)) {
            data.addStage(stageId);
            data.setDirty();

            String eventDisplayName = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
            NeoForge.EVENT_BUS.post(
                    new net.bananemdnsa.historystages.events.StageEvent.Unlocked(stageId, eventDisplayName));

            if (level.getServer() != null) {
                level.getServer().getCommands().performPrefixedCommand(
                        level.getServer().createCommandSourceStack().withSuppressedOutput(),
                        "history reload");
            }

            String stagename = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
            String configChat = Config.COMMON.unlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", stagename).replace("&", "\u00A7");

            level.getServer().getPlayerList().getPlayers().forEach(player -> {
                if (Config.COMMON.broadcastChat.get()) {
                    player.sendSystemMessage(
                            Component.literal("[HistoryStages] ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(finalChat)));
                }
                if (Config.COMMON.useSounds.get()) {
                    player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
                }
            });

            if (Config.COMMON.useToasts.get()) {
                String iconId = (stageEntry != null && !stageEntry.getIcon().isEmpty())
                        ? stageEntry.getIcon() : Config.COMMON.defaultStageIcon.get();
                PacketHandler.sendToastToAll(
                        new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(stagename, iconId));
            }
        }
    }

    private void finishIndividualResearch(ItemStack stack, String stageId) {
        if (ownerUUID == null) return;

        var stageEntry = StageManager.getIndividualStages().get(stageId);
        IndividualStageData data = IndividualStageData.get(level);

        if (!data.hasStage(ownerUUID, stageId)) {
            data.addStage(ownerUUID, stageId);
            data.setDirty();

            String eventDisplayName = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
            NeoForge.EVENT_BUS.post(
                    new net.bananemdnsa.historystages.events.StageEvent.IndividualUnlocked(stageId, eventDisplayName,
                            ownerUUID));

            if (level.getServer() != null) {
                net.minecraft.server.level.ServerPlayer ownerPlayer =
                        level.getServer().getPlayerList().getPlayer(ownerUUID);
                if (ownerPlayer != null) {
                    PacketHandler.sendIndividualStagesToPlayer(
                            new SyncIndividualStagesPacket(data.getUnlockedStages(ownerUUID)),
                            ownerPlayer);

                    String stagename = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
                    if (Config.COMMON.individualBroadcastChat.get()) {
                        String configChat = Config.COMMON.individualUnlockMessageFormat.get();
                        String finalChat = configChat.replace("{stage}", stagename)
                                .replace("{player}", ownerPlayer.getName().getString())
                                .replace("&", "\u00A7");
                        ownerPlayer.sendSystemMessage(
                                Component.literal("[HistoryStages] ")
                                        .withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(finalChat)));
                    }
                    if (Config.COMMON.individualUseActionbar.get()) {
                        String configChat = Config.COMMON.individualUnlockMessageFormat.get();
                        String finalChat = configChat.replace("{stage}", stagename)
                                .replace("{player}", ownerPlayer.getName().getString())
                                .replace("&", "\u00A7");
                        ownerPlayer.displayClientMessage(Component.literal(finalChat), true);
                    }
                    if (Config.COMMON.individualUseSounds.get()) {
                        ownerPlayer.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER,
                                0.75F, 1.0F);
                    }
                    if (Config.COMMON.individualUseToasts.get()) {
                        String indIconId = (stageEntry != null && !stageEntry.getIcon().isEmpty())
                                ? stageEntry.getIcon() : Config.COMMON.defaultStageIcon.get();
                        PacketHandler.sendToastToPlayer(
                                new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(stagename, indIconId),
                                ownerPlayer);
                    }
                }
            }
        }
    }

    private void finishCreativeResearch() {
        if (level.getServer() == null) return;

        StageData stageData = StageData.get(level);
        for (String id : StageManager.getStages().keySet()) {
            if (!stageData.getUnlockedStages().contains(id)) {
                stageData.addStage(id);
            }
        }
        stageData.setDirty();

        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput(),
                "history reload");

        IndividualStageData individualData = IndividualStageData.get(level);
        for (net.minecraft.server.level.ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            for (String id : StageManager.getIndividualStages().keySet()) {
                if (!individualData.hasStage(player.getUUID(), id)) {
                    individualData.addStage(player.getUUID(), id);
                }
            }
            PacketHandler.sendIndividualStagesToPlayer(
                    new SyncIndividualStagesPacket(individualData.getUnlockedStages(player.getUUID())),
                    player);
        }
        individualData.setDirty();

        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(StageData.SERVER_CACHE)));

        level.getServer().getPlayerList().getPlayers().forEach(player -> {
            if (Config.COMMON.broadcastChat.get()) {
                player.sendSystemMessage(
                        Component.literal("[HistoryStages] ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.translatable("command.historystages.unlocked_all")
                                        .withStyle(ChatFormatting.GREEN)));
            }
            if (Config.COMMON.useSounds.get()) {
                player.playNotifySound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.MASTER, 0.75F, 1.0F);
            }
        });
    }

    private int getMaxProgressForCurrentStage() {
        ItemStack stack = this.itemHandler.getStackInSlot(0);
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                    return Config.COMMON.researchTimeInSeconds.get() * 20;
                }
                if (StageManager.isIndividualStage(stageId)) {
                    return StageManager.getIndividualResearchTimeInTicks(stageId);
                }
                return StageManager.getResearchTimeInTicks(stageId);
            }
        }
        return Config.COMMON.researchTimeInSeconds.get() * 20;
    }

    public boolean isCurrentScrollIndividual() {
        ItemStack stack = this.itemHandler.getStackInSlot(0);
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                return StageManager.isIndividualStage(tag.getString("StageResearch"));
            }
        }
        return false;
    }

    private void performGlobalSync() {
        StageData data = StageData.get(this.level);
        StageData.SERVER_CACHE.clear();
        StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(StageData.SERVER_CACHE)));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.put("inventory", itemHandler.serializeNBT(registries));
        nbt.putInt("research.progress", progress);
        nbt.putInt("research.finishDelay", finishDelay);
        if (ownerUUID != null) {
            nbt.putUUID("research.ownerUUID", ownerUUID);
        }
        super.saveAdditional(nbt, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        // Manual loading with size check for backward compatibility (old NBT had 1 slot)
        CompoundTag invTag = nbt.getCompound("inventory");
        if (invTag.contains("Size", 3)) {
            int savedSize = invTag.getInt("Size");
            if (savedSize != itemHandler.getSlots()) {
                ItemStackHandler temp = new ItemStackHandler(savedSize);
                temp.deserializeNBT(registries, invTag);
                for (int i = 0; i < Math.min(savedSize, itemHandler.getSlots()); i++) {
                    itemHandler.setStackInSlot(i, temp.getStackInSlot(i));
                }
            } else {
                itemHandler.deserializeNBT(registries, invTag);
            }
        } else {
            itemHandler.deserializeNBT(registries, invTag);
        }

        progress = nbt.getInt("research.progress");
        finishDelay = nbt.getInt("research.finishDelay");
        if (nbt.hasUUID("research.ownerUUID")) {
            ownerUUID = nbt.getUUID("research.ownerUUID");
        }
    }
}
