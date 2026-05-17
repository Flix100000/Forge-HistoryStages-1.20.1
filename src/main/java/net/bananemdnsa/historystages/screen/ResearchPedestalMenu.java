package net.bananemdnsa.historystages.screen;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ResearchPedestalMenu extends AbstractContainerMenu {
    private final ResearchPedestalBlockEntity blockEntity;
    private final Level level;
    public final ContainerData data;

    public ResearchPedestalMenu(int containerId, Inventory inventory, BlockPos blockPos) {
        this(containerId, inventory, (ResearchPedestalBlockEntity) inventory.player.level().getBlockEntity(blockPos), new SimpleContainerData(6));
    }

    public ResearchPedestalMenu(int containerId, Inventory inventory, ResearchPedestalBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.RESEARCH_MENU, containerId);
        this.blockEntity = blockEntity;
        this.level = inventory.player.level();
        this.data = data;

        addPlayerInventory(inventory);
        addPlayerHotbar(inventory);
        this.addSlot(new Slot(blockEntity, 0, 26, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(net.bananemdnsa.historystages.init.ModItems.RESEARCH_SCROLL)
                        || stack.is(net.bananemdnsa.historystages.init.ModItems.CREATIVE_SCROLL);
            }
        });
        this.addSlot(new Slot(blockEntity, 1, 246, 142) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return blockEntity.hasScrollWithDependencies();
            }

            @Override
            public boolean isActive() {
                return blockEntity.hasScrollWithDependencies();
            }
        });
        addDataSlots(data);
    }

    public int getScaledProgress() {
        int progress = data.get(0);
        int max = data.get(1);
        return max != 0 && progress != 0 ? progress * 61 / max : 0;
    }

    public ResearchPedestalBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public BlockPos getBlockPos() {
        return blockEntity.getBlockPos();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.RESEARCH_PEDESTAL);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            if (index < 36) {
                if (!this.moveItemStackTo(stack, 36, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, 36, false)) {
                return ItemStack.EMPTY;
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
        return data.get(0) > 0 || data.get(2) > 0;
    }

    public boolean isIndividualMode() {
        return data.get(3) == 1;
    }

    public boolean areDependenciesMet() {
        return data.get(4) == 1;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(inventory, i, 8 + i * 18, 142));
        }
    }
}
