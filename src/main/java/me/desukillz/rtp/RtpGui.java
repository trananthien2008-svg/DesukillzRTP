package me.desukillz.rtp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI chọn thế giới (27 ô). Được nhận diện bằng chính lớp InventoryHolder này
 * chứ không dựa vào tiêu đề, nên không GUI nào khác (kể cả cùng tiêu đề) bị xử lý nhầm.
 */
final class RtpGui implements InventoryHolder {

    private final Inventory inventory;
    private volatile Map<Integer, WorldKind> slots = Map.of();

    /** Đặt true trước khi plugin tự đóng GUI để không phát âm thanh đóng. */
    volatile boolean closedByPlugin;

    RtpGui(Settings settings) {
        this.inventory = Bukkit.createInventory(this, 27, Text.color(settings.guiTitle()));
        render(settings);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Thế giới ở ô này, hoặc null nếu là ô trang trí / ô trống. */
    WorldKind kindAt(int slot) {
        return slots.get(slot);
    }

    /** Vẽ lại GUI theo cấu hình hiện tại (dùng khi mở và khi reload). */
    void render(Settings settings) {
        inventory.clear();

        if (settings.fillerEnabled()) {
            ItemStack filler = new ItemStack(settings.filler());
            ItemMeta meta = filler.getItemMeta();
            meta.displayName(Text.color(" "));
            filler.setItemMeta(meta);
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        Map<Integer, WorldKind> map = new HashMap<>();
        for (WorldKind kind : WorldKind.values()) {
            Settings.GuiItem gi = settings.item(kind);
            boolean unlocked = settings.rule(kind).enabled();

            Material material = unlocked ? gi.material() : gi.lockedMaterial();
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.color(unlocked ? gi.name() : gi.lockedName()));
            List<Component> lore = new ArrayList<>();
            for (String line : unlocked ? gi.lore() : gi.lockedLore()) {
                lore.add(Text.color(line));
            }
            meta.lore(lore);
            item.setItemMeta(meta);

            inventory.setItem(gi.slot(), item);
            map.put(gi.slot(), kind);
        }
        this.slots = Map.copyOf(map);
    }
}
