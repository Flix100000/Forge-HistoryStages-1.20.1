package net.bananemdnsa.historystages.client.editor.widget.list;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.client.editor.nbt.ComponentCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.CustomDataCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.EnchantmentListCriterion;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriteriaCodec;
import net.bananemdnsa.historystages.client.editor.nbt.NbtCriterion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shape "import item from inventory" has to produce.
 *
 * <p>Before the redesign it wrote unbreakable, custom_model_data, repair_cost, potion_contents,
 * custom_name and lore back as pre-1.20.5 top-level keys — criteria {@code NbtMatcher} looks up in
 * {@code custom_data}, where a normal item never has them. Those imports could not match anything.
 *
 * <p>The test works on the model side rather than calling {@code SearchableItemList}: that class
 * needs a loaded client, and touching it from a unit test would pull Minecraft onto the classpath.
 */
class ItemImportCriteriaShapeTest {

    private static final Gson GSON = new Gson();

    @Test
    void componentsImportAsComponentCriteria() {
        JsonObject imported = GSON.fromJson("""
                {"components":{"minecraft:unbreakable":{},"minecraft:repair_cost":3}}
                """, JsonObject.class);

        List<NbtCriterion> loaded = NbtCriteriaCodec.load(imported);

        assertEquals(2, loaded.size());
        for (NbtCriterion criterion : loaded) {
            assertInstanceOf(ComponentCriterion.class, criterion);
        }
    }

    @Test
    void enchantmentsStayTopLevel() {
        JsonObject imported = GSON.fromJson("""
                {"Enchantments":[{"id":"minecraft:sharpness","lvl":3}]}
                """, JsonObject.class);

        assertInstanceOf(EnchantmentListCriterion.class, NbtCriteriaCodec.load(imported).get(0));
    }

    @Test
    void aLegacyTopLevelImportWouldBeFlagged() {
        JsonObject legacy = GSON.fromJson("""
                {"Unbreakable":true}
                """, JsonObject.class);

        CustomDataCriterion custom =
                assertInstanceOf(CustomDataCriterion.class, NbtCriteriaCodec.load(legacy).get(0));
        assertTrue(custom.legacySuspect,
                "if the importer ever emits this shape again, the editor has to say so");
    }
}
