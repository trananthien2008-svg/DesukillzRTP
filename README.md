# DesukillzRTP

Plugin **RTP bằng GUI** cho server **Folia** (cũng chạy được trên Paper 1.20+).
`/rtp` mở GUI 27 ô để chọn Overworld / Nether / The End. Thế giới nào bị khoá trong `config.yml` thì **không thể RTP vào**, dù bấm GUI hay gõ lệnh trực tiếp.

- File xuất ra: `DesukillzRTP.jar`
- Java 17+, Folia / Paper 1.20.x trở lên
- Không bắt buộc plugin nào khác. LuckPerms là tuỳ chọn (dùng để phân quyền).

---

## 1. Lệnh

| Lệnh | Tác dụng | Quyền |
|---|---|---|
| `/rtp` | Mở GUI chọn thế giới | `desukillz.rtp.use` |
| `/rtp overworld` · `/rtp nether` · `/rtp end` | RTP thẳng (vẫn bị chặn nếu thế giới đang khoá) | `desukillz.rtp.use` + quyền thế giới |
| `/rtp reload` | Tải lại config, GUI đang mở được vẽ lại ngay | `desukillz.rtp.admin` |
| `/rtpq` | Vào hàng chờ PvP | `desukillz.rtp.queue` |
| `/rtpq leave` | Rời hàng chờ | `desukillz.rtp.queue` |

> **Lưu ý về `/rtpq`:** trong quá trình làm việc trước đây chưa có `/rtpq` nào, nên mình viết một bản **cơ bản mới** (xem mục 7). Nếu bạn đã có plugin `/rtpq` riêng, hãy tắt bằng `queue.enabled: false` để tránh trùng lệnh.

## 2. GUI (27 ô)

- Ô **11**: Overworld (`GRASS_BLOCK`), ô **13**: Nether (`NETHERRACK`), ô **15**: The End (`END_STONE`).
- Các ô còn lại: kính màu (mặc định đen, đổi bằng `gui.filler-color`, tắt bằng `gui.filler-enabled: false`).
- Thế giới đang khoá sẽ hiện tên/lore "ĐÃ BỊ KHÓA" (đổi được trong config); bấm vào sẽ nghe âm thanh khoá và nhận tin nhắn.
- Mọi chữ, vật phẩm, ô, màu đều chỉnh trong `config.yml`.

## 3. Khoá / mở Nether và The End

Mặc định: **Overworld mở, Nether và The End khoá.**

```yaml
worlds:
  overworld:
    enabled: true
  nether:
    enabled: false   # đổi thành true để mở
  end:
    enabled: false   # đổi thành true để mở
```

Mở khoá:
1. Sửa `plugins/DesukillzRTP/config.yml`, đổi `enabled: true` cho thế giới muốn mở.
2. Gõ `/rtp reload` (không cần restart).

Plugin **không bao giờ tự tạo** Nether/End. Nếu tên thế giới trong `world-name` không tồn tại hoặc chưa tải, người chơi nhận thông báo lỗi rõ ràng. Tên mặc định là `world`, `world_nether`, `world_the_end`. Nếu server đặt tên khác, sửa `world-name`.

## 4. Quy tắc tìm vị trí an toàn

| Dimension | Quy tắc |
|---|---|
| Overworld | Đứng trên bề mặt thật (bỏ qua lá cây). Loại nước, dung nham, lửa, xương rồng, magma, tuyết bột, mạng nhện, bedrock...; cần 2 block trống phía trên; không có block nguy hiểm quanh 3x3. |
| Nether | Quét từ `max-y` xuống `min-y` (mặc định 120 → 33) để không rơi vào trần bedrock; loại hồ dung nham, lửa, magma trong bán kính 2. |
| The End | Chỉ chọn nền end stone chắc, không phải obsidian, không ở mép đảo, không trên void (vùng 5x5 quanh chỗ đứng đều phải có nền). |

Chung: tránh ngoài biên giới thế giới (cách ≥ 6 block), mỗi thế giới có `min-radius`, `max-radius`, `max-attempts`. Hết số lần thử mà không có chỗ an toàn thì báo "không tìm được vị trí" và **không** đặt cooldown.

## 5. Cooldown, âm thanh

- `cooldown-seconds` (mặc định 60s). Chỉ tính khi dịch chuyển **thành công**. Quyền `desukillz.rtp.cooldown.bypass` bỏ qua cooldown.
- Âm thanh cấu hình trong `sounds` (mở GUI, chọn, bị khoá, thành công, thất bại, đóng GUI). Dùng tên vanilla dạng `block.note_block.pling`. Để trống `sound: ""` để tắt. Chỉ phát khi có sự kiện tương ứng.

## 6. Phân quyền (LuckPerms)

| Quyền | Mặc định | Ý nghĩa |
|---|---|---|
| `desukillz.rtp.use` | mọi người | Dùng `/rtp` |
| `desukillz.rtp.world.overworld` | mọi người | RTP vào Overworld |
| `desukillz.rtp.world.nether` | mọi người | RTP vào Nether |
| `desukillz.rtp.world.end` | mọi người | RTP vào The End |
| `desukillz.rtp.cooldown.bypass` | OP | Bỏ cooldown |
| `desukillz.rtp.queue` | mọi người | Dùng `/rtpq` |
| `desukillz.rtp.admin` | OP | `/rtp reload` |

