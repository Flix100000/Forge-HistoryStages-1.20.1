package net.bananemdnsa.historystages.screen;

import net.bananemdnsa.historystages.block.entity.ResearchStationBlockEntity;
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
import net.minecraftforge.items.SlotItemHandler;

public class ResearchStationMenu extends AbstractContainerMenu {
    private final ResearchStationBlockEntity blockEntity;
    private final Level level;
    public final ContainerData data;

    // Client-Konstruktor
    public ResearchStationMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        // WICHTIG: Hier muss eine 3 stehen, damit Platz für alle Daten ist!
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(3));
    }

    // Server-Konstruktor
    public ResearchStationMenu(int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.RESEARCH_MENU.get(), pContainerId);
        checkContainerSize(inv, 1);
        this.blockEntity = ((ResearchStationBlockEntity) entity);
        this.level = inv.player.level();
        this.data = data;

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        // Slot-Koordinaten für das Buch
        int slotX = 26;
        int slotY = 35;

        this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            this.addSlot(new SlotItemHandler(handler, 0, slotX, slotY));
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

    @Override
    public boolean stillValid(Player pPlayer) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), pPlayer, ModBlocks.RESEARCH_STATION.get());
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

            // Wenn das Item aus dem Inventar kommt (0-35)
            if (index < 36) {
                // Versuche es in den Maschinen-Slot (36) zu schieben
                if (!this.moveItemStackTo(stack, 36, 37, false)) {
                    return ItemStack.EMPTY;
                }
            }
            // Wenn das Item aus der Maschine kommt (36)
            else {
                // Versuche es ins Inventar zu schieben (0-35)
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
}