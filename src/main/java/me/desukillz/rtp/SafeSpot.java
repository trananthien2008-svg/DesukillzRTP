package me.desukillz.rtp;

import java.util.OptionalInt;
import java.util.Set;

/**
 * Quy tắc chọn chỗ đứng an toàn theo từng dimension. Không phụ thuộc Bukkit để có thể kiểm thử riêng.
 *
 * <p>Mọi truy cập block đều nằm trong phạm vi +-2 quanh (x, z) và LocationFinder luôn chọn (x, z)
 * cách mép chunk ít nhất 2 block, nên toàn bộ phép đọc nằm trong đúng một chunk
 * (quan trọng với Folia: mỗi chunk thuộc một region/thread).
 */
final class SafeSpot {

    /** Thông tin tối thiểu của một block. */
    record Info(String name, boolean solid, boolean passable, boolean liquid) {
    }

    interface Blocks {
        Info at(int x, int y, int z);

        /** Y của block cao nhất chặn chuyển động (không tính lá cây). */
        int highest(int x, int z);

        int minY();

        int maxY();
    }

    /** Block gây sát thương hoặc đẩy người chơi đi chỗ khác. */
    static final Set<String> HAZARDS = Set.of(
            "LAVA", "FIRE", "SOUL_FIRE", "MAGMA_BLOCK", "CACTUS", "CAMPFIRE", "SOUL_CAMPFIRE",
            "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW", "COBWEB", "TNT", "POINTED_DRIPSTONE",
            "LAVA_CAULDRON", "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY", "BUBBLE_COLUMN");

    /** Block không nên đứng lên. */
    static final Set<String> BAD_GROUND = Set.of("BEDROCK", "END_PORTAL_FRAME", "DRAGON_EGG");

    private SafeSpot() {
    }

    /** @return Y của chân người chơi, hoặc empty nếu cột block này không an toàn */
    static OptionalInt find(WorldKind kind, Blocks w, int x, int z, int minY, int maxY) {
        return switch (kind) {
            case OVERWORLD -> overworld(w, x, z);
            case NETHER -> nether(w, x, z, minY, maxY);
            case END -> end(w, x, z, minY, maxY);
        };
    }

    // ------------------------------------------------------------------ Overworld

    private static OptionalInt overworld(Blocks w, int x, int z) {
        int ground = w.highest(x, z);
        if (ground <= w.minY() || ground + 3 >= w.maxY()) {
            return OptionalInt.empty();
        }
        // Nếu mặt trên cùng là nước hoặc dung nham thì Info.liquid = true nên bị loại ở đây
        if (!goodGround(w.at(x, ground, z))) {
            return OptionalInt.empty();
        }
        int feet = ground + 1;
        if (!clear(w.at(x, feet, z)) || !clear(w.at(x, feet + 1, z))) {
            return OptionalInt.empty();
        }
        // Không có block nguy hiểm trong vùng 3x3 quanh chỗ đứng
        if (hazardNear(w, x, z, 1, ground, feet + 1)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(feet);
    }

    // ------------------------------------------------------------------ Nether

    private static OptionalInt nether(Blocks w, int x, int z, int minY, int maxY) {
        int lo = Math.max(minY, w.minY() + 1);
        int hi = Math.min(maxY, w.maxY() - 3);
        for (int y = hi; y >= lo; y--) {
            if (!goodGround(w.at(x, y - 1, z))) {
                continue;
            }
            if (!clear(w.at(x, y, z)) || !clear(w.at(x, y + 1, z))) {
                continue;
            }
            // Tránh hồ dung nham, lửa, magma: quét bán kính 2, từ 3 block dưới chân đến 2 block trên đầu
            if (hazardNear(w, x, z, 2, y - 3, y + 2)) {
                continue;
            }
            return OptionalInt.of(y);
        }
        return OptionalInt.empty();
    }

    // ------------------------------------------------------------------ The End

    private static OptionalInt end(Blocks w, int x, int z, int minY, int maxY) {
        int lo = Math.max(minY, w.minY() + 3);
        int hi = Math.min(maxY, w.maxY() - 3);
        for (int y = hi; y >= lo; y--) {
            if (!goodEndGround(w.at(x, y - 1, z))) {
                continue;
            }
            if (!clear(w.at(x, y, z)) || !clear(w.at(x, y + 1, z))) {
                continue;
            }
            // Nền chắc chắn phía dưới, không phải mặt mỏng lơ lửng
            if (!w.at(x, y - 2, z).solid() || !w.at(x, y - 3, z).solid()) {
                continue;
            }
            // Tránh mép đảo và void: mọi cột trong vùng 5x5 đều phải có nền ngay dưới chân (tối đa lệch 1 block)
            if (!islandInterior(w, x, y, z)) {
                continue;
            }
            return OptionalInt.of(y);
        }
        return OptionalInt.empty();
    }

    private static boolean islandInterior(Blocks w, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean floor = goodEndGround(w.at(x + dx, y - 1, z + dz))
                        || goodEndGround(w.at(x + dx, y - 2, z + dz));
                if (!floor || !clear(w.at(x + dx, y, z + dz)) || !clear(w.at(x + dx, y + 1, z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ chung

    /** Chân / đầu người chơi: không đặc, không chất lỏng, không nguy hiểm. */
    static boolean clear(Info i) {
        return i.passable() && !i.liquid() && !HAZARDS.contains(i.name());
    }

    /** Nền đứng: đặc, không phải chất lỏng, không nguy hiểm, không phải bedrock / cổng. */
    static boolean goodGround(Info i) {
        return i.solid() && !i.liquid() && !HAZARDS.contains(i.name()) && !BAD_GROUND.contains(i.name());
    }

    /** Ở The End không đứng trên obsidian (cột trụ của đảo chính). */
    static boolean goodEndGround(Info i) {
        return goodGround(i) && !i.name().equals("OBSIDIAN");
    }

    /** Có block nguy hiểm nào trong hình hộp (bán kính ngang r, từ yFrom đến yTo) quanh (x, z) không. */
    static boolean hazardNear(Blocks w, int x, int z, int r, int yFrom, int yTo) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = yFrom; y <= yTo; y++) {
                    if (HAZARDS.contains(w.at(x + dx, y, z + dz).name())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
