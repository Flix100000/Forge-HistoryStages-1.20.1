package net.bananemdnsa.historystages.block.entity;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.block.MultiBlockResearchPedestalBlock;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.block.TieredPedestal;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.ScrollCompletion;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.research.BoosterUtil;
import net.bananemdnsa.historystages.research.ResearchBooster;
import net.bananemdnsa.historystages.research.ResearchBoosterRegistry;
import net.bananemdnsa.historystages.research.TierMatcher;
import net.bananemdnsa.historystages.research.TierMode;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.util.ScrollVariants;
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
                    // EXTERNAL- and AUTO-mode scrolls are allowed in the slot but
                    // research is paused in tick() and the GUI shows a "not researchable"
                    // message instead of progress.
                    loadProgressFromItem(stack);
                } else {
                    ResearchPedestalBlockEntity.this.ownerUUID = null;
                    ResearchPedestalBlockEntity.this.resetResearchState();
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

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 0 && isScrollLocked()) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    };

    /** True when the scroll cannot be removed: a research is actively running. Pausing
     *  releases it, which is how a scroll is handed to the next player. */
    public boolean isScrollLocked() {
        return this.running;
    }

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

        CompoundTag scrollTag = scroll.getOrCreateTag();
        CompoundTag deposited = scroll.getOrCreateTagElement("DepositedDependencies");
        boolean changed = false;

        // Use locked cost reduction if present, else preview using the current pedestal's booster.
        boolean alreadyLocked = scrollTag.contains("LockedCostReduction");
        double costReduction = alreadyLocked
                ? getLockedCostReduction(scrollTag)
                : getActiveBooster().costReduction();

        for (int i = 0; i < entry.getDependencies().size(); i++) {
            net.bananemdnsa.historystages.data.DependencyGroup group = entry.getDependencies().get(i);
            for (net.bananemdnsa.historystages.data.dependency.DependencyItem reqItem : group.getItems()) {
                ResourceLocation reqRl = ResourceLocation.tryParse(reqItem.getId());
                if (reqRl != null && reqRl.equals(depositRl)
                        && (!reqItem.hasNbt() || NbtMatcher.matches(depositStack, reqItem.getNbt()))) {
                    String key = "Group_" + i + "_Item_" + reqRl.toString();
                    int current = deposited.getInt(key);
                    int effectiveRequired = BoosterUtil.effectiveCount(reqItem.getCount(), costReduction);
                    int needed = effectiveRequired - current;

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
            // First deposit ever for this scroll: lock the cost reduction value.
            if (!alreadyLocked) {
                scrollTag.putDouble("LockedCostReduction", costReduction);
            }
            scroll.setTag(scrollTag); // Trigger sync
            setChanged();

            // Push update to watching players immediately
            if (entry != null && level != null && !level.isClientSide) {
                // Same fallback as tick(): before a start there is no owner, and the player
                // depositing still needs their checklist refreshed.
                UUID checkUUID = isCurrentScrollIndividual() && this.ownerUUID != null
                        ? this.ownerUUID
                        : this.lastInteractingPlayer;
                if (checkUUID != null) {
                    var player = level.getServer().getPlayerList().getPlayer(checkUUID);
                    if (player != null) {
                        double tickCost = scrollTag.contains("LockedCostReduction")
                                ? scrollTag.getDouble("LockedCostReduction") : 0.0;
                        var result = net.bananemdnsa.historystages.data.dependency.DependencyChecker.checkAll(entry,
                                player, level, scroll.getTag().getCompound("DepositedDependencies"),
                                tickCost);
                        // To the one player the result was computed for, not the chunk: the
                        // status is personal (their inventory, their stages, their deposits), so
                        // a broadcast would have every client nearby cache someone else's answer
                        // under this stage id.
                        net.bananemdnsa.historystages.network.PacketHandler.INSTANCE.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                                new net.bananemdnsa.historystages.network.SyncDependencyStatusPacket(
                                        stageId, isCurrentScrollIndividual(), result));
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
    private boolean running = false;
    private double progressAccumulator = 0.0;
    private int currentSpeedPercent = 0;
    private boolean tierMismatch = false;
    private int requiredTier = 1;
    private TierMode requiredTierMode = TierMode.MIN;
    private int lastComparatorOutput = -1;

    /** Read the cost reduction locked into the scroll on first deposit, or 0.0 if not yet locked. */
    public static double getLockedCostReduction(ItemStack scroll) {
        if (scroll.isEmpty() || !scroll.hasTag()) return 0.0;
        return getLockedCostReduction(scroll.getTag());
    }

    /** Read the locked cost reduction from an already-copied scroll tag. */
    public static double getLockedCostReduction(CompoundTag scrollTag) {
        if (scrollTag == null || !scrollTag.contains("LockedCostReduction")) return 0.0;
        return scrollTag.getDouble("LockedCostReduction");
    }

    /**
     * Booster effect for this pedestal. Scans all positions under the pedestal
     * (1 for single-block tiers, 2 for multiblock Tier 3/4), drops any booster
     * whose tier gating rejects this pedestal, and returns the strongest of
     * what remains (speed first, then cost; foot wins on a total tie).
     */
    public ResearchBooster getActiveBooster() {
        if (level == null) return ResearchBooster.NONE;
        BlockState selfState = level.getBlockState(worldPosition);
        int pedestalTier = tierOf(selfState);

        ResearchBooster best = candidate(level.getBlockState(worldPosition.below()), pedestalTier);

        if (selfState.getBlock() instanceof MultiBlockResearchPedestalBlock) {
            Direction facing = selfState.getValue(MultiBlockResearchPedestalBlock.FACING);
            ResearchBooster head = candidate(level.getBlockState(worldPosition.relative(facing).below()),
                    pedestalTier);
            // Foot wins on tie: only replace when strictly stronger.
            if (head != ResearchBooster.NONE
                    && ResearchBooster.BY_STRENGTH.compare(head, best) > 0) {
                best = head;
            }
        }
        return best;
    }

    private static ResearchBooster candidate(BlockState belowState, int pedestalTier) {
        ResearchBooster b = ResearchBoosterRegistry.forBlockState(belowState)
                .orElse(ResearchBooster.NONE);
        if (b == ResearchBooster.NONE) return ResearchBooster.NONE;
        if (!TierMatcher.matches(pedestalTier, b.minTier(), b.tierMode())) {
            return ResearchBooster.NONE;
        }
        return b;
    }

    private static int tierOf(BlockState state) {
        return state.getBlock() instanceof TieredPedestal tp ? tp.getTier() : 1;
    }

    /** Single point of truth for clearing in-progress research state on this pedestal. */
    private void resetResearchState() {
        this.progress = 0;
        this.progressAccumulator = 0.0;
        this.tierMismatch = false;
        this.requiredTier = 1;
        this.requiredTierMode = TierMode.MIN;
        this.running = false;
    }

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
                    case 6 -> ResearchPedestalBlockEntity.this.currentSpeedPercent;
                    case 7 -> ResearchPedestalBlockEntity.this.tierMismatch ? 1 : 0;
                    case 8 -> ResearchPedestalBlockEntity.this.requiredTier;
                    case 9 -> ResearchPedestalBlockEntity.this.requiredTierMode.ordinal();
                    case 10 -> ResearchPedestalBlockEntity.this.running ? 1 : 0;
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
                    case 6 -> ResearchPedestalBlockEntity.this.currentSpeedPercent = pValue;
                    case 7 -> ResearchPedestalBlockEntity.this.tierMismatch = pValue == 1;
                    case 8 -> ResearchPedestalBlockEntity.this.requiredTier = pValue;
                    case 9 -> ResearchPedestalBlockEntity.this.requiredTierMode =
                            pValue == TierMode.EXACT.ordinal() ? TierMode.EXACT : TierMode.MIN;
                    case 10 -> ResearchPedestalBlockEntity.this.running = pValue == 1;
                }
            }

            @Override
            public int getCount() {
                return 11;
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

    /** Drop both inventory slots at the given position. Saves current research progress to the
     *  scroll first so the dropped item reflects the latest tick. */
    public void dropContents(Level dropLevel, BlockPos pos) {
        ItemStack scroll = itemHandler.getStackInSlot(0);
        if (!scroll.isEmpty()) {
            CompoundTag tag = scroll.hasTag() ? scroll.getTag().copy() : new CompoundTag();
            if (tag.contains("StageResearch")) {
                tag.putInt("ResearchProgress", this.progress);
                tag.putInt("MaxProgress", getMaxProgressForCurrentStage());
                scroll.setTag(tag);
            }
        }
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(dropLevel,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        if (level != null) {
            return level.getBlockState(worldPosition).getBlock().getName();
        }
        return Component.translatable("block.historystages.research_pedestal");
    }

    public ItemStack getScrollStack() {
        return this.itemHandler.getStackInSlot(0);
    }

    /**
     * True for the scroll types the pedestal may research. An open scroll carries the same
     * {@code StageResearch} tag as a fresh one, so the tag alone is not enough: without this
     * check an {@code open} completion would leave a scroll the next player could research
     * again, turning the mode into an endless refill.
     */
    private static boolean isResearchable(ItemStack stack) {
        return stack.is(ModItems.RESEARCH_SCROLL.get()) || stack.is(ModItems.CREATIVE_SCROLL.get());
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

        // Refresh active speed multiplier from the booster below (synced to client via data slot 6).
        ResearchBooster activeBooster = entity.getActiveBooster();
        entity.currentSpeedPercent = BoosterUtil.percent(activeBooster.speedReduction());

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

        boolean hasValidBook = isResearchable(stack) && stack.hasTag() && stack.getTag().contains("StageResearch");
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
                StageEntry stageEntryForTier = isCreative ? null
                        : (isIndividual
                            ? StageManager.getIndividualStages().get(stageId)
                            : StageManager.getStages().get(stageId));

                // Stage tier gating: pause research if the pedestal tier doesn't satisfy
                // the stage's min_pedestal_tier + pedestal_tier_mode.
                int pedestalTier = tierOf(state);
                int needTier = stageEntryForTier != null ? stageEntryForTier.getMinPedestalTier() : 1;
                TierMode needMode = stageEntryForTier != null
                        ? stageEntryForTier.getPedestalTierMode() : TierMode.MIN;
                entity.requiredTier = needTier;
                entity.requiredTierMode = needMode;
                entity.tierMismatch = !TierMatcher.matches(pedestalTier, needTier, needMode);

                if (!isCreative) {
                    StageEntry stageEntry = stageEntryForTier;

                    // Only DEFAULT-mode stages can be researched at the Pedestal.
                    // EXTERNAL and AUTO scrolls are allowed in the slot but research
                    // is paused here so progress never accumulates; the GUI shows a
                    // "not researchable" message instead of the normal progress UI.
                    if (stageEntry != null && stageEntry.getMode() != StageMode.DEFAULT) {
                        stageEntry = null;
                    }

                    if (stageEntry != null) {
                        if (stageEntry.hasDependencies()) {
                            // Find the researching player for dependency checks
                            net.minecraft.server.level.ServerPlayer researchPlayer = null;
                            // Before anyone has started, an individual scroll has no owner yet,
                            // so fall back to whoever is at the pedestal. Checking against a
                            // null owner would report "requirements not met", which disables the
                            // start button — and only starting can set the owner.
                            UUID checkUUID = isIndividual && entity.ownerUUID != null
                                    ? entity.ownerUUID
                                    : entity.lastInteractingPlayer;
                            if (checkUUID != null && level.getServer() != null) {
                                researchPlayer = level.getServer().getPlayerList().getPlayer(checkUUID);
                            }

                            if (researchPlayer != null) {
                                CompoundTag depositedTag = stack.hasTag()
                                        && stack.getTag().contains("DepositedDependencies")
                                                ? stack.getTag().getCompound("DepositedDependencies")
                                                : null;
                                double tickCost = stack.hasTag()
                                        && stack.getTag().contains("LockedCostReduction")
                                                ? stack.getTag().getDouble("LockedCostReduction")
                                                : 0.0;
                                DependencyResult result = DependencyChecker.checkAll(stageEntry, researchPlayer, level,
                                        depositedTag, tickCost);
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

                // Tier mismatch acts like an unmet dependency: pause progress.
                if (entity.tierMismatch) {
                    metTotal = false;
                }

                // What the screen shows and what the start button tests: the requirements
                // themselves, deliberately independent of whether anyone pressed start.
                // Folding `running` into this would report "requirements not met" for every
                // paused pedestal and leave the start button permanently disabled.
                entity.dependenciesMet = metTotal;

                // Research only advances after a player pressed start. This is also what
                // guarantees an owner exists: an ownerless individual scroll would otherwise
                // research to completion, unlock nothing, and — under REPLACE — refill itself
                // forever.
                if (metTotal && entity.running) {
                    isResearching = true;
                    if (entity.progress < maxProgress) {
                        entity.progressAccumulator += BoosterUtil.speedMultiplier(activeBooster.speedReduction());
                        int wholeTicks = (int) entity.progressAccumulator;
                        if (wholeTicks > 0) {
                            entity.progress = Math.min(maxProgress, entity.progress + wholeTicks);
                            entity.progressAccumulator -= wholeTicks;
                        }
                        if (entity.progress % 10 == 0 || entity.progress >= maxProgress) {
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
                entity.resetResearchState();
            }
        } else {
            entity.resetResearchState();
            entity.finishDelay = 0;
        }

        if (state.getValue(ResearchPedestalBlock.WORKING) != hasValidBook
                || state.getValue(ResearchPedestalBlock.LIT) != isResearching) {
            level.setBlock(pos, state.setValue(ResearchPedestalBlock.WORKING, hasValidBook)
                    .setValue(ResearchPedestalBlock.LIT, isResearching), 3);
            // For multiblock pedestals, mirror WORKING/LIT onto the head part so its shape
            // and lighting stay in sync with the foot.
            if (state.getBlock() instanceof net.bananemdnsa.historystages.block.MultiBlockResearchPedestalBlock) {
                net.minecraft.core.Direction facing = state.getValue(net.bananemdnsa.historystages.block.MultiBlockResearchPedestalBlock.FACING);
                BlockPos headPos = pos.relative(facing);
                BlockState headState = level.getBlockState(headPos);
                if (headState.is(state.getBlock())) {
                    level.setBlock(headPos, headState
                            .setValue(ResearchPedestalBlock.WORKING, hasValidBook)
                            .setValue(ResearchPedestalBlock.LIT, isResearching), 3);
                }
            }
        }
        setChanged(level, pos, state);
        entity.updateComparatorIfChanged(level, pos, state);
    }

    private void finishResearch(ItemStack stack) {
        String stageId = (stack.hasTag() && stack.getTag().contains("StageResearch"))
                ? stack.getTag().getString("StageResearch")
                : null;

        if (!level.isClientSide && stageId != null) {
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

        // Station zurücksetzen und Buch entsprechend dem Completion-Modus behandeln
        this.resetResearchState();
        this.finishDelay = 0;
        applyCompletion(stack, stageId);
        setChanged();
    }

    /**
     * Dispose of the finished scroll according to the stage's completion mode. The creative
     * scroll is always consumed: refilling it would let the pedestal feed itself forever.
     */
    private void applyCompletion(ItemStack finished, String stageId) {
        finished.shrink(1);

        // The finished research is over either way, so the pedestal must forget who owned it
        // before anything else happens here. Shrinking the stack in place fires no slot
        // change, so nothing else would clear it — and a leftover owner would be handed the
        // next player's research.
        this.ownerUUID = null;
        this.lastInteractingPlayer = null;

        if (stageId == null || ModItems.CREATIVE_STAGE_ID.equals(stageId)) return;

        StageEntry entry = StageManager.isIndividualStage(stageId)
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        ScrollCompletion mode = ScrollCompletion.resolve(
                entry != null ? entry.getScrollCompletion() : null,
                Config.COMMON.defaultScrollCompletion.get());

        ItemStack replacement = switch (mode) {
            case CONSUME -> ItemStack.EMPTY;
            case REPLACE -> ScrollVariants.createScroll(stageId);
            case OPEN -> ScrollVariants.createOpenScroll(stageId);
        };
        if (replacement.isEmpty()) return;

        this.itemHandler.setStackInSlot(0, replacement);
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
                String iconId = (stageEntry != null && stageEntry.getIcon() != null) ? stageEntry.getIcon() : "";
                PacketHandler
                        .sendToastToAll(new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(stagename, iconId));
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
                        String iconId = (stageEntry != null && stageEntry.getIcon() != null) ? stageEntry.getIcon() : "";
                        PacketHandler.INSTANCE.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> ownerPlayer),
                                new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(stagename, iconId));
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

    /**
     * Comparator output (0-15) based on research progress.
     * 0 = no scroll or no progress, 15 = research complete.
     */
    public int getComparatorOutput() {
        if (this.itemHandler.getStackInSlot(0).isEmpty()) return 0;
        if (this.progress <= 0) return 0;
        int max = getMaxProgressForCurrentStage();
        if (max <= 0) return 0;
        return Math.min(15, 1 + (14 * this.progress) / max);
    }

    /** Push a comparator-output change to redstone neighbors on this pedestal (and the head if multiblock). */
    private void updateComparatorIfChanged(Level level, BlockPos pos, BlockState state) {
        int current = getComparatorOutput();
        if (current == lastComparatorOutput) return;
        lastComparatorOutput = current;
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        if (state.getBlock() instanceof MultiBlockResearchPedestalBlock) {
            Direction facing = state.getValue(MultiBlockResearchPedestalBlock.FACING);
            BlockPos headPos = pos.relative(facing);
            BlockState headState = level.getBlockState(headPos);
            if (headState.is(state.getBlock())) {
                level.updateNeighbourForOutputSignal(headPos, headState.getBlock());
            }
        }
    }

    public boolean isCurrentScrollIndividual() {
        ItemStack stack = this.itemHandler.getStackInSlot(0);
        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            return StageManager.isIndividualStage(stack.getTag().getString("StageResearch"));
        }
        return false;
    }

    /**
     * Try to start research for {@code player}. Returns false and changes nothing when the
     * pedestal is not in a state that can research. The first player to start becomes the
     * owner; a research already running is left alone.
     */
    public boolean tryStart(net.minecraft.server.level.ServerPlayer player) {
        if (level == null || level.isClientSide) return false;
        if (this.running) return false;

        ItemStack scroll = getScrollStack();
        if (!isResearchable(scroll)) return false;
        if (!scroll.hasTag() || !scroll.getTag().contains("StageResearch")) return false;
        CompoundTag tag = scroll.getOrCreateTag();
        String stageId = tag.getString("StageResearch");

        if (!ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
            boolean individual = StageManager.isIndividualStage(stageId);
            StageEntry entry = individual
                    ? StageManager.getIndividualStages().get(stageId)
                    : StageManager.getStages().get(stageId);
            // Only DEFAULT stages are researchable here; AUTO/EXTERNAL/TEMPORARY are not.
            if (entry == null || entry.getMode() != StageMode.DEFAULT) return false;

            // The same two conditions the screen greys the button out for. Both are kept
            // current by tick(). Without them a start would latch `running` on and lock the
            // scroll into a pedestal where progress can never advance.
            if (this.tierMismatch || !this.dependenciesMet) return false;

            if (individual) {
                // Anyone may press start, but the research belongs to whoever started it
                // first: resuming a paused scroll does not take it over.
                // Only the scroll decides. A pedestal-level fallback would hand the next
                // player's fresh scroll to whoever researched here last.
                UUID owner = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
                boolean claiming = owner == null;
                if (claiming) owner = player.getUUID();
                // Refuse an unlock the owner already has, so a scroll is not burned for nothing.
                if (IndividualStageData.hasStageCached(owner, stageId)) return false;
                this.ownerUUID = owner;
                if (claiming) {
                    // The owner is written onto the scroll, not just onto the pedestal: it has
                    // to survive being carried away, and the deposit gate, the item tooltip and
                    // the screen all read it back from there.
                    tag.putUUID("OwnerUUID", owner);
                    tag.putString("OwnerName", player.getName().getString());
                    scroll.setTag(tag);
                }
            } else {
                if (StageData.get(level).getUnlockedStages().contains(stageId)) return false;
                this.ownerUUID = null;
            }
        }

        this.lastInteractingPlayer = player.getUUID();
        this.running = true;
        setChanged();
        return true;
    }

    /** Stop advancing progress. Owner and progress are kept; the scroll becomes removable. */
    public void pause() {
        if (level == null || level.isClientSide) return;
        this.running = false;
        // tick() only writes progress onto the scroll every 10 ticks, and pausing is exactly
        // when the scroll may be carried off — flush it so nothing is lost on the way out.
        ItemStack scroll = getScrollStack();
        if (!scroll.isEmpty() && scroll.hasTag() && scroll.getTag().contains("StageResearch")) {
            CompoundTag tag = scroll.getOrCreateTag();
            tag.putInt("ResearchProgress", this.progress);
            tag.putInt("MaxProgress", getMaxProgressForCurrentStage());
        }
        setChanged();
    }

    public boolean isRunning() {
        return this.running;
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
        nbt.putBoolean("research.running", running);
        if (ownerUUID != null) {
            nbt.putUUID("research.ownerUUID", ownerUUID);
        }
        // A global research resolves its dependency checks against this player, so losing it
        // over a restart would silently freeze a running research until someone reopens the GUI.
        if (lastInteractingPlayer != null) {
            nbt.putUUID("research.lastPlayer", lastInteractingPlayer);
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
        running = nbt.getBoolean("research.running");
        if (nbt.hasUUID("research.ownerUUID")) {
            ownerUUID = nbt.getUUID("research.ownerUUID");
        }
        if (nbt.hasUUID("research.lastPlayer")) {
            lastInteractingPlayer = nbt.getUUID("research.lastPlayer");
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
        CompoundTag scrollTag = scroll.getTag();
        CompoundTag depositedData = scrollTag.getCompound("DepositedDependencies");
        double costReduction = scrollTag.contains("LockedCostReduction")
                ? getLockedCostReduction(scrollTag)
                : getActiveBooster().costReduction();

        for (int i = 0; i < entry.getDependencies().size(); i++) {
            net.bananemdnsa.historystages.data.DependencyGroup group = entry.getDependencies().get(i);
            for (net.bananemdnsa.historystages.data.dependency.DependencyItem item : group.getItems()) {
                if (item.getId().equals(itemId)
                        && (!item.hasNbt() || NbtMatcher.matches(depositStack, item.getNbt()))) {
                    String key = "Group_" + i + "_Item_" + item.getId();
                    int count = depositedData.getInt(key);
                    int effectiveRequired = BoosterUtil.effectiveCount(item.getCount(), costReduction);
                    if (count < effectiveRequired)
                        return true;
                }
            }
        }
        return false;
    }
}