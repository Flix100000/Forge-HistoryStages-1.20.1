package net.bananemdnsa.historystages.block.entity;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.block.ResearchPedestalBlock;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.bananemdnsa.historystages.util.StageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ResearchPedestalBlockEntity extends BlockEntity implements MenuProvider {

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

    protected final ContainerData data;
    private int progress = 0;
    private int finishDelay = 0;
    private int syncTickDelay = -1; // Neu: Delay-Timer

    public ResearchPedestalBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.RESEARCH_PEDESTAL_BE.get(), pPos, pBlockState);
        this.data = new ContainerData() {
            @Override
            public int get(int pIndex) {
                return switch (pIndex) {
                    case 0 -> ResearchPedestalBlockEntity.this.progress;
                    case 1 -> ResearchPedestalBlockEntity.this.getMaxProgressForCurrentStage();
                    case 2 -> ResearchPedestalBlockEntity.this.finishDelay;
                    default -> 0;
                };
            }

            @Override
            public void set(int pIndex, int pValue) {
                switch (pIndex) {
                    case 0 -> ResearchPedestalBlockEntity.this.progress = pValue;
                    case 2 -> ResearchPedestalBlockEntity.this.finishDelay = pValue;
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
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
        return new ResearchPedestalMenu(pContainerId, pPlayerInventory, this, this.data);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResearchPedestalBlockEntity entity) {
        if (level.isClientSide) return;

        // Neu: Warte kurz, bevor das Sync-Paket gesendet wird (Timing-Fix)
        if (entity.syncTickDelay > 0) {
            entity.syncTickDelay--;
        } else if (entity.syncTickDelay == 0) {
            entity.performGlobalSync();
            entity.syncTickDelay = -1;
        }

        ItemStack stack = entity.itemHandler.getStackInSlot(0);
        int maxProgress = entity.getMaxProgressForCurrentStage();

        CompoundTag stackTag = !stack.isEmpty() ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag() : new CompoundTag();
        boolean hasValidBook = !stack.isEmpty() && stackTag.contains("StageResearch");
        boolean isResearching = false;

        if (hasValidBook) {
            String stageId = stackTag.getString("StageResearch");
            StageData data = StageData.get(level);
            boolean alreadyUnlocked = data.getUnlockedStages().contains(stageId);

            if (!alreadyUnlocked) {
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
            } else {
                entity.progress = 0;
            }
        } else {
            entity.progress = 0;
            entity.finishDelay = 0;
        }

        if (state.getValue(ResearchPedestalBlock.WORKING) != hasValidBook || state.getValue(ResearchPedestalBlock.LIT) != isResearching) {
            level.setBlock(pos, state.setValue(ResearchPedestalBlock.WORKING, hasValidBook).setValue(ResearchPedestalBlock.LIT, isResearching), 3);
        }
        setChanged(level, pos, state);
    }

    private void finishResearch(ItemStack stack) {
        CompoundTag stackTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!level.isClientSide && stackTag.contains("StageResearch")) {
            String stageId = stackTag.getString("StageResearch");
            var stageEntry = net.bananemdnsa.historystages.data.StageManager.getStages().get(stageId);
            net.bananemdnsa.historystages.util.StageData data = net.bananemdnsa.historystages.util.StageData.get(level);

            if (!data.getUnlockedStages().contains(stageId)) {
                // 1. In der Welt-Datei speichern
                data.addStage(stageId);
                data.setDirty();

                // Fire custom NeoForge event for KubeJS/CraftTweaker
                String eventDisplayName = (stageEntry != null) ? stageEntry.getDisplayName() : stageId;
                NeoForge.EVENT_BUS.post(
                        new net.bananemdnsa.historystages.events.StageEvent.Unlocked(stageId, eventDisplayName));

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
                        player.sendSystemMessage(
                                Component.literal("[HistoryStages] ")
                                        .withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(finalChat))
                        );
                    }
                    // Sound abspielen
                    if (Config.COMMON.useSounds.get()) {
                        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.75F, 1.0F);
                    }
                });

                // Toast notification
                if (Config.COMMON.useToasts.get()) {
                    PacketHandler.sendToastToAll(new net.bananemdnsa.historystages.network.StageUnlockedToastPacket(stagename));
                }
            }
        }

        // 4. Station zurücksetzen und Buch verbrauchen
        this.progress = 0;
        this.finishDelay = 0;
        stack.shrink(1);
        setChanged();
    }

    private int getMaxProgressForCurrentStage() {
        ItemStack stack = this.itemHandler.getStackInSlot(0);
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                return net.bananemdnsa.historystages.data.StageManager.getResearchTimeInTicks(stageId);
            }
        }
        return Config.COMMON.researchTimeInSeconds.get() * 20;
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
        super.saveAdditional(nbt, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        itemHandler.deserializeNBT(registries, nbt.getCompound("inventory"));
        progress = nbt.getInt("research.progress");
        finishDelay = nbt.getInt("research.finishDelay");
    }
}
