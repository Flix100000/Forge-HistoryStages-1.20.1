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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
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

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            ItemStack stack = getStackInSlot(slot);
            if (!stack.isEmpty()) {
                loadProgressFromItem(stack);
                // Set owner UUID for individual stages
                if (isCurrentScrollIndividual() && lastInteractingPlayer != null) {
                    ownerUUID = lastInteractingPlayer;
                    stack.getOrCreateTag().putUUID("OwnerUUID", ownerUUID);
                    // Store owner name for client-side display
                    if (level != null && level.getServer() != null) {
                        net.minecraft.server.level.ServerPlayer ownerPlayer = level.getServer().getPlayerList()
                                .getPlayer(ownerUUID);
                        if (ownerPlayer != null) {
                            stack.getOrCreateTag().putString("OwnerName", ownerPlayer.getName().getString());
                        }
                    }
                }
            } else {
                ownerUUID = null;
            }
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.is(ModItems.RESEARCH_SCROLL.get()) || stack.is(ModItems.CREATIVE_SCROLL.get());
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Prevent extraction of individual scrolls during research
            if (isCurrentScrollIndividual()) {
                ItemStack current = getStackInSlot(slot);
                if (!current.isEmpty() && current.hasTag() && current.getTag().contains("StageResearch")) {
                    String stageId = current.getTag().getString("StageResearch");
                    // Allow extraction only if the stage is already unlocked for the owner
                    if (ownerUUID != null && !IndividualStageData.hasStageCached(ownerUUID, stageId)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            return super.extractItem(slot, amount, simulate);
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    protected final ContainerData data;
    private int progress = 0;
    private int finishDelay = 0;
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
                    default -> 0;
                };
            }

            @Override
            public void set(int pIndex, int pValue) {
                switch (pIndex) {
                    case 0 -> ResearchPedestalBlockEntity.this.progress = pValue;
                    case 2 -> ResearchPedestalBlockEntity.this.finishDelay = pValue;
                    case 4 -> ResearchPedestalBlockEntity.this.dependenciesMet = pValue == 1;
                }
            }

            @Override
            public int getCount() {
                return 5;
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

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        this.lastInteractingPlayer = pPlayer.getUUID();
        return new ResearchPedestalMenu(pContainerId, pPlayerInventory, this, this.data);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchPedestalBlockEntity entity) {
        if (level.isClientSide)
            return;

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
                boolean dependenciesMet = true;
                if (!isCreative) {
                    StageEntry stageEntry = isIndividual
                            ? StageManager.getIndividualStages().get(stageId)
                            : StageManager.getStages().get(stageId);
                    if (stageEntry != null && stageEntry.hasDependencies()) {
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
                            dependenciesMet = result.isFulfilled();
                        } else {
                            // No player available to check - pause research
                            dependenciesMet = false;
                        }
                    }
                }

                entity.dependenciesMet = dependenciesMet;
                if (dependenciesMet) {
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

    private boolean isCurrentScrollIndividual() {
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
        itemHandler.deserializeNBT(nbt.getCompound("inventory"));
        progress = nbt.getInt("research.progress");
        finishDelay = nbt.getInt("research.finishDelay");
        if (nbt.hasUUID("research.ownerUUID")) {
            ownerUUID = nbt.getUUID("research.ownerUUID");
        }
    }
}