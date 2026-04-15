package net.bananemdnsa.historystages.block.entity;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

public class ResearchPedestalBlockEntity extends BlockEntity implements MenuProvider {

    private final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            if (slot == 0) {
                ItemStack stack = getStackInSlot(0);
                if (!stack.isEmpty()) {
                    loadProgressFromItem(stack);
                    // Set owner UUID for individual stages ONLY if not already set
                    if (isCurrentScrollIndividual()) {
                        CompoundTag tag = stack.getOrCreateTag();
                        if (tag.hasUUID("OwnerUUID")) {
                            // Keep existing owner
                            ResearchPedestalBlockEntity.this.ownerUUID = tag.getUUID("OwnerUUID");
                        } else if (lastInteractingPlayer != null) {
                            // Assign new owner
                            ResearchPedestalBlockEntity.this.ownerUUID = lastInteractingPlayer;
                            tag.putUUID("OwnerUUID", ResearchPedestalBlockEntity.this.ownerUUID);
                            // Store owner name for client-side display
                            if (level != null && level.getServer() != null) {
                                net.minecraft.server.level.ServerPlayer ownerPlayer = level.getServer().getPlayerList()
                                        .getPlayer(ResearchPedestalBlockEntity.this.ownerUUID);
                                if (ownerPlayer != null) {
                                    tag.putString("OwnerName", ownerPlayer.getName().getString());
                                }
                            }
                        }
                    }
                } else {
                    ResearchPedestalBlockEntity.this.ownerUUID = null;
                }
            } else if (slot == 1) {
                // Reset deposit delay when item changed
                depositDelay = 0;
            }
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0) {
                return stack.is(ModItems.RESEARCH_SCROLL.get()) || stack.is(ModItems.CREATIVE_SCROLL.get());
            }
            // All items are potentially valid for deposit (checked during processing)
            return true;
        }
    };

    private void tryProcessDeposit(ItemStack depositStack) {
        ItemStack scroll = getScrollStack();
        if (scroll.isEmpty() || !scroll.hasTag() || !scroll.getTag().contains("StageResearch"))
            return;

        String stageId = scroll.getTag().getString("StageResearch");
        boolean isIndividual = StageManager.isIndividualStage(stageId);
        StageEntry entry = isIndividual ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);

        if (entry == null || entry.getDependencies() == null)
            return;

        ResourceLocation depositRl = ForgeRegistries.ITEMS.getKey(depositStack.getItem());
        if (depositRl == null)
            return;

        CompoundTag deposited = scroll.getOrCreateTagElement("DepositedDependencies");
        boolean changed = false;

        for (int i = 0; i < entry.getDependencies().size(); i++) {
            net.bananemdnsa.historystages.data.DependencyGroup group = entry.getDependencies().get(i);
            for (net.bananemdnsa.historystages.data.dependency.DependencyItem reqItem : group.getItems()) {
                ResourceLocation reqRl = ResourceLocation.tryParse(reqItem.getId());
                if (reqRl != null && reqRl.equals(depositRl)) {
                    String key = "Group_" + i + "_Item_" + reqRl.toString();
                    int current = deposited.getInt(key);
                    int needed = reqItem.getCount() - current;

                    if (needed > 0) {
                        int toTake = Math.min(needed, depositStack.getCount());
                        depositStack.shrink(toTake);
                        deposited.putInt(key, current + toTake);
                        changed = true;

                        if (depositStack.isEmpty())
                            break;
                    }
                }
            }
            if (depositStack.isEmpty())
                break;
        }

        if (changed) {
            scroll.setTag(scroll.getTag()); // Trigger sync
            setChanged();

            // Push update to watching players immediately
            if (entry != null && level != null && !level.isClientSide) {
                UUID checkUUID = isCurrentScrollIndividual() ? this.ownerUUID : this.lastInteractingPlayer;
                if (checkUUID != null) {
                    var player = level.getServer().getPlayerList().getPlayer(checkUUID);
                    if (player != null) {
                        var result = net.bananemdnsa.historystages.data.dependency.DependencyChecker.checkAll(entry,
                                player, level, scroll.getTag().getCompound("DepositedDependencies"));
                        net.bananemdnsa.historystages.network.PacketHandler.INSTANCE.send(
                                net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK
                                        .with(() -> level.getChunkAt(this.worldPosition)),
                                new net.bananemdnsa.historystages.network.SyncDependencyStatusPacket(stageId, result));
                    }
                }
            }
        }
    }

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    protected final ContainerData data;
    private int progress = 0;
    private int finishDelay = 0;
    private int depositDelay = 0;
    public static final int MAX_DEPOSIT_DELAY = 20; // 1 second
    private int syncTickDelay = -1;
    private UUID ownerUUID = null;
    private UUID lastInteractingPlayer = null;
    private boolean dependenciesMet = true; // Tracks if current stage's dependencies are fulfilled

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

    private void loadProgressFromItem(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("ResearchProgress")) {
            this.progress = stack.getTag().getInt("ResearchProgress");
        } else {
            this.progress = 0;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.historystages.research_pedestal");
    }

    public ItemStack getScrollStack() {
        return this.itemHandler.getStackInSlot(0);
    }

    public boolean hasScrollWithDependencies() {
        ItemStack stack = getScrollStack();
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains("StageResearch"))
            return false;
        String stageId = stack.getTag().getString("StageResearch");
        StageEntry entry = StageManager.isIndividualStage(stageId)
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        return entry != null && entry.hasDependencies();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        this.lastInteractingPlayer = pPlayer.getUUID();
        return new ResearchPedestalMenu(pContainerId, pPlayerInventory, this, this.data);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchPedestalBlockEntity entity) {
        if (level.isClientSide)
            return;

        // Handle item deposit delay logic
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

        // Neu: Warte kurz, bevor das Sync-Paket gesendet wird (Timing-Fix)
        if (entity.syncTickDelay > 0) {
            entity.syncTickDelay--;
        } else if (entity.syncTickDelay == 0) {
            entity.performGlobalSync();
            entity.syncTickDelay = -1;
        }

        ItemStack stack = entity.itemHandler.getStackInSlot(0);
        int maxProgress = entity.getMaxProgressForCurrentStage();

        boolean hasValidBook = !stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch");
        boolean isResearching = false;

        if (hasValidBook) {
            String stageId = stack.getTag().getString("StageResearch");
            boolean isCreative = ModItems.CREATIVE_STAGE_ID.equals(stageId);
            boolean isIndividual = !isCreative && StageManager.isIndividualStage(stageId);
            boolean alreadyUnlocked;

            if (isCreative) {
                // Creative scroll: never "already unlocked"
                alreadyUnlocked = false;
            } else if (isIndividual) {
                // Individual: check if the owner has this stage
                UUID owner = entity.ownerUUID;
                if (owner == null && stack.hasTag() && stack.getTag().hasUUID("OwnerUUID")) {
                    owner = stack.getTag().getUUID("OwnerUUID");
                    entity.ownerUUID = owner;
                }
                alreadyUnlocked = owner != null && IndividualStageData.hasStageCached(owner, stageId);
            } else {
                StageData data = StageData.get(level);
                alreadyUnlocked = data.getUnlockedStages().contains(stageId);
            }

            if (!alreadyUnlocked) {
                // Check non-item dependencies before allowing research
                boolean metTotal = false;
                if (!isCreative) {
                    StageEntry stageEntry = isIndividual
                            ? StageManager.getIndividualStages().get(stageId)
                            : StageManager.getStages().get(stageId);

                    if (stageEntry != null) {
                        if (stageEntry.hasDependencies()) {
                            // Find the researching player for dependency checks
                            net.minecraft.server.level.ServerPlayer researchPlayer = null;
                            UUID checkUUID = isIndividual ? entity.ownerUUID : entity.lastInteractingPlayer;
                            if (checkUUID != null && level.getServer() != null) {
                                researchPlayer = level.getServer().getPlayerList().getPlayer(checkUUID);
                            }

                            if (researchPlayer != null) {
                                CompoundTag depositedTag = stack.hasTag()
                                        && stack.getTag().contains("DepositedDependencies")
                                                ? stack.getTag().getCompound("DepositedDependencies")
                                                : null;
                                DependencyResult result = DependencyChecker.checkAll(stageEntry, researchPlayer, level,
                                        depositedTag);
                                metTotal = result.isFulfilled();
                            } else {
                                // No player available to check - pause research
                                metTotal = false;
                            }
                        } else {
                            // No dependencies defined - fulfill automatically
                            metTotal = true;
                        }
                    }
                } else {
                    // Creative always fulfills
                    metTotal = true;
                }

                entity.dependenciesMet = metTotal;
                if (metTotal) {
                    isResearching = true;
                    if (entity.progress < maxProgress) {
                        entity.progress++;
                        if (entity.progress % 10 == 0) {
                            CompoundTag nbt = stack.getOrCreateTag();
                            nbt.putInt("ResearchProgress", entity.progress);
                            nbt.putInt("MaxProgress", maxProgress);
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
        if (!level.isClientSide && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            String stageId = stack.getTag().getString("StageResearch");

            // Consuming items and XP is now handled when depositing into the scroll
            // so we don't need to do it here anymore.

            if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                finishCreativeResearch();
            } else if (StageManager.isIndividualStage(stageId)) {
                finishIndividualResearch(stack, stageId);
            } else {
                finishGlobalResearch(stack, stageId);
            }
        }

        // Station zurücksetzen und Buch verbrauchen
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
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.bananemdnsa.historystages.events.StageEvent.Unlocked(stageId, eventDisplayName));

            if (level.getServer() != null) {
                level.getServer().getCommands().performPrefixedCommand(
                        level.getServer().createCommandSourceStack().withSuppressedOutput(),
                        "history reload");
            }

            String stagename = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
            String configChat = Config.COMMON.unlockMessageFormat.get();
            String finalChat = configChat.replace("{stage}", stagename).replace("&", "§");

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
                PacketHandler
                        .sendToastToAll(new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(stagename));
            }
        }
    }

    private void finishIndividualResearch(ItemStack stack, String stageId) {
        if (ownerUUID == null)
            return;

        var stageEntry = StageManager.getIndividualStages().get(stageId);
        IndividualStageData data = IndividualStageData.get(level);

        if (!data.hasStage(ownerUUID, stageId)) {
            data.addStage(ownerUUID, stageId);
            data.setDirty();

            String eventDisplayName = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.bananemdnsa.historystages.events.StageEvent.IndividualUnlocked(stageId, eventDisplayName,
                            ownerUUID));

            // Sync individual stages to the owner player only
            if (level.getServer() != null) {
                net.minecraft.server.level.ServerPlayer ownerPlayer = level.getServer().getPlayerList()
                        .getPlayer(ownerUUID);
                if (ownerPlayer != null) {
                    PacketHandler.sendIndividualStagesToPlayer(
                            new SyncIndividualStagesPacket(data.getUnlockedStages(ownerUUID)),
                            ownerPlayer);

                    // Notify the owner player
                    String stagename = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
                    if (Config.COMMON.individualBroadcastChat.get()) {
                        String configChat = Config.COMMON.individualUnlockMessageFormat.get();
                        String finalChat = configChat.replace("{stage}", stagename)
                                .replace("{player}", ownerPlayer.getName().getString())
                                .replace("&", "§");
                        ownerPlayer.sendSystemMessage(
                                Component.literal("[HistoryStages] ")
                                        .withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(finalChat)));
                    }
                    if (Config.COMMON.individualUseActionbar.get()) {
                        String configChat = Config.COMMON.individualUnlockMessageFormat.get();
                        String finalChat = configChat.replace("{stage}", stagename)
                                .replace("{player}", ownerPlayer.getName().getString())
                                .replace("&", "§");
                        ownerPlayer.displayClientMessage(Component.literal(finalChat), true);
                    }
                    if (Config.COMMON.individualUseSounds.get()) {
                        ownerPlayer.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F,
                                1.0F);
                    }
                    if (Config.COMMON.individualUseToasts.get()) {
                        PacketHandler.INSTANCE.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> ownerPlayer),
                                new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(stagename));
                    }
                }
            }
            // No recipe reload needed for individual stages
        }
    }

    private void finishCreativeResearch() {
        if (level.getServer() == null)
            return;

        // Unlock all global stages
        StageData stageData = StageData.get(level);
        for (String id : StageManager.getStages().keySet()) {
            if (!stageData.getUnlockedStages().contains(id)) {
                stageData.addStage(id);
            }
        }
        stageData.setDirty();

        // Reload recipes
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput(),
                "history reload");

        // Unlock all individual stages for all online players
        IndividualStageData individualData = IndividualStageData.get(level);
        for (net.minecraft.server.level.ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            for (String id : StageManager.getIndividualStages().keySet()) {
                if (!individualData.hasStage(player.getUUID(), id)) {
                    individualData.addStage(player.getUUID(), id);
                }
            }
            // Sync individual stages to each player
            PacketHandler.sendIndividualStagesToPlayer(
                    new SyncIndividualStagesPacket(individualData.getUnlockedStages(player.getUUID())),
                    player);
        }
        individualData.setDirty();

        // Sync global stages and notify
        PacketHandler.sendToAll(new SyncStagesPacket(new java.util.ArrayList<>(StageData.SERVER_CACHE)));

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
                        net.minecraft.sounds.SoundSource.MASTER, 0.75F, 1.0F);
            }
        });
    }

    private int getMaxProgressForCurrentStage() {
        ItemStack stack = this.itemHandler.getStackInSlot(0);
        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            String stageId = stack.getTag().getString("StageResearch");
            if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                return Config.COMMON.researchTimeInSeconds.get() * 20;
            }
            if (StageManager.isIndividualStage(stageId)) {
                return StageManager.getIndividualResearchTimeInTicks(stageId);
            }
            return StageManager.getResearchTimeInTicks(stageId);
        }
        return Config.COMMON.researchTimeInSeconds.get() * 20;
    }

    public boolean isCurrentScrollIndividual() {
        ItemStack stack = this.itemHandler.getStackInSlot(0);
        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            return StageManager.isIndividualStage(stack.getTag().getString("StageResearch"));
        }
        return false;
    }

    private void performGlobalSync() {
        StageData data = StageData.get(this.level);
        StageData.refreshCache(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(StageData.SERVER_CACHE)));
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER)
            return lazyItemHandler.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        nbt.put("inventory", itemHandler.serializeNBT());
        nbt.putInt("research.progress", progress);
        nbt.putInt("research.finishDelay", finishDelay);
        if (ownerUUID != null) {
            nbt.putUUID("research.ownerUUID", ownerUUID);
        }
        super.saveAdditional(nbt);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);

        // Manual loading to prevent shrinking if loaded from old NBT
        CompoundTag invTag = nbt.getCompound("inventory");
        if (invTag.contains("Size", 3)) { // 3 is Tag.TAG_INT
            int savedSize = invTag.getInt("Size");
            if (savedSize != itemHandler.getSlots()) {
                // If the saved size is different, we load what we can but keep our 2 slots
                ItemStackHandler temp = new ItemStackHandler(savedSize);
                temp.deserializeNBT(invTag);
                for (int i = 0; i < Math.min(savedSize, itemHandler.getSlots()); i++) {
                    itemHandler.setStackInSlot(i, temp.getStackInSlot(i));
                }
            } else {
                itemHandler.deserializeNBT(invTag);
            }
        } else {
            itemHandler.deserializeNBT(invTag);
        }

        progress = nbt.getInt("research.progress");
        finishDelay = nbt.getInt("research.finishDelay");
        if (nbt.hasUUID("research.ownerUUID")) {
            ownerUUID = nbt.getUUID("research.ownerUUID");
        }
    }

    private boolean isItemNeeded(ItemStack depositStack) {
        if (depositStack.isEmpty())
            return false;
        ItemStack scroll = getScrollStack();
        if (scroll.isEmpty() || !scroll.hasTag() || !scroll.getTag().contains("StageResearch"))
            return false;

        String stageId = scroll.getTag().getString("StageResearch");
        StageEntry entry = StageManager.isIndividualStage(stageId)
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (entry == null || !entry.hasDependencies())
            return false;

        String itemId = ForgeRegistries.ITEMS.getKey(depositStack.getItem()).toString();
        CompoundTag depositedData = scroll.getTag().getCompound("DepositedDependencies");

        return entry.getDependencies().stream().anyMatch(group -> group.getItems().stream().anyMatch(item -> {
            if (!item.getId().equals(itemId))
                return false;
            int count = depositedData.contains(itemId) ? depositedData.getInt(itemId) : 0;
            return count < item.getCount();
        }));
    }
}