package net.bananemdnsa.historystages.screen;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ResearchPedestalMenu extends AbstractContainerMenu {
    private final ResearchPedestalBlockEntity blockEntity;
    private final Level level;
    public final ContainerData data;

    // Client-Konstruktor
    public ResearchPedestalMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        // WICHTIG: Hier muss eine 6 stehen, damit Platz für alle Daten ist!
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(6));
    }

    // Server-Konstruktor
    public ResearchPedestalMenu(int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.RESEARCH_MENU.get(), pContainerId);
        checkContainerSize(inv, 1);
        this.blockEntity = ((ResearchPedestalBlockEntity) entity);
        this.level = inv.player.level();
        this.data = data;

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        // We MUST ensure slots are ALWAYS added to have a consistent slot count (38
        // total).
        // On the client, the BE cap might be empty during early init.
        IItemHandler handler = this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElse(new ItemStackHandler(2));

        // Internal Slot 0: Scroll
        this.addSlot(new SlotItemHandler(handler, 0, 26, 35));

        // Internal Slot 1: Deposit (Inside the dependency panel)
        this.addSlot(new SlotItemHandler(handler, 1, 246, 142) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                if (ResearchPedestalMenu.this.blockEntity == null)
                    return false;

                // Get current stage info
                ItemStack scroll = ResearchPedestalMenu.this.blockEntity.getScrollStack();
                if (scroll.isEmpty() || !scroll.hasTag() || !scroll.getTag().contains("StageResearch"))
                    return false;
                String stageId = scroll.getTag().getString("StageResearch");

                // Block if already unlocked
                if (ResearchPedestalMenu.this.blockEntity.isCurrentScrollIndividual()) {
                    UUID owner = scroll.getTag().contains("OwnerUUID") ? scroll.getTag().getUUID("OwnerUUID") : null;
                    if (owner != null
                            && net.bananemdnsa.historystages.util.IndividualStageData.hasStageCached(owner, stageId)) {
                        return false;
                    }
                } else {
                    if (net.bananemdnsa.historystages.util.StageData.SERVER_CACHE.contains(stageId)) {
                        return false;
                    }
                }

                return super.mayPlace(stack);
            }

            @Override
            public boolean isActive() {
                return ResearchPedestalMenu.this.blockEntity != null
                        && ResearchPedestalMenu.this.blockEntity.hasScrollWithDependencies();
            }
        });

        // Synchronisiert die Daten (Progress, Max, Delay) zwischen Server und Client
        addDataSlots(data);
    }

    public int getScaledProgress() {
        int progress = this.data.get(0);
        int maxProgress = this.data.get(1);
        int progressArrowSize = 61;

        return maxProgress != 0 && progress != 0 ? progress * progressArrowSize / maxProgress : 0;
    }

    public ResearchPedestalBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public net.minecraft.core.BlockPos getBlockPos() {
        return this.blockEntity.getBlockPos();
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), pPlayer,
                ModBlocks.RESEARCH_PEDESTAL.get());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();

            // If item comes from player inventory/hotbar (0-35)
            if (index < 36) {
                // First try to put in scroll slot if valid
                if (this.moveItemStackTo(stack, 36, 37, false)) {
                    // Success
                } else if (this.moveItemStackTo(stack, 37, 38, false)) {
                    // Try to put in deposit slot
                } else {
                    return ItemStack.EMPTY;
                }
            }
            // If item comes from pedestal slots (36-37)
            else {
                if (!this.moveItemStackTo(stack, 0, 36, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return copy;
        }
        return ItemStack.EMPTY;
    }

    public boolean isCrafting() {
        // Wir zeigen den Balken auch an, wenn wir im finishDelay (data Index 2) sind!
        return data.get(0) > 0 || data.get(2) > 0;
    }

    public boolean isIndividualMode() {
        return data.get(3) == 1;
    }

    public boolean areDependenciesMet() {
        return data.get(4) == 1;
    }
}