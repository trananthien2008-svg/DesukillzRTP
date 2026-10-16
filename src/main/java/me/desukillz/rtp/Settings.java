package me.desukillz.rtp;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Toàn bộ cấu hình đã đọc từ config.yml. Bất biến: khi reload thì thay cả object. */
record Settings(
        Map<WorldKind, WorldRule> rules,
        int cooldownSeconds,
        String guiTitle,
        boolean fillerEnabled,
        Material filler,
        Map<WorldKind, GuiItem> items,
        Map<String, SoundSpec> sounds,
        QueueRule queue,
        Map<String, String> messages) {

    record WorldRule(WorldKind kind, boolean enabled, String worldName, int minRadius, int maxRadius,
                     int centerX, int centerZ, int minY, int maxY, int maxAttempts) {
    }

    record GuiItem(int slot, Material material, String name, List<String> lore,
                   Material lockedMaterial, String lockedName, List<String> lockedLore) {
    }

    record QueueRule(boolean enabled, WorldKind kind, int pairDistance, int timeoutSeconds,
                     boolean allowRtpDuringMatch) {
    }

    record SoundSpec(String key, float volume, float pitch) {
        /** Phát cho riêng người chơi này. Phải gọi trên thread của người chơi. */
        void play(Player player) {
            if (key == null || key.isBlank()) {
                return;
            }
            player.playSound(player.getLocation(), key, volume, pitch);
        }
    }

    WorldRule rule(WorldKind kind) {
        return rules.get(kind);
    }

    GuiItem item(WorldKind kind) {
        return items.get(kind);
    }

    SoundSpec sound(String name) {
        return sounds.get(name);
    }

    String message(String key) {
        String m = messages.get(key);
        return m != null ? m : "&c[thiếu tin nhắn: " + key + "]";
    }

    static Settings load(FileConfiguration c, Logger log) {
        Map<WorldKind, WorldRule> rules = new EnumMap<>(WorldKind.class);
        Map<WorldKind, GuiItem> items = new EnumMap<>(WorldKind.class);
        for (WorldKind kind : WorldKind.values()) {
            rules.put(kind, readRule(c, kind));
            items.put(kind, readItem(c, kind, log));
        }

        Map<String, SoundSpec> sounds = new HashMap<>();
        for (String name : List.of("open", "select", "locked", "success", "fail", "close")) {
            String path = "sounds." + name;
            sounds.put(name, new SoundSpec(
                    c.getString(path + ".sound", ""),
                    (float) c.getDouble(path + ".volume", 1.0),
                    (float) c.getDouble(path + ".pitch", 1.0)));
        }

        WorldKind queueKind = WorldKind.parse(c.getString("queue.world", "overworld"));
        QueueRule queue = new QueueRule(
                c.getBoolean("queue.enabled", true),
                queueKind != null ? queueKind : WorldKind.OVERWORLD,
                Math.max(2, c.getInt("queue.pair-distance", 8)),
                Math.max(10, c.getInt("queue.match-timeout-seconds", 600)),
                c.getBoolean("queue.allow-rtp-during-match", false));

        Map<String, String> messages = new HashMap<>();
        // Khóa tin nhắn bị thiếu trong config của người dùng sẽ lấy từ config.yml mặc định trong jar
        Configuration defaults = c.getDefaults();
        if (defaults != null) {
            ConfigurationSection def = defaults.getConfigurationSection("messages");
            if (def != null) {
                for (String key : def.getKeys(false)) {
                    messages.put(key, def.getString(key, ""));
                }
            }
        }
        ConfigurationSection section = c.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                messages.put(key, section.getString(key, ""));
            }
        }

        String color = c.getString("gui.filler-color", "BLACK").toUpperCase(Locale.ROOT);
        Material filler = Material.matchMaterial(color + "_STAINED_GLASS_PANE");
        if (filler == null) {
            log.warning("gui.filler-color không hợp lệ: " + color + ", dùng BLACK.");
            filler = Material.BLACK_STAINED_GLASS_PANE;
        }

        return new Settings(rules,
                Math.max(0, c.getInt("cooldown-seconds", 60)),
                c.getString("gui.title", "&b&lCHỌN THẾ GIỚI RTP"),
                c.getBoolean("gui.filler-enabled", true),
                filler, items, sounds, queue, messages);
    }

    private static WorldRule readRule(FileConfiguration c, WorldKind kind) {
        String p = "worlds." + kind.key + ".";
        int minRadius = Math.max(0, c.getInt(p + "min-radius", defaultMin(kind)));
        int maxRadius = Math.max(minRadius + 1, c.getInt(p + "max-radius", defaultMax(kind)));
        int minY = c.getInt(p + "min-y", kind == WorldKind.NETHER ? 33 : 5);
        int maxY = Math.max(minY + 4, c.getInt(p + "max-y", kind == WorldKind.NETHER ? 120 : 100));
        return new WorldRule(kind,
                // Chỉ Overworld mặc định mở; Nether và The End mặc định khoá
                c.getBoolean(p + "enabled", kind == WorldKind.OVERWORLD),
                c.getString(p + "world-name", kind.defaultWorldName),
                minRadius, maxRadius,
                c.getInt(p + "center-x", 0), c.getInt(p + "center-z", 0),
                minY, maxY,
                Math.max(1, c.getInt(p + "max-attempts", 30)));
    }

    private static int defaultMin(WorldKind kind) {
        return switch (kind) {
            case OVERWORLD -> 500;
            case NETHER -> 100;
            case END -> 55;
        };
    }

    private static int defaultMax(WorldKind kind) {
        return switch (kind) {
            case OVERWORLD -> 5000;
            case NETHER -> 2000;
            case END -> 70;
        };
    }

    private static GuiItem readItem(FileConfiguration c, WorldKind kind, Logger log) {
        String p = "gui.items." + kind.key + ".";
        Material fallback = switch (kind) {
            case OVERWORLD -> Material.GRASS_BLOCK;
            case NETHER -> Material.NETHERRACK;
            case END -> Material.END_STONE;
        };
        int slot = c.getInt(p + "slot", switch (kind) {
            case OVERWORLD -> 11;
            case NETHER -> 13;
            case END -> 15;
        });
        if (slot < 0 || slot > 26) {
            log.warning(p + "slot phải từ 0 đến 26, dùng ô mặc định.");
            slot = switch (kind) {
                case OVERWORLD -> 11;
                case NETHER -> 13;
                case END -> 15;
            };
        }
        Material material = material(c.getString(p + "material"), fallback, log, p + "material");
        Material locked = material(c.getString(p + "locked-material"), material, log, p + "locked-material");
        return new GuiItem(slot, material,
                c.getString(p + "name", kind.displayName),
                c.getStringList(p + "lore"),
                locked,
                c.getString(p + "locked-name", kind.displayName + " ĐÃ BỊ KHÓA"),
                c.getStringList(p + "locked-lore"));
    }

    private static Material material(String name, Material fallback, Logger log, String path) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            log.warning(path + " không hợp lệ: " + name + ", dùng " + fallback.name() + ".");
            return fallback;
        }
        return m;
    }
}
