package net.felix.historystages.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.felix.historystages.Config;
import net.felix.historystages.data.StageManager;
import net.felix.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class StageLootModifier extends LootModifier {
    private final Random random = new Random();

    // Der Codec wird von Forge benötigt, um den Modifier aus JSON-Dateien zu laden
    public static final Supplier<Codec<StageLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, StageLootModifier::new)));

    public StageLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // Wenn das Loot-Locking in der Config komplett aus ist, nichts tun
        if (!Config.COMMON.lockMobLoot.get()) return generatedLoot;

        // Sicherstellen, dass der SERVER_CACHE geladen ist
        if (StageData.SERVER_CACHE.isEmpty()) {
            StageData.get(context.getLevel());
        }

        ObjectArrayList<ItemStack> newLoot = new ObjectArrayList<>();

        for (ItemStack stack : generatedLoot) {
            ResourceLocation resLoc = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (resLoc == null) {
                newLoot.add(stack);
                continue;
            }

            // Prüfen, ob das Item eine Stage benötigt
            String requiredStage = StageManager.getStageForItemOrMod(resLoc.toString(), resLoc.getNamespace());

            // Wenn das Item gesperrt ist (Stage nicht im Cache)
            if (requiredStage != null && !StageData.SERVER_CACHE.contains(requiredStage)) {
                ItemStack replacement = getReplacementStack(stack.getCount());
                // Falls das Ersatz-Item nicht AIR ist, fügen wir es hinzu
                if (!replacement.isEmpty()) {
                    newLoot.add(replacement);
                }
            } else {
                // Item ist erlaubt oder benötigt keine Stage
                newLoot.add(stack);
            }
        }

        return newLoot;
    }

    private ItemStack getReplacementStack(int count) {
        // KORREKTUR: Wenn der Schalter in der Config aus ist, geben wir sofort AIR zurück
        if (!Config.COMMON.useReplacements.get()) {
            return ItemStack.EMPTY;
        }

        // 1. Check Tag (Priorität)
        String tagStr = Config.COMMON.replacementTag.get();
        if (!tagStr.isEmpty()) {
            try {
                TagKey<Item> tagKey = TagKey.create(ForgeRegistries.Keys.ITEMS, new ResourceLocation(tagStr));
                var tagItems = ForgeRegistries.ITEMS.tags().getTag(tagKey).stream().toList();
                if (!tagItems.isEmpty()) {
                    return new ItemStack(tagItems.get(random.nextInt(tagItems.size())), count);
                }
            } catch (Exception e) {
                // Falls der Tag-Name ungültig ist
            }
        }

        // 2. Check Liste (Fallback)
        List<? extends String> list = Config.COMMON.replacementItems.get();
        if (!list.isEmpty()) {
            String randomId = list.get(random.nextInt(list.size()));
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(randomId));
            if (item != null && item != Items.AIR) {
                return new ItemStack(item, count);
            }
        }

        return ItemStack.EMPTY; // Fallback auf AIR
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}