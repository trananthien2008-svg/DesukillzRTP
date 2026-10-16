package me.desukillz.rtp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /rtpq (vào hàng chờ PvP) và /rtpq leave (rời hàng chờ). */
final class RtpqCommand implements TabExecutor {

    private final DesukillzRtpPlugin plugin;

    RtpqCommand(DesukillzRtpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.color(plugin.prefixed("only-players")));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave")) {
            plugin.queue().leave(player);
        } else {
            plugin.queue().join(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "leave".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("leave");
        }
        return List.of();
    }
}
