package io.linsaftw.betterenderchest.text;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class TextBridge {
    private final JavaPlugin plugin;
    private final TextCodec codec;
    private final BukkitAudiences audiences;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public TextBridge(JavaPlugin plugin, TextCodec codec) {
        this.plugin = plugin;
        this.codec = codec;
        this.audiences = BukkitAudiences.create(plugin);
    }

    public void send(CommandSender sender, String source) {
        if (sender instanceof Player) {
            audiences.player((Player) sender).sendMessage(codec.parse(source));
        } else {
            sender.sendMessage(codec.toLegacy(source));
        }
    }

    public void send(CommandSender sender, String source, Map<String, String> placeholders) {
        if (sender instanceof Player) {
            audiences.player((Player) sender).sendMessage(codec.parse(source, placeholders));
        } else {
            sender.sendMessage(codec.toLegacy(source, placeholders));
        }
    }

    public TextCodec codec() {
        return codec;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            audiences.close();
        }
    }
}
