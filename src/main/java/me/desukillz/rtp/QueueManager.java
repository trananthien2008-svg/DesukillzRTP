package me.desukillz.rtp;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hàng chờ PvP độc lập với GUI RTP: /rtpq vào hàng, đủ hai người thì ghép cặp,
 * dịch chuyển hai người đến gần nhau trong thế giới cấu hình ở {@code queue.world}.
 *
 * <p>Mọi cấu trúc dữ liệu đều an toàn đa luồng vì sự kiện đến từ nhiều region của Folia.
 */
final class QueueManager {

    private static final class Match {
        final UUID a;
        final UUID b;
        final String nameA;
        final String nameB;
        final AtomicBoolean ended = new AtomicBoolean();
        volatile boolean active;
        volatile ScheduledTask timeout;

        Match(UUID a, String nameA, UUID b, String nameB) {
            this.a = a;
            this.nameA = nameA;
            this.b = b;
            this.nameB = nameB;
        }

        UUID other(UUID id) {
            return id.equals(a) ? b : a;
        }

        String nameOf(UUID id) {
            return id.equals(a) ? nameA : nameB;
        }
    }

    private final DesukillzRtpPlugin plugin;
    private final Object lock = new Object();
    private final Map<UUID, String> waiting = new LinkedHashMap<>();
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();

    QueueManager(DesukillzRtpPlugin plugin) {
        this.plugin = plugin;
    }

    boolean inMatch(UUID id) {
        return matches.containsKey(id);
    }

    // ------------------------------------------------------------------ lệnh

    /** Gọi trên thread của người chơi. */
    void join(Player player) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        Settings.WorldRule rule = s.rule(s.queue().kind());

