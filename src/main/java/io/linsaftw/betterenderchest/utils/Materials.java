package io.linsaftw.betterenderchest.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class Materials {

    private Materials() {
    }

    public static Optional<Material> find(final String... names) {
        if (names == null) {
            return Optional.empty();
        }
        for (final String raw : names) {
            if (raw == null) {
                continue;
            }
            final String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            final Material material = Material.getMaterial(normalized);
            if (material != null) {
                return Optional.of(material);
            }
        }
        return Optional.empty();
    }

    public static Material require(final String... names) {
        final Optional<Material> found = find(names);
        if (found.isPresent()) {
            return found.get();
        }
        throw new IllegalStateException("No known material for aliases: " + Arrays.toString(names));
    }

    public static ItemStack create(final String modernName, final String legacyName, final int legacyData) {
        final Optional<Material> modern = find(modernName);
        if (modern.isPresent()) {
            return new ItemStack(modern.get());
        }
        final Optional<Material> legacy = find(legacyName);
        if (legacy.isPresent()) {
            if (legacyData >= 0) {
                return new ItemStack(legacy.get(), 1, (short) legacyData);
            }
            return new ItemStack(legacy.get());
        }
        throw new IllegalStateException("No known material for aliases: [" + modernName + ", " + legacyName + "]");
    }
}
