package net.minecraftforge.registries;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ForgeRegistries {
    public static final RegistryView<Item> ITEMS = new RegistryView<>(BuiltInRegistries.ITEM);
    public static final RegistryView<Block> BLOCKS = new RegistryView<>(BuiltInRegistries.BLOCK);
    public static final RegistryView<EntityType<?>> ENTITY_TYPES = new RegistryView<>(BuiltInRegistries.ENTITY_TYPE);

    private ForgeRegistries() {
    }

    public static final class RegistryView<T> implements Iterable<T> {
        private final Registry<T> registry;

        public RegistryView(Registry<T> registry) {
            this.registry = registry;
        }

        public java.util.Set<ResourceLocation> getKeys() {
            return registry.keySet();
        }

        public ResourceLocation getKey(T value) {
            return registry.getKey(value);
        }

        public T getValue(ResourceLocation id) {
            return registry.get(id);
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return registry.iterator();
        }
    }
}
