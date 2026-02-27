package net.bananemdnsa.historystages.block.entity;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.block.ResearchPedestialBlock;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.screen.ResearchPedestialMenu;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
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

public class ResearchPedestialBlockEntity extends BlockEntity implements MenuProvider {

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
            return stack.is(ModItems.RESEARCH_SCROLL.get());
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    protected final ContainerData data;
    private int progress = 0;
    private int finishDelay = 0;
    private int syncTickDelay = -1; // Neu: Delay-Timer

    public ResearchPedestialBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.RESEARCH_PEDESTIAL_BE.get(), pPos, pBlockState);
        this.data = new ContainerData() {
            @Override
            public int get(int pIndex) {
                return switch (pIndex) {
                    case 0 -> ResearchPedestialBlockEntity.this.progress;
                    case 1 -> Config.COMMON.researchTimeInSeconds.get() * 20;
                    case 2 -> ResearchPedestialBlockEntity.this.finishDelay;
                    default -> 0;
                };
            }

            @Override
            public void set(int pIndex, int pValue) {
                switch (pIndex) {
                    case 0 -> ResearchPedestialBlockEntity.this.progress = pValue;
                    case 2 -> ResearchPedestialBlockEntity.this.finishDelay = pValue;
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
        return Component.translatable("block.historystages.research_pedestial");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new ResearchPedestialMenu(pContainerId, pPlayerInventory, this, this.data);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchPedestialBlockEntity entity) {
        if (level.isClientSide) return;

        // Neu: Warte kurz, bevor das Sync-Paket gesendet wird (Timing-Fix)
        if (entity.syncTickDelay > 0) {
            entity.syncTickDelay--;
        } else if (entity.syncTickDelay == 0) {
            entity.performGlobalSync();
            entity.syncTickDelay = -1;
        }

        ItemStack stack = entity.itemHandler.getStackInSlot(0);
        int maxProgress = Config.COMMON.researchTimeInSeconds.get() * 20;

        boolean hasValidBook = !stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch");
        boolean isResearching = false;

        if (hasValidBook) {
            String stageId = stack.getTag().getString("StageResearch");
            StageData data = StageData.get(level);
            boolean alreadyUnlocked = data.getUnlockedStages().contains(stageId);

            if (!alreadyUnlocked) {
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

        if (state.getValue(ResearchPedestialBlock.WORKING) != hasValidBook || state.getValue(ResearchPedestialBlock.LIT) != isResearching) {
            level.setBlock(pos, state.setValue(ResearchPedestialBlock.WORKING, hasValidBook).setValue(ResearchPedestialBlock.LIT, isResearching), 3);
        }
        setChanged(level, pos, state);
    }

    private void finishResearch(ItemStack stack) {
        if (!level.isClientSide && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            String stageId = stack.getTag().getString("StageResearch");
            var stageEntry = net.bananemdnsa.historystages.data.StageManager.getStages().get(stageId);
            net.bananemdnsa.historystages.util.StageData data = net.bananemdnsa.historystages.util.StageData.get(level);

            if (!data.getUnlockedStages().contains(stageId)) {
                // 1. In der Welt-Datei speichern
                data.addStage(stageId);
                data.setDirty();

                // 2. DEN BEFEHL LEISE AUSFÜHREN (Erzwingt JEI Hard-Reload auf allen Clients)
                if (level.getServer() != null) {
                    level.getServer().getCommands().performPrefixedCommand(
                            level.getServer().createCommandSourceStack().withSuppressedOutput(),
                            "history reload"
                    );
                }

                // 3. Deine individuellen Nachrichten & Sounds (Old Logic)
                String stagename = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
                String configChat = Config.COMMON.unlockMessageFormat.get();
                String finalChat = configChat.replace("{stage}", stagename).replace("&", "§");

                level.getServer().getPlayerList().getPlayers().forEach(player -> {
                    // Chat Nachricht senden
                    if (Config.COMMON.broadcastChat.get()) {
                        player.sendSystemMessage(Component.literal(finalChat));
                    }
                    // Sound abspielen
                    if (Config.COMMON.useSounds.get()) {
                        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
                    }
                });
            }
        }

        // 4. Station zurücksetzen und Buch verbrauchen
        this.progress = 0;
        this.finishDelay = 0;
        stack.shrink(1);
        setChanged();
    }

    private void performGlobalSync() {
        StageData data = StageData.get(this.level);
        StageData.SERVER_CACHE.clear();
        StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(StageData.SERVER_CACHE)));
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