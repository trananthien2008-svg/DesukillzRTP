package me.desukillz.rtp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Dọn dữ liệu khi người chơi thoát và kết thúc trận PvP khi có người chết. */
final class PlayerListener implements Listener {

    private final DesukillzRtpPlugin plugin;

    PlayerListener(DesukillzRtpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.rtp().onQuit(id);
        plugin.queue().onQuit(id);
        plugin.forgetGui(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.queue().onDeath(player.getUniqueId());
    }
}
