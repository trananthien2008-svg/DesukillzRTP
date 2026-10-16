package me.desukillz.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;

import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Tìm vị trí ngẫu nhiên an toàn mà không chặn luồng chính và đúng luồng của Folia:
 * <ol>
 *   <li>chọn toạ độ ngẫu nhiên (bất kỳ thread nào);</li>
 *   <li>nạp chunk bằng {@code getChunkAtAsync};</li>
 *   <li>chuyển sang thread của region sở hữu chunk đó rồi mới đọc block.</li>
 * </ol>
 */
final class LocationFinder {

    /** Nguồn toạ độ ứng viên: trả về {x, z}. */
    interface Candidates {
        int[] next();
    }

    private final DesukillzRtpPlugin plugin;

    LocationFinder(DesukillzRtpPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return future hoàn thành với vị trí, hoặc null nếu không tìm được / bị huỷ */
    CompletableFuture<Location> find(World world, Settings.WorldRule rule, Candidates candidates,
                                     BooleanSupplier cancelled) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        attempt(world, rule, candidates, cancelled, 1, result);
        return result;
    }

    private void attempt(World world, Settings.WorldRule rule, Candidates candidates, BooleanSupplier cancelled,
                         int attempt, CompletableFuture<Location> result) {
        if (result.isDone()) {
            return;
        }
        if (cancelled.getAsBoolean() || attempt > rule.maxAttempts()) {
            result.complete(null);
            return;
        }

        int[] c = candidates.next();
        int x = c[0];
        int z = c[1];
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        CompletableFuture<Chunk> chunk;
        try {
            chunk = world.getChunkAtAsync(chunkX, chunkZ, true);
        } catch (Throwable t) {
            plugin.fail("finder-chunk", t);
            result.complete(null);
            return;
        }

        chunk.whenComplete((loaded, error) -> {
            if (error != null || loaded == null) {
                attempt(world, rule, candidates, cancelled, attempt + 1, result);
                return;
            }
            try {
                // Đọc block chỉ được phép trên thread của region sở hữu chunk
                Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                    Location found = null;
                    try {
                        found = evaluate(world, rule, x, z);
                    } catch (Throwable t) {
                        plugin.fail("finder-evaluate", t);
                    }
                    if (found != null) {
                        result.complete(found);
                    } else {
                        attempt(world, rule, candidates, cancelled, attempt + 1, result);
                    }
                });
            } catch (Throwable t) {
                plugin.fail("finder-schedule", t);
                result.complete(null);
            }
        });
    }

    /** Chạy trên thread của region. */
    private static Location evaluate(World world, Settings.WorldRule rule, int x, int z) {
        if (!insideBorder(world, x, z)) {
            return null;
        }
        OptionalInt feet = SafeSpot.find(rule.kind(), new WorldBlocks(world), x, z, rule.minY(), rule.maxY());
        if (feet.isEmpty()) {
            return null;
        }
        return new Location(world, x + 0.5, feet.getAsInt(), z + 0.5);
    }

    /** Cách biên giới thế giới ít nhất 6 block ở cả bốn góc. */
    private static boolean insideBorder(World world, int x, int z) {
        WorldBorder border = world.getWorldBorder();
        int margin = 6;
        for (int dx : new int[]{-margin, margin}) {
            for (int dz : new int[]{-margin, margin}) {
                if (!border.isInside(new Location(world, x + dx + 0.5, 64, z + dz + 0.5))) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ chọn toạ độ

    /** Điểm ngẫu nhiên trong hình vành khuyên quanh tâm của thế giới (phân bố đều theo diện tích). */
    static int[] ring(Settings.WorldRule rule) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double min = rule.minRadius();
        double max = rule.maxRadius();
        double radius = Math.sqrt(rnd.nextDouble() * (max * max - min * min) + min * min);
        double angle = rnd.nextDouble() * Math.PI * 2;
        return normalize(rule.centerX() + (int) Math.round(radius * Math.cos(angle)),
                rule.centerZ() + (int) Math.round(radius * Math.sin(angle)));
    }

    /** Điểm ngẫu nhiên cách (cx, cz) từ minDistance đến maxDistance block. */
    static int[] around(int cx, int cz, int minDistance, int maxDistance) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double radius = minDistance + rnd.nextDouble() * Math.max(1, maxDistance - minDistance);
        double angle = rnd.nextDouble() * Math.PI * 2;
        return normalize(cx + (int) Math.round(radius * Math.cos(angle)),
                cz + (int) Math.round(radius * Math.sin(angle)));
    }

    /** Đẩy toạ độ vào giữa chunk (cách mép 2 block) để mọi phép đọc nằm trong một chunk. */
    static int[] normalize(int x, int z) {
        return new int[]{inChunk(x), inChunk(z)};
    }

    private static int inChunk(int v) {
        int local = Math.max(2, Math.min(13, Math.floorMod(v, 16)));
        return Math.floorDiv(v, 16) * 16 + local;
    }

    // ------------------------------------------------------------------ truy cập block thật

    private static final class WorldBlocks implements SafeSpot.Blocks {
        private final World world;

        WorldBlocks(World world) {
            this.world = world;
        }

        @Override
        public SafeSpot.Info at(int x, int y, int z) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            return new SafeSpot.Info(type.name(), type.isSolid(), block.isPassable(), block.isLiquid());
        }

        @Override
        public int highest(int x, int z) {
            return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        }

        @Override
        public int minY() {
            return world.getMinHeight();
        }

        @Override
        public int maxY() {
            return world.getMaxHeight();
        }
    }
}
