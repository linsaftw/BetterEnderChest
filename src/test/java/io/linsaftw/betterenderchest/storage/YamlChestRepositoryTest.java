package io.linsaftw.betterenderchest.storage;

import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class YamlChestRepositoryTest {
    private static final Logger LOGGER = Logger.getLogger(YamlChestRepositoryTest.class.getName());
    @BeforeAll
    static void installBukkitSerializationSupport() {
        final ItemFactory itemFactory = (ItemFactory) Proxy.newProxyInstance(
                ItemFactory.class.getClassLoader(), new Class<?>[]{ItemFactory.class},
                (proxy, method, args) -> {
                    if ("ensureServerConversions".equals(method.getName())) {
                        return args[0];
                    }
                    if ("equals".equals(method.getName())) {
                        return args[0] == args[1];
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
        final UnsafeValues unsafe = (UnsafeValues) Proxy.newProxyInstance(
                UnsafeValues.class.getClassLoader(), new Class<?>[]{UnsafeValues.class},
                (proxy, method, args) -> {
                    if ("getMaterial".equals(method.getName())) {
                        return Material.valueOf((String) args[0]);
                    }
                    if ("getDataVersion".equals(method.getName())) {
                        return 0;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
        final Server testServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getUnsafe".equals(method.getName())) {
                        return unsafe;
                    }
                    if ("getLogger".equals(method.getName())) {
                        return LOGGER;
                    }
                    if ("getItemFactory".equals(method.getName())) {
                        return itemFactory;
                    }
                    return null;
                });
        Bukkit.setServer(testServer);
    }

    @Test
    void preservesLegacySlot53(@TempDir final Path dataFolder) throws Exception {
        final UUID owner = UUID.randomUUID();
        final ItemStack item = new ItemStack(Material.DIAMOND, 1);
        final YamlConfiguration config = new YamlConfiguration();
        config.set("player-name", "Legacy");
        config.set("last-known-rows", 6);
        config.set("items.53", item);
        config.save(dataFolder.resolve(owner + ".yml").toFile());

        final ChestData loaded = new YamlChestRepository(LOGGER, dataFolder).load(owner, "Fallback");

        assertEquals(6, loaded.lastKnownRows());
        assertEquals(item, loaded.copyContents()[53]);
    }

    @Test
    void roundTripsHiddenSlotsAndItemStacks(@TempDir final Path dataFolder) {
        final UUID owner = UUID.randomUUID();
        final ItemStack item = new ItemStack(Material.DIAMOND_SWORD, 1);
        final ItemStack[] contents = new ItemStack[63];
        contents[62] = item;
        final ChestData original = new ChestData(owner, "Player", 6, contents);
        final YamlChestRepository repository = new YamlChestRepository(LOGGER, dataFolder);

        repository.save(original);
        final ChestData loaded = repository.load(owner, "Fallback");

        assertEquals(6, loaded.lastKnownRows());
        assertEquals(63, loaded.slotCount());
        assertEquals(item.serialize(), loaded.copyContents()[62].serialize());
    }

    @Test
    void malformedKeyDoesNotDiscardValidItem(@TempDir final Path dataFolder) throws Exception {
        final UUID owner = UUID.randomUUID();
        final ItemStack valid = new ItemStack(Material.EMERALD, 1);
        final YamlConfiguration config = new YamlConfiguration();
        config.set("player-name", "Player");
        config.set("last-known-rows", 1);
        config.set("items.0", valid);
        config.set("items.not-a-slot", valid);
        config.save(dataFolder.resolve(owner + ".yml").toFile());

        final ChestData loaded = new YamlChestRepository(LOGGER, dataFolder).load(owner, "Fallback");

        assertEquals(valid, loaded.copyContents()[0]);
    }

    @Test
    void loadsOldFormatWithoutRowsOrVersion(@TempDir final Path dataFolder) throws Exception {
        final UUID owner = UUID.randomUUID();
        final ItemStack first = new ItemStack(Material.STONE, 1);
        final ItemStack last = new ItemStack(Material.DIAMOND_SWORD, 1);
        final YamlConfiguration config = new YamlConfiguration();
        config.set("player-name", "OldPlayer");
        config.set("last-saved", 123456789L);
        config.set("items.0", first);
        config.set("items.53", last);
        config.save(dataFolder.resolve(owner + ".yml").toFile());

        final YamlChestRepository repository = new YamlChestRepository(LOGGER, dataFolder);
        final ChestData loaded = repository.load(owner, "Fallback");

        assertNotNull(loaded.copyContents()[0]);
        assertNotNull(loaded.copyContents()[53]);
        assertEquals(first, loaded.copyContents()[0]);
        assertEquals(last, loaded.copyContents()[53]);
        assertEquals(54, loaded.slotCount());

        repository.save(new ChestData(loaded.ownerUuid(), loaded.ownerName(),
                loaded.lastKnownRows(), loaded.copyContents()));
        final ItemStack[] reloaded = repository.load(owner, "Fallback").copyContents();

        assertEquals(first, reloaded[0]);
        assertEquals(last, reloaded[53]);
        for (int slot = 1; slot < 53; slot++) {
            assertNull(reloaded[slot]);
        }
    }
}
