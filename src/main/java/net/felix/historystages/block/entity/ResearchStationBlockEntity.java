package net.felix.historystages.block.entity;

import net.felix.historystages.Config;
import net.felix.historystages.block.ResearchStationBlock;
import net.felix.historystages.init.ModBlockEntities;
import net.felix.historystages.init.ModItems;
import net.felix.historystages.screen.ResearchStationMenu;
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

public class ResearchStationBlockEntity extends BlockEntity implements MenuProvider {

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            ItemStack stack = getStackInSlot(slot);
            if (!stack.isEmpty()) {
                loadProgressFromItem(stack);
            }
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.is(ModItems.RESEARCH_BOOK.get());
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    protected final ContainerData data;
    private int progress = 0;
    private int finishDelay = 0;

    public ResearchStationBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.RESEARCH_STATION_BE.get(), pPos, pBlockState);
        this.data = new ContainerData() {
            @Override
            public int get(int pIndex) {
                return switch (pIndex) {
                    case 0 -> ResearchStationBlockEntity.this.progress;
                    case 1 -> Config.COMMON.researchTimeInSeconds.get() * 20;
                    case 2 -> ResearchStationBlockEntity.this.finishDelay;
                    default -> 0;
                };
            }

            @Override
            public void set(int pIndex, int pValue) {
                switch (pIndex) {
                    case 0 -> ResearchStationBlockEntity.this.progress = pValue;
                    case 2 -> ResearchStationBlockEntity.this.finishDelay = pValue;
                }
            }

            @Override
            public int getCount() {
                return 3;
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
        return Component.translatable("block.historystages.research_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new ResearchStationMenu(pContainerId, pPlayerInventory, this, this.data);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchStationBlockEntity entity) {
        if (level.isClientSide) return;

        ItemStack stack = entity.itemHandler.getStackInSlot(0);
        int maxProgress = Config.COMMON.researchTimeInSeconds.get() * 20;

        boolean hasValidBook = !stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch");
        boolean isResearching = false;

        if (hasValidBook) {
            String stageId = stack.getTag().getString("StageResearch");
            net.felix.historystages.util.StageData data = net.felix.historystages.util.StageData.get(level);
            boolean alreadyUnlocked = data.getUnlockedStages().contains(stageId);

            if (!alreadyUnlocked) {
                // Hier läuft die Forschung -> LIT soll true sein
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
            } else {
                entity.progress = 0;
            }
        } else {
            entity.progress = 0;
            entity.finishDelay = 0;
        }

        // State Update: WORKING wenn Buch da, LIT wenn Forschung läuft
        if (state.getValue(ResearchStationBlock.WORKING) != hasValidBook || state.getValue(ResearchStationBlock.LIT) != isResearching) {
            level.setBlock(pos, state.setValue(ResearchStationBlock.WORKING, hasValidBook).setValue(ResearchStationBlock.LIT, isResearching), 3);
        }

        setChanged(level, pos, state);
    }

    private void finishResearch(ItemStack stack) {
        if (!level.isClientSide && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            String stageId = stack.getTag().getString("StageResearch");
            var stageEntry = net.felix.historystages.data.StageManager.getStages().get(stageId);
            net.felix.historystages.util.StageData data = net.felix.historystages.util.StageData.get(level);

            if (!data.getUnlockedStages().contains(stageId)) {
                data.addStage(stageId);
                net.felix.historystages.util.StageData.SERVER_CACHE.clear();
                net.felix.historystages.util.StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
                net.felix.historystages.network.PacketHandler.sendToAll(
                        new net.felix.historystages.network.SyncStagesPacket(new java.util.ArrayList<>(data.getUnlockedStages()))
                );

                String stagename = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
                String configChat = Config.COMMON.unlockMessageFormat.get();
                String finalChat = configChat.replace("{stage}", stagename).replace("&", "§");

                level.getServer().getPlayerList().getPlayers().forEach(player -> {
                    if (Config.COMMON.broadcastChat.get()) player.sendSystemMessage(Component.literal(finalChat));
                    if (Config.COMMON.useSounds.get()) player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
                });
            }
        }
        this.progress = 0;
        this.finishDelay = 0;
        stack.shrink(1);
        setChanged();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return lazyItemHandler.cast();
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
        super.saveAdditional(nbt);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemHandler.deserializeNBT(nbt.getCompound("inventory"));
        progress = nbt.getInt("research.progress");
        finishDelay = nbt.getInt("research.finishDelay");
    }
}