        if (!s.queue().enabled()) {
            plugin.send(player, "queue-disabled");
            s.sound("fail").play(player);
            return;
        }
        if (!rule.enabled()) {
            plugin.send(player, "locked", "world", rule.kind().displayName);
            s.sound("locked").play(player);
            return;
        }
        if (Bukkit.getWorld(rule.worldName()) == null) {
            plugin.send(player, "world-missing", "world", rule.worldName());
            s.sound("fail").play(player);
            return;
        }
        if (matches.containsKey(id)) {
            plugin.send(player, "in-match");
            s.sound("fail").play(player);
            return;
        }
        if (plugin.rtp().isBusy(id)) {
            plugin.send(player, "queue-busy");
            s.sound("fail").play(player);
            return;
        }
        synchronized (lock) {
            if (waiting.containsKey(id)) {
                plugin.send(player, "queue-already");
                s.sound("fail").play(player);
                return;
            }
            waiting.put(id, player.getName());
        }
        plugin.send(player, "queue-joined");
        s.sound("select").play(player);
        pairIfPossible();
    }

    /** Gọi trên thread của người chơi. */
    void leave(Player player) {
        Settings s = plugin.settings();
        UUID id = player.getUniqueId();
        boolean removed;
        synchronized (lock) {
            removed = waiting.remove(id) != null;
        }
        if (removed) {
            plugin.send(player, "queue-left");
            s.sound("close").play(player);
        } else if (matches.containsKey(id)) {
            plugin.send(player, "queue-in-match");
            s.sound("fail").play(player);
        } else {
            plugin.send(player, "queue-not-in");
            s.sound("fail").play(player);
        }
    }

    // ------------------------------------------------------------------ sự kiện

    /** Người chơi thoát server (bất kỳ thread nào). */
    void onQuit(UUID id) {
        synchronized (lock) {
            waiting.remove(id);
        }
        Match m = matches.get(id);
        if (m == null || !m.ended.compareAndSet(false, true)) {
            return;
        }
        cleanup(m);
        UUID winner = m.other(id);
        if (m.active) {
            plugin.onPlayer(winner, p -> {
                plugin.send(p, "queue-opponent-left", "player", m.nameOf(id));
                plugin.settings().sound("success").play(p);
            });
        } else {
            plugin.onPlayer(winner, p -> {
                plugin.send(p, "queue-failed");
                plugin.settings().sound("fail").play(p);
            });
        }
    }

    /** Người chơi chết (chạy trên thread của người đó). */
    void onDeath(UUID id) {
        Match m = matches.get(id);
        if (m == null || !m.active || !m.ended.compareAndSet(false, true)) {
            return;
        }
        cancelTimeout(m);
        cleanup(m);
        UUID winner = m.other(id);
        plugin.onPlayer(winner, p -> {
            plugin.send(p, "queue-win", "player", m.nameOf(id));
            plugin.settings().sound("success").play(p);
        });
        plugin.onPlayer(id, p -> {
            plugin.send(p, "queue-lose", "player", m.nameOf(winner));
            plugin.settings().sound("fail").play(p);
        });
    }

    /** Tắt plugin: huỷ hết hẹn giờ và dọn dữ liệu. */
    void shutdown() {
        for (Match m : new ArrayList<>(matches.values())) {
            m.ended.set(true);
            cancelTimeout(m);
        }
        matches.clear();
        synchronized (lock) {
            waiting.clear();
        }
    }

    // ------------------------------------------------------------------ ghép cặp

    private void pairIfPossible() {
        List<Match> created = new ArrayList<>();
        synchronized (lock) {
            while (waiting.size() >= 2) {
                Iterator<Map.Entry<UUID, String>> it = waiting.entrySet().iterator();
                Map.Entry<UUID, String> first = it.next();
                UUID a = first.getKey();
                String nameA = first.getValue();
                it.remove();
                Map.Entry<UUID, String> second = it.next();
                UUID b = second.getKey();
                String nameB = second.getValue();
                it.remove();

                Match m = new Match(a, nameA, b, nameB);
                matches.put(a, m);
                matches.put(b, m);
                created.add(m);
            }
        }
        for (Match m : created) {
            startMatch(m);
        }
    }

    private void startMatch(Match m) {
        RtpService rtp = plugin.rtp();
        RtpService.Session sa = rtp.tryBegin(m.a);
        RtpService.Session sb = rtp.tryBegin(m.b);
        if (sa == null || sb == null) {
            // Có người vừa bắt đầu RTP riêng: người bận bị loại khỏi hàng, người còn lại xếp lại
            m.ended.set(true);
            matches.remove(m.a, m);
            matches.remove(m.b, m);
            if (sa != null) {
                rtp.end(m.a);
            }
            if (sb != null) {
                rtp.end(m.b);
            }
            requeueOrBusy(m.a, sa != null, m);
            requeueOrBusy(m.b, sb != null, m);
            pairIfPossible();
            return;
        }

        Settings s = plugin.settings();
        Settings.WorldRule rule = s.rule(s.queue().kind());
        World world = Bukkit.getWorld(rule.worldName());
        if (!rule.enabled() || world == null || world.getEnvironment() != rule.kind().environment) {
            failSetup(m);
            return;
        }

        plugin.onPlayer(m.a, p -> {
            plugin.send(p, "queue-found", "player", m.nameB);
            plugin.settings().sound("select").play(p);
        });
        plugin.onPlayer(m.b, p -> {
            plugin.send(p, "queue-found", "player", m.nameA);
            plugin.settings().sound("select").play(p);
        });

        LocationFinder finder = rtp.finder();
        finder.find(world, rule, () -> LocationFinder.ring(rule), m.ended::get).whenComplete((locA, err) -> {
            if (err != null || locA == null || m.ended.get()) {
                failSetup(m);
                return;
            }
            // Người thứ hai: tìm gần người thứ nhất, ít lần thử hơn
            Settings.WorldRule near = new Settings.WorldRule(rule.kind(), rule.enabled(), rule.worldName(),
                    rule.minRadius(), rule.maxRadius(), rule.centerX(), rule.centerZ(), rule.minY(), rule.maxY(),
                    Math.min(15, rule.maxAttempts()));
            int ax = locA.getBlockX();
            int az = locA.getBlockZ();
            int dist = plugin.settings().queue().pairDistance();
            finder.find(world, near, () -> LocationFinder.around(ax, az, dist, dist + 6), m.ended::get)
                    .whenComplete((locB0, err2) -> {
                        // Không tìm được chỗ riêng thì đặt cùng chỗ với người thứ nhất
                        Location locB = (err2 != null || locB0 == null) ? locA.clone() : locB0;
                        teleportBoth(m, locA, locB);
                    });
        });
    }

    private void requeueOrBusy(UUID id, boolean free, Match m) {
        if (free) {
            synchronized (lock) {
                waiting.put(id, m.nameOf(id));
            }
        } else {
            plugin.onPlayer(id, p -> {
                plugin.send(p, "queue-busy");
                plugin.settings().sound("fail").play(p);
            });
        }
    }

    private void teleportBoth(Match m, Location locA, Location locB) {
        Player pa = Bukkit.getPlayer(m.a);
        Player pb = Bukkit.getPlayer(m.b);
        if (m.ended.get() || pa == null || pb == null) {
            failSetup(m);
            return;
        }
        RtpService rtp = plugin.rtp();
        CompletableFuture<Boolean> ta = rtp.teleport(pa, locA);
        CompletableFuture<Boolean> tb = rtp.teleport(pb, locB);
        ta.thenCombine(tb, (x, y) -> x && y).whenComplete((ok, err) -> {
            if (err != null || !Boolean.TRUE.equals(ok)) {
                failSetup(m);
            } else {
                activate(m);
            }
        });
    }

    private void activate(Match m) {
        if (m.ended.get()) {
            return;
        }
        m.active = true;
        plugin.rtp().end(m.a);
        plugin.rtp().end(m.b);

        long ticks = plugin.settings().queue().timeoutSeconds() * 20L;
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> timeout(m), ticks);
        m.timeout = task;
        if (m.ended.get()) {
            cancelTimeout(m);
            return;
        }
        plugin.onPlayer(m.a, p -> {
            plugin.send(p, "queue-started", "player", m.nameB);
            plugin.settings().sound("success").play(p);
        });
        plugin.onPlayer(m.b, p -> {
            plugin.send(p, "queue-started", "player", m.nameA);
            plugin.settings().sound("success").play(p);
        });
    }

    private void timeout(Match m) {
        if (!m.ended.compareAndSet(false, true)) {
            return;
        }
        cleanup(m);
        for (UUID id : new UUID[]{m.a, m.b}) {
            plugin.onPlayer(id, p -> plugin.send(p, "queue-timeout"));
        }
    }

    private void failSetup(Match m) {
        if (!m.ended.compareAndSet(false, true)) {
            return;
        }
        cleanup(m);
        for (UUID id : new UUID[]{m.a, m.b}) {
            plugin.onPlayer(id, p -> {
                plugin.send(p, "queue-failed");
                plugin.settings().sound("fail").play(p);
            });
        }
    }

    /** Gỡ trận khỏi bảng theo dõi; trả phiên RTP nếu trận chưa kích hoạt. */
    private void cleanup(Match m) {
        matches.remove(m.a, m);
        matches.remove(m.b, m);
        if (!m.active) {
            plugin.rtp().end(m.a);
            plugin.rtp().end(m.b);
        }
        cancelTimeout(m);
    }

    private static void cancelTimeout(Match m) {
        ScheduledTask t = m.timeout;
        if (t != null) {
            t.cancel();
        }
    }
}
