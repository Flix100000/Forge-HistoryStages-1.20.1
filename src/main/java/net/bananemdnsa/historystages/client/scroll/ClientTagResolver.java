package net.bananemdnsa.historystages.client.scroll;

import net.bananemdnsa.historystages.data.scroll.OpenScrollContent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The real {@link OpenScrollContent.TagResolver}: asks the item registry which items carry a tag.
 * Client-side only, which is fine — tags are synced to the client with the rest of the registry
 * data before any screen can open.
 */
public final class ClientTagResolver implements OpenScrollContent.TagResolver {

    public static final ClientTagResolver INSTANCE = new ClientTagResolver();

    private ClientTagResolver() {}

    @Override
    public List<String> itemsInTag(String tagId) {
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        if (id == null) return List.of();
        TagKey<Item> key = ItemTags.create(id);
        // BuiltInRegistries.ITEM has no getTagOrEmpty in this NeoForge version — getTag() returns
        // an Optional instead, empty for an unknown tag. Same pattern already used by
        // LootLockHandler and StageLockFilter.
        Optional<? extends Iterable<Holder<Item>>> holders = BuiltInRegistries.ITEM.getTag(key);
        List<String> out = new ArrayList<>();
        holders.ifPresent(iterable -> iterable.forEach(holder ->
                holder.unwrapKey().ifPresent(resourceKey -> out.add(resourceKey.location().toString()))));
        return out;
    }
}
