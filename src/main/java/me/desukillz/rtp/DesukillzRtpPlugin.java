package me.desukillz.rtp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** DesukillzRTP: RTP bằng GUI cho Folia, khoá thế giới theo config, tìm chỗ an toàn theo từng dimension. */
public final class DesukillzRtpPlugin extends JavaPlugin {

    private volatile Settings settings;
    private RtpService rtp;
    private QueueManager queue;

    /** GUI đang mở của từng người chơi, dùng để vẽ lại khi reload. */
    private final Map<UUID, RtpGui> openGuis = new ConcurrentHashMap<>();
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig(), getLogger());
        rtp = new RtpService(this);
        queue = new QueueManager(this);

        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        if (getCommand("rtp") != null) {
            getCommand("rtp").setExecutor(new RtpCommand(this));
        }
        if (getCommand("rtpq") != null) {
            getCommand("rtpq").setExecutor(new RtpqCommand(this));
        }
        reportWorlds();
        getLogger().info("DesukillzRTP đã bật.");
    }

    @Override
    public void onDisable() {
        if (queue != null) {
            queue.shutdown();
        }
        // Đóng GUI còn mở để không ai lấy được đồ trong GUI khi plugin đã tắt (cố gắng hết sức)
        for (RtpGui gui : new ArrayList<>(openGuis.values())) {
            for (var viewer : new ArrayList<>(gui.getInventory().getViewers())) {
                try {
                    viewer.closeInventory();
                } catch (Throwable ignored) {
                    // Folia có thể từ chối đóng khác thread khi tắt server, người chơi sẽ bị kick/đóng tự nhiên
                }
            }
        }
        openGuis.clear();
    }

    // ------------------------------------------------------------------ truy cập

    Settings settings() {
        return settings;
    }

    RtpService rtp() {
        return rtp;
    }

    QueueManager queue() {
        return queue;
    }

    // ------------------------------------------------------------------ GUI

    /** Mở GUI cho người chơi. Gọi trên thread của người chơi. */
    void openGui(Player player) {
        Settings s = settings;
        if (!player.hasPermission("desukillz.rtp.use")) {
            send(player, "no-permission");
            s.sound("fail").play(player);
            return;
        }
        if (queue.inMatch(player.getUniqueId()) && !s.queue().allowRtpDuringMatch()) {
            send(player, "in-match");
            s.sound("fail").play(player);
            return;
        }
        RtpGui gui = new RtpGui(s);
        openGuis.put(player.getUniqueId(), gui);
        if (player.openInventory(gui.getInventory()) == null) {
            openGuis.remove(player.getUniqueId(), gui);
            return;
        }
        s.sound("open").play(player);
    }

    void forgetGui(UUID id, RtpGui gui) {
        openGuis.remove(id, gui);
    }

    void forgetGui(UUID id) {
        openGuis.remove(id);
    }

    // ------------------------------------------------------------------ reload

    /** Tải lại config.yml và vẽ lại mọi GUI đang mở (mỗi GUI trên thread của người xem). */
    synchronized void reload() {
        reloadConfig();
        Settings fresh = Settings.load(getConfig(), getLogger());
        settings = fresh;
        reportWorlds();
        for (Map.Entry<UUID, RtpGui> e : openGuis.entrySet()) {
            RtpGui gui = e.getValue();
            onPlayer(e.getKey(), p -> gui.render(fresh));
        }
    }

    private void reportWorlds() {
        for (WorldKind kind : WorldKind.values()) {
            Settings.WorldRule rule = settings.rule(kind);
            if (!rule.enabled()) {
                continue;
            }
            if (Bukkit.getWorld(rule.worldName()) == null) {
                getLogger().warning(kind.displayName + " đang được mở nhưng thế giới '" + rule.worldName()
                        + "' chưa được tải. Người chơi sẽ nhận thông báo lỗi khi RTP.");
            }
        }
    }

    // ------------------------------------------------------------------ tiện ích

    /** Tin nhắn có tiền tố. */
    String prefixed(String key, Object... pairs) {
        Settings s = settings;
        return s.message("prefix") + Text.format(s.message(key), pairs);
    }

    void send(Player player, String key, Object... pairs) {
        player.sendMessage(Text.color(prefixed(key, pairs)));
    }

    /** Chạy hành động trên thread sở hữu người chơi (an toàn với Folia). Bỏ qua nếu họ đã offline. */
    void onPlayer(UUID id, Consumer<Player> action) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        player.getScheduler().run(this, task -> {
            try {
                action.accept(player);
            } catch (Throwable t) {
                fail("on-player", t);
            }
        }, null);
    }

    /** Ghi lỗi đầy đủ một lần cho mỗi module để tránh spam console. */
    void fail(String module, Throwable t) {
        if (reportedFailures.add(module)) {
            getLogger().log(java.util.logging.Level.SEVERE, "Lỗi trong " + module + " (chỉ báo một lần)", t);
        } else {
            getLogger().warning("Lỗi lặp lại trong " + module + ": " + t);
        }
    }
}
