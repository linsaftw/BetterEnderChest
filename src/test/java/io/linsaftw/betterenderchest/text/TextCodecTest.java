package io.linsaftw.betterenderchest.text;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextCodecTest {
    private final TextCodec codec = new TextCodec();

    @Test
    void minimessageRendersWithoutRawTags() {
        String legacy = codec.toLegacy("<red>Hello");
        assertTrue(legacy.contains("Hello"));
        assertFalse(legacy.contains("<red>"));
    }

    @Test
    void ampInput() {
        String legacy = codec.toLegacy("&cHello");
        assertTrue(legacy.contains("Hello"));
        assertFalse(legacy.contains("&c"));
    }

    @Test
    void sectionInput() {
        String legacy = codec.toLegacy("§cHello");
        assertTrue(legacy.contains("Hello"));
    }

    @Test
    void titlePlaceholders() {
        Map<String, String> map = new HashMap<>();
        map.put("page", "2");
        map.put("pages", "5");
        String legacy = codec.toLegacy("Chest {page}/{pages}", map);
        assertTrue(legacy.contains("2/5"));
        assertFalse(legacy.contains("{page}"));
    }

    @Test
    void oldTitleSuffix() {
        String legacy = codec.inventoryTitle("Chest", 1, 3);
        assertTrue(legacy.contains("(Page 1/3)"));
    }

    @Test
    void itemNameNoRawTag() {
        String name = codec.itemName("<red>Sword", Collections.<String, String>emptyMap());
        assertTrue(name.contains("Sword"));
        assertFalse(name.contains("<red>"));
    }

    @Test
    void loreItalicDisabled() {
        List<String> lore = codec.itemLore(Arrays.asList("&7Line"), Collections.<String, String>emptyMap());
        assertEquals(1, lore.size());
        assertFalse(lore.get(0).contains("<"));
        Component back = LegacyComponentSerializer.legacySection().deserialize(lore.get(0));
        assertNotEquals(TextDecoration.State.TRUE, back.decoration(TextDecoration.ITALIC));
    }

    @Test
    void placeholderInjectionEscaped() {
        Map<String, String> map = new HashMap<>();
        map.put("name", "<red>evil");
        String legacy = codec.toLegacy("Hi {name}", map);
        assertTrue(legacy.contains("evil"));
    }
}