Ví dụ: chỉ cho nhóm VIP vào The End (sau khi đã bật `enabled: true`):

```
/lp group default permission set desukillz.rtp.world.end false
/lp group vip permission set desukillz.rtp.world.end true
```

Có hai lớp kiểm soát độc lập: **config** (`enabled`, áp dụng cho tất cả) và **quyền** (áp dụng theo người/nhóm).

## 7. Hàng chờ PvP `/rtpq` (độc lập với GUI)

1. Người chơi gõ `/rtpq` để vào hàng chờ; đủ 2 người sẽ được ghép cặp.
2. Plugin tìm một chỗ an toàn ở `queue.world`, sau đó tìm chỗ thứ hai cách `pair-distance` block (không tìm được thì đặt cùng chỗ), và dịch chuyển cả hai.
3. Trận kết thúc khi một người chết (người kia thắng), một người thoát (người kia thắng) hoặc hết `match-timeout-seconds`.
4. Trong trận, `/rtp` và GUI RTP bị chặn, trừ khi bạn đặt `queue.allow-rtp-during-match: true`.
5. `/rtpq` tôn trọng khoá thế giới: nếu thế giới của hàng chờ đang khoá thì không vào được.

Giới hạn: bản này chỉ ghép cặp và dịch chuyển, **không** có phần thưởng, kit, hay bảng xếp hạng.

## 8. Bảo mật GUI

- GUI nhận diện bằng `InventoryHolder`, không dựa tiêu đề, nên không ảnh hưởng GUI plugin khác.
- Chặn lấy đồ, kéo thả, thả đồ, shift-click, phím số, double-click trong GUI.
- Chỉ chuột trái/phải/shift-click vào ô thế giới mới có tác dụng.
- Khoá thế giới, quyền, cooldown, trận PvP đều **kiểm tra lại phía server** mỗi lần RTP, không tin vào hình hiển thị.

## 9. Tương thích Folia

- Đọc block chỉ chạy trên thread của region sở hữu chunk (`RegionScheduler`), chunk nạp bằng `getChunkAtAsync`.
- Mọi thao tác trên người chơi (gửi tin, âm thanh, mở/đóng GUI, dịch chuyển) chạy trên scheduler riêng của người đó; dịch chuyển bằng `teleportAsync`.
- Không chặn luồng chính, không `Thread.sleep`, không `.join()/.get()`.
- Khi người chơi thoát: huỷ phiên tìm kiếm, xoá cooldown, rời hàng chờ, kết thúc trận.

## 10. Build

Cần JDK 17 và Maven:

```
mvn clean package
```

File ra: `target/DesukillzRTP.jar`.

Không có Maven trên máy? Đẩy thư mục này lên GitHub: workflow `.github/workflows/build.yml` tự build, tải `DesukillzRTP.jar` ở mục **Actions → Artifacts**.

## 11. Cài đặt

1. Chép `DesukillzRTP.jar` vào thư mục `plugins/`.
2. Khởi động server một lần để sinh `plugins/DesukillzRTP/config.yml`.
3. Kiểm tra `world-name` khớp với tên thế giới thật của server.
4. Sửa config theo ý, `/rtp reload`.

## 12. Danh sách kiểm tra trên Folia

- [ ] `/rtp` mở GUI, có tiếng mở; đóng bằng ESC có tiếng đóng.
- [ ] Bấm Overworld: dịch chuyển, có tiếng thành công, cooldown 60s sau đó.
- [ ] Bấm Nether/End khi `enabled: false`: tiếng bass + tin "đã bị khóa", **không** dịch chuyển.
- [ ] `/rtp nether` khi khoá: cũng bị chặn.
- [ ] Đổi `enabled: true`, `/rtp reload` với GUI đang mở: GUI đổi sang trạng thái mở.
- [ ] Đổi `world-name` sai: thông báo "chưa được tải hoặc không tồn tại".
- [ ] Thử lấy đồ, shift-click, phím số, kéo thả trong GUI: tất cả bị chặn.
- [ ] Bấm RTP liên tục: chỉ một lần được xử lý.
- [ ] Thoát giữa lúc đang tìm: không lỗi console.
- [ ] Hai người `/rtpq`: được ghép và đưa đến gần nhau; `/rtp` bị chặn trong trận.
- [ ] Nhiều người RTP cùng lúc ở các vùng khác nhau: không lỗi "wrong thread" trong log.

## 13. Giới hạn trung thực

- Mã được biên dịch thử với bản stub API tự viết và các quy tắc chọn chỗ an toàn có test riêng, **nhưng chưa chạy trên server Folia thật**. Hãy chạy danh sách kiểm tra ở mục 12 trước khi đưa lên server chính.
- Nether/End có địa hình phức tạp nên có thể cần nhiều lần thử; tăng `max-attempts` nếu hay báo "không tìm được vị trí".
- Khi tắt plugin/server, GUI còn mở được đóng theo kiểu cố gắng hết sức.
