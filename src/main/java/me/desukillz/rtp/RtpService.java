package me.desukillz.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Điều phối một yêu cầu RTP: kiểm tra điều kiện phía máy chủ, tìm vị trí, dịch chuyển, cooldown.
 * Mọi kiểm tra (khoá thế giới, quyền, cooldown, trận PvP) đều làm lại ở đây, không tin vào biểu tượng GUI.
 */
final class RtpService {

    /** Phiên đang tìm vị trí của một người chơi; bị huỷ khi họ thoát server. */
    static final class Session {
        volatile boolean cancelled;
    }

    private final DesukillzRtpPlugin plugin;
    private final LocationFinder finder;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    RtpService(DesukillzRtpPlugin plugin) {
        this.plugin = plugin;
        this.finder = new LocationFinder(plugin);
    }

    LocationFinder finder() {
        return finder;
    }

    // ------------------------------------------------------------------ phiên

    boolean isBusy(UUID id) {
        return sessions.containsKey(id);
    }

    /** @return phiên mới, hoặc null nếu người chơi đang bận */
    Session tryBegin(UUID id) {
        Session s = new Session();
        return sessions.putIfAbsent(id, s) == null ? s : null;
    }

    void end(UUID id) {
        sessions.remove(id);
    }

    /** Người chơi thoát: huỷ phiên đang tìm và dọn dữ liệu. */
    void onQuit(UUID id) {
        Session s = sessions.remove(id);
        if (s != null) {
            s.cancelled = true;
        }
        cooldowns.remove(id);
    }

    long cooldownLeftSeconds(Player player) {
        if (player.hasPermission("desukillz.rtp.cooldown.bypass")) {
            return 0;
        }
        Long until = cooldowns.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            cooldowns.remove(player.getUniqueId());
            return 0;
        }
        return (left + 999) / 1000;
    }

    // ------------------------------------------------------------------ yêu cầu RTP

    /**
     * Xử lý yêu cầu RTP vào một thế giới. Gọi trên thread của người chơi.
     *
     * @param gui GUI đang mở (null nếu dùng lệnh); sẽ được đóng khi yêu cầu hợp lệ
     */
    void request(Player player, WorldKind kind, RtpGui gui) {
        Settings s = plugin.settings();
        Settings.WorldRule rule = s.rule(kind);

        // 1. Thế giới bị khoá: chặn tuyệt đối, kể cả khi dùng lệnh hoặc GUI cũ
        if (!rule.enabled()) {
            plugin.send(player, "locked", "world", kind.displayName);
            s.sound("locked").play(player);
            return;
        }
        // 2. Quyền
        if (!player.hasPermission("desukillz.rtp.use")) {
            plugin.send(player, "no-permission");
            s.sound("fail").play(player);
            return;
        }
        if (!player.hasPermission(kind.permission())) {
            plugin.send(player, "no-permission-world", "world", kind.displayName);
            s.sound("fail").play(player);
            return;
        }
        // 3. Đang trong trận PvP: không cho dùng RTP để trốn
        if (plugin.queue().inMatch(player.getUniqueId()) && !s.queue().allowRtpDuringMatch()) {
            plugin.send(player, "in-match");
            s.sound("fail").play(player);
            return;
        }
        // 4. Cooldown
        long cooldown = cooldownLeftSeconds(player);
        if (cooldown > 0) {
            plugin.send(player, "cooldown", "seconds", cooldown);
            s.sound("fail").play(player);
            return;
        }
        // 5. Thế giới phải tồn tại và đã được tải. KHÔNG tự tạo world.
        World world = Bukkit.getWorld(rule.worldName());
        if (world == null) {
            plugin.send(player, "world-missing", "world", rule.worldName());
            s.sound("fail").play(player);
            return;
        }
        if (world.getEnvironment() != kind.environment) {
            plugin.send(player, "world-wrong-environment", "world", rule.worldName());
            s.sound("fail").play(player);
            return;
        }
        // 6. Chống bấm liên tục
        Session session = tryBegin(player.getUniqueId());
        if (session == null) {
            plugin.send(player, "already-searching");
            return;
        }

        if (gui != null) {
            s.sound("select").play(player);
            gui.closedByPlugin = true;
            player.closeInventory();
        }
        plugin.send(player, "searching", "world", kind.displayName);

        finder.find(world, rule, () -> LocationFinder.ring(rule), () -> session.cancelled)
                .whenComplete((location, error) -> {
                    if (error != null) {
                        plugin.fail("rtp-search", error);
                    }
                    Location target = error == null ? location : null;
                    // Quay về thread của người chơi. Nếu họ đã thoát thì chạy retired để dọn.
                    player.getScheduler().run(plugin, task -> complete(player, kind, session, target),
                            () -> end(player.getUniqueId()));
                });
    }

    /** Chạy trên thread của người chơi sau khi tìm xong. */
    private void complete(Player player, WorldKind kind, Session session, Location target) {
        UUID id = player.getUniqueId();
        Settings s = plugin.settings();

        if (session.cancelled || !player.isOnline()) {
            end(id);
            return;
        }
        if (target == null) {
            end(id);
            plugin.send(player, "no-location", "attempts", s.rule(kind).maxAttempts());
            s.sound("fail").play(player);
            return;
        }
        // Kiểm tra lại lần cuối: thế giới có thể vừa bị khoá trong lúc đang tìm
        if (!s.rule(kind).enabled()) {
            end(id);
            plugin.send(player, "locked", "world", kind.displayName);
            s.sound("locked").play(player);
            return;
        }

        teleport(player, target).thenAccept(ok -> player.getScheduler().run(plugin, task -> {
            end(id);
            Settings now = plugin.settings();
            if (ok) {
                if (now.cooldownSeconds() > 0) {
                    cooldowns.put(id, System.currentTimeMillis() + now.cooldownSeconds() * 1000L);
                }
                plugin.send(player, "success", "world", kind.displayName,
                        "x", target.getBlockX(), "y", target.getBlockY(), "z", target.getBlockZ());
                now.sound("success").play(player);
            } else {
                plugin.send(player, "teleport-failed");
                now.sound("fail").play(player);
            }
        }, () -> end(id)));
    }

    /** Dịch chuyển bằng teleportAsync của Folia, gọi từ thread của người chơi. */
    CompletableFuture<Boolean> teleport(Player player, Location target) {
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            try {
                player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .whenComplete((ok, error) -> out.complete(error == null && Boolean.TRUE.equals(ok)));
            } catch (Throwable t) {
                plugin.fail("teleport", t);
                out.complete(false);
            }
        }, () -> out.complete(false));
        return out;
    }
}
