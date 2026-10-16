package me.desukillz.rtp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /rtp (mở GUI), /rtp &lt;overworld|nether|end&gt; (RTP trực tiếp, vẫn tuân theo khoá thế giới), /rtp reload. */
final class RtpCommand implements TabExecutor {

    private final DesukillzRtpPlugin plugin;

    RtpCommand(DesukillzRtpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("desukillz.rtp.admin")) {
                sender.sendMessage(Text.color(plugin.prefixed("no-permission")));
                return true;
            }
            plugin.reload();
            sender.sendMessage(Text.color(plugin.prefixed("reloaded")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.color(plugin.prefixed("only-players")));
            return true;
        }

        if (args.length == 0) {
            plugin.openGui(player);
            return true;
        }

        WorldKind kind = WorldKind.parse(args[0]);
        if (kind == null) {
            plugin.send(player, "usage");
            return true;
        }
        // Dùng chung đường kiểm tra với GUI: khoá thế giới, quyền, cooldown, trận PvP
        plugin.rtp().request(player, kind, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (WorldKind kind : WorldKind.values()) {
            if (kind.key.startsWith(prefix) && sender.hasPermission(kind.permission())) {
                out.add(kind.key);
            }
        }
        if ("reload".startsWith(prefix) && sender.hasPermission("desukillz.rtp.admin")) {
            out.add("reload");
        }
        return out;
    }
}
