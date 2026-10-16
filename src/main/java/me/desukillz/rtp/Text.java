package me.desukillz.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Chuyển chuỗi dùng mã màu '&' thành Component, tắt chữ nghiêng mặc định của tên/lore vật phẩm. */
    static Component color(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** Thay {khoá} bằng giá trị. Tham số truyền theo cặp: "khoá", giá trị, "khoá", giá trị... */
    static String format(String template, Object... pairs) {
        String result = template;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return result;
    }
}
