package io.linsaftw.betterenderchest.command;

import io.linsaftw.betterenderchest.service.PermissionService;
import io.linsaftw.betterenderchest.utils.FoliaAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Command-level onCommand/onTabComplete tests are not possible here: the command
// requires a concrete BetterEnderChestPlugin (final JavaPlugin, needs a server),
// and no MockBukkit/Mockito is available. This covers the testable units instead:
// grammar helpers, CommandSender permissions, and the FoliaAPI fallback.
class EnderChestAdminCommandTest {
    private static final Logger LOGGER = Logger.getLogger(EnderChestAdminCommandTest.class.getName());

    @BeforeAll
    static void installSchedulerSupport() {
        final BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(), new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    if ("runTask".equals(method.getName()) && args != null && args.length == 2
                            && args[1] instanceof Runnable) {
                        ((Runnable) args[1]).run();
                        return null;
                    }
                    return defaultAnswer(method);
                });
        final ItemFactory itemFactory = (ItemFactory) Proxy.newProxyInstance(
                ItemFactory.class.getClassLoader(), new Class<?>[]{ItemFactory.class},
                (proxy, method, args) -> {
                    if ("ensureServerConversions".equals(method.getName())) {
                        return args[0];
                    }
                    if ("equals".equals(method.getName())) {
                        return args[0] == args[1];
                    }
                    return defaultAnswer(method);
                });
        final UnsafeValues unsafe = (UnsafeValues) Proxy.newProxyInstance(
                UnsafeValues.class.getClassLoader(), new Class<?>[]{UnsafeValues.class},
                (proxy, method, args) -> {
                    if ("getMaterial".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof String) {
                        return Material.valueOf((String) args[0]);
                    }
                    if ("getDataVersion".equals(method.getName())) {
                        return 0;
                    }
                    return defaultAnswer(method);
                });
        final Server server = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getScheduler".equals(method.getName())) {
                        return scheduler;
                    }
                    if ("getUnsafe".equals(method.getName())) {
                        return unsafe;
                    }
                    if ("getItemFactory".equals(method.getName())) {
                        return itemFactory;
                    }
                    if ("getLogger".equals(method.getName())) {
                        return LOGGER;
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakeServer";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return defaultAnswer(method);
                });
        Server existing = null;
        try {
            existing = Bukkit.getServer();
        } catch (final Throwable ignored) {
            existing = null;
        }
        if (existing == null) {
            try {
                Bukkit.setServer(server);
                return;
            } catch (final UnsupportedOperationException | IllegalStateException alreadySet) {
                replaceServer(server);
                return;
            }
        }
        replaceServer(server);
    }

    @AfterAll
    static void resetBukkitServer() {
        try {
            final Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (final ReflectiveOperationException | SecurityException ignored) {
            // Best effort: lets a later test class install its own singleton.
        }
    }

    private static void replaceServer(final Server server) {
        try {
            final Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, server);
        } catch (final ReflectiveOperationException | SecurityException ignored) {
            // Cannot upgrade shared singleton; scheduler test will skip via assumption.
        }
    }

    @Test
    void clearTokenMatchesCaseInsensitive() {
        assertTrue(EnderChestAdminCommand.isClearAction("clear"));
        assertTrue(EnderChestAdminCommand.isClearAction("CLEAR"));
        assertTrue(EnderChestAdminCommand.isClearAction("ClEaR"));
        assertFalse(EnderChestAdminCommand.isClearAction("view"));
        assertFalse(EnderChestAdminCommand.isClearAction("delete"));
        assertFalse(EnderChestAdminCommand.isClearAction(""));
        assertFalse(EnderChestAdminCommand.isClearAction(null));
        assertFalse(EnderChestAdminCommand.isClearAction(" clear"));
    }

    @Test
    void viewTokenMatchesCaseInsensitive() {
        assertTrue(EnderChestAdminCommand.isViewAction("view"));
        assertTrue(EnderChestAdminCommand.isViewAction("VIEW"));
        assertTrue(EnderChestAdminCommand.isViewAction("ViEw"));
        assertFalse(EnderChestAdminCommand.isViewAction("clear"));
        assertFalse(EnderChestAdminCommand.isViewAction("delete"));
        assertFalse(EnderChestAdminCommand.isViewAction(""));
        assertFalse(EnderChestAdminCommand.isViewAction(null));
    }

    @Test
    void noTokenMatchesBothActions() {
        for (final String token : Arrays.asList("clear", "CLEAR", "view", "VIEW", "delete", "", null)) {
            assertFalse(EnderChestAdminCommand.isClearAction(token) && EnderChestAdminCommand.isViewAction(token),
                    "token must not match both: " + token);
        }
    }

    @Test
    void secondTokenCompletionFiltersByPrefix() {
        assertEquals(Arrays.asList("clear", "view"), EnderChestAdminCommand.completeSecondToken(""));
        assertEquals(Arrays.asList("clear", "view"), EnderChestAdminCommand.completeSecondToken(null));
        assertEquals(Collections.singletonList("clear"), EnderChestAdminCommand.completeSecondToken("c"));
        assertEquals(Collections.singletonList("clear"), EnderChestAdminCommand.completeSecondToken("C"));
        assertEquals(Collections.singletonList("view"), EnderChestAdminCommand.completeSecondToken("v"));
        assertEquals(Collections.singletonList("view"), EnderChestAdminCommand.completeSecondToken("V"));
        assertTrue(EnderChestAdminCommand.completeSecondToken("x").isEmpty());
    }

    @Test
    void canonicalAdminPermissionGrantsAccess() {
        final PermissionService permissions = new PermissionService(null);
        assertTrue(permissions.canAdmin(playerSender(permission("betterenderchest.admin"), false)));
    }

    @Test
    void legacyAliasesGrantAccess() {
        final PermissionService permissions = new PermissionService(null);
        assertTrue(permissions.canAdmin(playerSender(permission("enderchest.admin"), false)));
        assertTrue(permissions.canAdmin(playerSender(permission("enderchest.admin.lol"), false)));
    }

    @Test
    void operatorGrantsAccessWithoutPermission() {
        assertTrue(new PermissionService(null).canAdmin(playerSender(new HashMap<String, Boolean>(), true)));
    }

    @Test
    void plainPlayerDenied() {
        assertFalse(new PermissionService(null).canAdmin(playerSender(new HashMap<String, Boolean>(), false)));
    }

    @Test
    void consoleGrantedWithoutChecks() {
        final ConsoleCommandSender console = (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(), new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> defaultAnswer(method));
        assertTrue(new PermissionService(null).canAdmin(console));
    }

    @Test
    void unprivilegedBlockSenderDenied() {
        assertFalse(new PermissionService(null).canAdmin(blockSender(false)));
    }

    @Test
    void privilegedBlockSenderGranted() {
        assertTrue(new PermissionService(null).canAdmin(blockSender(true)));
    }

    @Test
    void runGlobalFallsBackToBukkitScheduler() {
        Assumptions.assumeTrue(Bukkit.getScheduler() != null,
                "Bukkit scheduler unavailable (shared singleton without scheduler support)");
        final FoliaAPI folia = new FoliaAPI(pluginProxy());
        assertFalse(folia.isFolia());
        final AtomicBoolean ran = new AtomicBoolean(false);
        folia.runGlobal(() -> ran.set(true));
        assertTrue(ran.get());
    }

    private static Map<String, Boolean> permission(final String node) {
        final Map<String, Boolean> permissions = new HashMap<String, Boolean>();
        permissions.put(node, Boolean.TRUE);
        return permissions;
    }

    private static Player playerSender(final Map<String, Boolean> permissions, final boolean op) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof String) {
                        return permissions.getOrDefault((String) args[0], Boolean.FALSE);
                    }
                    if ("isOp".equals(method.getName())) {
                        return op;
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakePlayer";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return defaultAnswer(method);
                });
    }

    private static BlockCommandSender blockSender(final boolean privileged) {
        return (BlockCommandSender) Proxy.newProxyInstance(
                BlockCommandSender.class.getClassLoader(), new Class<?>[]{BlockCommandSender.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof String) {
                        return privileged;
                    }
                    if ("isOp".equals(method.getName())) {
                        return false;
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakeBlockSender";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return defaultAnswer(method);
                });
    }

    private static Plugin pluginProxy() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("isEnabled".equals(method.getName())) {
                        return true;
                    }
                    if ("getName".equals(method.getName())) {
                        return "TestPlugin";
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakePlugin";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return defaultAnswer(method);
                });
    }

    private static Object defaultAnswer(final Method method) {
        final Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == void.class) {
            return null;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == String.class) {
            return "";
        }
        return null;
    }
}
