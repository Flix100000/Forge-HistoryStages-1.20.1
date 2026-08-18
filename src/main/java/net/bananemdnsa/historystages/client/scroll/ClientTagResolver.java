package net.bananemdnsa.historystages.client.scroll;

import net.bananemdnsa.historystages.data.scroll.OpenScrollContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

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
        TagKey<Item> key = TagKey.create(Registries.ITEM, id);
        // BuiltInRegistries.ITEM.getTag(...) returns an Optional, empty for an unknown tag. Same
        // pattern already used by LootLockHandler and StageLockFilter.
        List<String> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(key).ifPresent(holders -> holders.forEach(holder ->
                holder.unwrapKey().ifPresent(resourceKey -> out.add(resourceKey.location().toString()))));
        return out;
    }
}
