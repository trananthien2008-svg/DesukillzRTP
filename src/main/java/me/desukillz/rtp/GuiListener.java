package me.desukillz.rtp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Bảo vệ GUI: huỷ mọi thao tác trong GUI của plugin (click trái/phải, shift-click, phím số,
 * double-click, kéo thả, thả đồ, đổi tay phụ...) và chỉ xử lý click trái/phải/shift vào đúng ô thế giới.
 * Chỉ động vào GUI của DesukillzRTP (nhận diện bằng InventoryHolder), không chặn GUI khác.
 */
final class GuiListener implements Listener {

    private final DesukillzRtpPlugin plugin;

    GuiListener(DesukillzRtpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        // getInventory() là inventory phía trên (GUI nếu đang mở GUI của plugin)
        if (!(event.getInventory().getHolder() instanceof RtpGui gui)) {
            return;
        }
        // Huỷ tất cả, kể cả shift-click từ túi đồ của người chơi vào GUI
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != event.getInventory()) {
            return; // click vào túi đồ bên dưới
        }
        ClickType type = event.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT
                && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
            return; // phím số, double-click, thả đồ... chỉ bị huỷ, không kích hoạt gì
        }

        WorldKind kind = gui.kindAt(event.getRawSlot());
        if (kind == null) {
            return; // ô trang trí
        }
        try {
            plugin.rtp().request(player, kind, gui);
        } catch (Throwable t) {
            plugin.fail("gui-click", t);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RtpGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RtpGui gui)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            plugin.forgetGui(player.getUniqueId(), gui);
            if (!gui.closedByPlugin) {
                plugin.settings().sound("close").play(player);
            }
        }
    }
}
