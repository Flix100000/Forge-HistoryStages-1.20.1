package net.astr0.historystages.api;

/**
 * LockFlags allow for extremely fast runtime conditional checking of certain rare cases or specific info. These flags can be checked using
 * {@link LockCategory#hasFlag(Object, int)}.
 * <p>
 * These lock flags allow for optimised checking of certain metadata which can be pre-computed in order to avoid
 * continuous recalculations at runtime. For an example, refer to {@link LockFlags#ITEM_HAS_NBT_LOCK}.
 */
public class LockFlags {
    /**
     * This flag should be set on the lock for any item which has at least one NBT restriction on it.
     * This flag can be checked at runtime to see if expensive NBT comparisons are needed or not for a given item.
     * <p>
     * For items which do not have any NBT locks applied to them, we should not waste the processing power on checking any NBT data
     */
    public static int ITEM_HAS_NBT_LOCK = -1;
    public static int ITEM_USE = -1;
    public static int ITEM_ATTACK = -1;
    public static int ITEM_EQUIP = -1;
    public static int ITEM_PICKUP = -1;
    public static int BLOCK_PLACE = -1;
    public static int BLOCK_BREAK = -1;
    /**
     * Do any stages that lock this block, allow access to the GUI even whilst it is locked.
     * If even a single stage allows this behaviour, this flag should be set.
     */
    public static int BLOCK_HAS_GUI_EXCEPTIONS = -1;
    public static int OUTPUT_LOOT = -1;
    public static int OUTPUT_RECIPE = -1;
    public static int OUTPUT_ICON = -1;
    public static int LOCK_IS_GLOBAL_ONLY = -1;
    public static int LOCK_IS_INDIVIDUAL_ONLY = -1;
    public static int LOCK_IS_DUAL_PHASE = -1;


    public static void initLockFlags(IStageManager manager) {
        ITEM_HAS_NBT_LOCK = manager.registerMetadataBit("ITEM_HAS_NBT_LOCK");
        ITEM_USE = manager.registerMetadataBit("ITEM_USE");
        ITEM_ATTACK = manager.registerMetadataBit("ITEM_ATTACK");
        ITEM_EQUIP = manager.registerMetadataBit("ITEM_EQUIP");
        ITEM_PICKUP = manager.registerMetadataBit("ITEM_PICKUP");
        BLOCK_PLACE = manager.registerMetadataBit("BLOCK_PLACE");
        BLOCK_BREAK = manager.registerMetadataBit("BLOCK_BREAK");
        BLOCK_HAS_GUI_EXCEPTIONS = manager.registerMetadataBit("BLOCK_HAS_GUI_EXCEPTIONS");
        OUTPUT_LOOT = manager.registerMetadataBit("OUTPUT_LOOT");
        OUTPUT_RECIPE = manager.registerMetadataBit("OUTPUT_RECIPE");
        OUTPUT_ICON = manager.registerMetadataBit("OUTPUT_ICON");
        LOCK_IS_GLOBAL_ONLY = manager.registerMetadataBit("LOCK_IS_GLOBAL_ONLY");
        LOCK_IS_INDIVIDUAL_ONLY = manager.registerMetadataBit("LOCK_IS_INDIVIDUAL_ONLY");
        LOCK_IS_DUAL_PHASE = manager.registerMetadataBit("LOCK_IS_DUAL_PHASE");
    }
}
