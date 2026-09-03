package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.DependencyProgress;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What an item-tag requirement does with the things thrown into a pedestal.
 *
 * <p>A tag entry is the only requirement in the mod that changes what it demands while a player is
 * working on it: the first matching item is written to the scroll, and from then on the entry
 * wants that item and nothing else. Everything worth getting wrong lives in that one moment —
 * settling too early, settling on the wrong thing, or letting an open tag take an item a plain
 * entry in the same group was waiting for.
 *
 * <p>{@code minecraft:planks} rather than one of the common ingot tags, because it is vanilla and
 * has many members without anything having to load a datapack for it.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemTagDepositTests {

    private static final String PLANKS = "#minecraft:planks";

    private ItemTagDepositTests() {}

    @GameTest(template = "empty")
    public static void anOpenTagSettlesOnTheFirstItemHandedIn(GameTestHelper helper) {
        try {
            GameTestStages.global("tagsettle", tagGroup(3));
            ResearchPedestalBlockEntity pedestal = pedestalWithScroll(helper, "tagsettle");

            pedestal.offerDeposit(new ItemStack(Items.BIRCH_PLANKS, 1));

            CompoundTag deposited = depositedOf(pedestal);
            if (deposited.getInt(countKey()) != 1) {
                helper.fail("one plank was handed in, but the tag counter reads "
                        + deposited.getInt(countKey()));
                return;
            }
            if (!"minecraft:birch_planks".equals(deposited.getString(choiceKey()))) {
                helper.fail("the tag should have settled on birch planks, but the scroll records '"
                        + deposited.getString(choiceKey()) + "'");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aSettledTagRefusesAnotherMemberOfTheSameTag(GameTestHelper helper) {
        try {
            GameTestStages.global("tagrefuse", tagGroup(3));
            ResearchPedestalBlockEntity pedestal = pedestalWithScroll(helper, "tagrefuse");

            pedestal.offerDeposit(new ItemStack(Items.BIRCH_PLANKS, 1));
            // Oak is in the tag too. Before the entry settled it would have counted; now it must
            // not, or the tag is nothing but a shorter way of writing an OR of items.
            boolean wanted = pedestal.offerDeposit(new ItemStack(Items.OAK_PLANKS, 1));

            CompoundTag deposited = depositedOf(pedestal);
            if (wanted) {
                helper.fail("the pedestal reported wanting oak planks for a tag that has "
                        + "already settled on birch");
                return;
            }
            if (deposited.getInt(countKey()) != 1) {
                helper.fail("oak planks were counted towards a tag settled on birch: counter reads "
                        + deposited.getInt(countKey()));
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void aSettledTagKeepsTakingTheItemItChose(GameTestHelper helper) {
        try {
            GameTestStages.global("tagcontinue", tagGroup(3));
            ResearchPedestalBlockEntity pedestal = pedestalWithScroll(helper, "tagcontinue");

            pedestal.offerDeposit(new ItemStack(Items.BIRCH_PLANKS, 1));
            pedestal.offerDeposit(new ItemStack(Items.BIRCH_PLANKS, 2));

            int counted = depositedOf(pedestal).getInt(countKey());
            if (counted != 3) {
                helper.fail("three birch planks were handed in for a tag settled on birch, "
                        + "but the counter reads " + counted);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The rule the deposit order exists for.
     *
     * <p>A group wanting one oak plank outright and one of any plank has two entries competing for
     * the same stack. The named entry can only ever be satisfied by oak; the tag could still take
     * spruce, birch or anything else. Handing the tag the oak would cost the player a choice for
     * nothing.
     */
    @GameTest(template = "empty")
    public static void aNamedItemIsServedBeforeAnOpenTag(GameTestHelper helper) {
        try {
            DependencyGroup group = tagGroup(1);
            group.setItems(new ArrayList<>(List.of(new DependencyItem("minecraft:oak_planks", 1))));
            GameTestStages.global("tagorder", group);
            ResearchPedestalBlockEntity pedestal = pedestalWithScroll(helper, "tagorder");

            pedestal.offerDeposit(new ItemStack(Items.OAK_PLANKS, 1));

            CompoundTag deposited = depositedOf(pedestal);
            String itemKey = DependencyProgress.key("0",
                    DependencyProgress.itemSuffix("minecraft:oak_planks"));
            if (deposited.getInt(itemKey) != 1) {
                helper.fail("the oak plank should have gone to the entry naming it, but that "
                        + "counter reads " + deposited.getInt(itemKey));
                return;
            }
            if (!deposited.getString(choiceKey()).isEmpty()) {
                helper.fail("the open tag settled on '" + deposited.getString(choiceKey())
                        + "' even though the entry naming that item took the plank");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The gate in front of the deposit slot, which decides whether the slot starts counting at
     * all. Blind to tags, it never lets {@code tryProcessDeposit} run, and a group asking for
     * nothing but a tag can never be completed however long the player waits.
     */
    @GameTest(template = "empty")
    public static void theSlotWantsAnItemForATagOnlyGroup(GameTestHelper helper) {
        try {
            GameTestStages.global("taggate", tagGroup(2));
            ResearchPedestalBlockEntity pedestal = pedestalWithScroll(helper, "taggate");

            if (!pedestal.offerDeposit(new ItemStack(Items.SPRUCE_PLANKS, 1))) {
                helper.fail("the pedestal did not want a plank for a group that asks for "
                        + "nothing but a plank tag");
                return;
            }
            if (pedestal.offerDeposit(new ItemStack(Items.DIAMOND, 1))) {
                helper.fail("the pedestal wanted a diamond for a group that asks only for planks");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    // --- Shared ---

    private static DependencyGroup tagGroup(int count) {
        DependencyGroup group = new DependencyGroup();
        group.setItemTags(new ArrayList<>(List.of(new DependencyItem(PLANKS, count))));
        return group;
    }

    /**
     * A pedestal holding a scroll researching {@code gametest:<name>}.
     *
     * <p>The stage has to exist before the scroll points at it: the pedestal looks the entry up on
     * every deposit and does nothing at all when it finds none, which would leave every assertion
     * below passing for the wrong reason.
     */
    private static ResearchPedestalBlockEntity pedestalWithScroll(GameTestHelper helper, String name) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.RESEARCH_PEDESTAL.get());

        BlockEntity be = helper.getBlockEntity(pos);
        if (!(be instanceof ResearchPedestalBlockEntity pedestal)) {
            throw new IllegalStateException("research pedestal placed but no block entity behind it");
        }

        ItemStack scroll = new ItemStack(ModItems.RESEARCH_SCROLL.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("StageResearch", GameTestStages.PREFIX + name);
        scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        pedestal.getItemHandler().setStackInSlot(0, scroll);
        return pedestal;
    }

    private static CompoundTag depositedOf(ResearchPedestalBlockEntity pedestal) {
        CompoundTag tag = pedestal.getScrollStack()
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("DepositedDependencies")
                ? tag.getCompound("DepositedDependencies") : new CompoundTag();
    }

    /** The group is the only one on the stage and carries no id of its own, so its key is "0". */
    private static String countKey() {
        return DependencyProgress.key("0", DependencyProgress.itemTagSuffix(PLANKS));
    }

    private static String choiceKey() {
        return DependencyProgress.key("0", DependencyProgress.itemTagChoiceSuffix(PLANKS));
    }
}
