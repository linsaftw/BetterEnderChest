package io.linsaftw.betterenderchest.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ControlItemDefinition(String modernMaterialName, String legacyMaterialName, int legacyData,
        String name, List<String> lore) {
    public ControlItemDefinition {
        if ((modernMaterialName == null || modernMaterialName.isBlank())
                && (legacyMaterialName == null || legacyMaterialName.isBlank())) {
            throw new IllegalArgumentException("modernMaterialName and legacyMaterialName must not both be blank");
        }
        if (legacyData != -1 && (legacyData < -1 || legacyData > Short.MAX_VALUE)) {
            throw new IllegalArgumentException("legacyData must be -1..Short.MAX_VALUE");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        lore = lore == null ? Collections.emptyList() : List.copyOf(new ArrayList<>(lore));
    }

    public String materialName() {
        if (modernMaterialName != null && !modernMaterialName.isBlank()) {
            return modernMaterialName;
        }
        return legacyMaterialName;
    }

    public String displayName() {
        return name;
    }
}
