package io.linsaftw.betterenderchest.resources;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorTest {
    @Test
    void descriptorDeclaresApiVersionFoliaAndPermissionDefaults() throws Exception {
        InputStream in = PluginDescriptorTest.class.getResourceAsStream("/plugin.yml");
        assertNotNull(in, "plugin.yml must be on test classpath");
        String content;
        try (InputStream open = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = open.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }

        assertTrue(content.contains("api-version:"),
                "plugin.yml must declare api-version");
        assertTrue(content.replace("'", "").replace("\"", "").contains("api-version: 1.13"),
                "api-version must be 1.13");
        assertTrue(content.contains("folia-supported: true"),
                "plugin.yml must declare folia-supported: true");

        Map<String, String> defaults = parsePermissionDefaults(content);
        assertEquals("true", defaults.get("betterenderchest.use"));
        assertEquals("op", defaults.get("betterenderchest.admin"));
        String[] rows = {"special", "mvp", "elite", "mythical", "celestial", "immortal", "patron"};
        for (String row : rows) {
            assertEquals("false", defaults.get("betterenderchest.rows." + row),
                    "row perm betterenderchest.rows." + row + " must default to false");
        }
    }

    private static Map<String, String> parsePermissionDefaults(String content) {
        Map<String, String> defaults = new HashMap<>();
        String current = null;
        for (String raw : content.split("\n")) {
            String trimmed = raw.trim();
            if (trimmed.startsWith("betterenderchest.") && trimmed.endsWith(":")) {
                current = trimmed.substring(0, trimmed.length() - 1);
            } else if (current != null && trimmed.startsWith("default:")) {
                defaults.put(current, trimmed.substring("default:".length()).trim());
            }
        }
        return defaults;
    }
}
