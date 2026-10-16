package me.desukillz.rtp;

import org.bukkit.World;

import java.util.Locale;

/** Ba loại thế giới có thể RTP. */
enum WorldKind {
    OVERWORLD("overworld", "Overworld", World.Environment.NORMAL, "world"),
    NETHER("nether", "Nether", World.Environment.NETHER, "world_nether"),
    END("end", "The End", World.Environment.THE_END, "world_the_end");

    final String key;
    final String displayName;
    final World.Environment environment;
    final String defaultWorldName;

    WorldKind(String key, String displayName, World.Environment environment, String defaultWorldName) {
        this.key = key;
        this.displayName = displayName;
        this.environment = environment;
        this.defaultWorldName = defaultWorldName;
    }

    String permission() {
        return "desukillz.rtp.world." + key;
    }

    /** Hiểu các cách gõ phổ biến: overworld/ow/world, nether, end/the_end. */
    static WorldKind parse(String input) {
        if (input == null) {
            return null;
        }
        switch (input.toLowerCase(Locale.ROOT)) {
            case "overworld", "ow", "world", "normal":
                return OVERWORLD;
            case "nether", "hell":
                return NETHER;
            case "end", "the_end", "theend":
                return END;
            default:
                return null;
        }
    }
}
