# MC Bot Manager (app Android chạy bot Minecraft ngay trên điện thoại)

Biến `mcpeb.js` thành app Android thật. **Không cần server**: Node.js (nodejs-mobile) chạy ngay trong app,
giao diện là trang web nội bộ (127.0.0.1) hiển thị bằng WebView. Bot vẫn chạy khi tắt màn hình (foreground service).

## Build APK bằng GitHub (làm hoàn toàn trên điện thoại)
> **Lý do hay gặp nhất khiến "ném lên GitHub mà không build":** GitHub chỉ đọc workflow nằm ở
> `.github/workflows/` **ngoài cùng của repo**. File `build-apk.yml` nằm *bên trong* `project.zip` thì GitHub không thấy,
> nên chỉ tải mỗi `project.zip` lên thì tab Actions sẽ trống. Phải làm cả bước 3 bên dưới.
> Nếu repo này từng dùng cho dự án khác, hãy **xóa workflow cũ** trong `.github/workflows/` (nó cũng chạy khi tải zip lên và sẽ báo lỗi).

1. Tạo repo GitHub mới (private cũng được).
2. **Add file → Upload files**: tải lên `project.zip` (đừng giải nén) ở thư mục gốc repo.
3. **Add file → Create new file**: đặt tên đúng `.github/workflows/build-apk.yml` (gõ cả dấu `/`, GitHub sẽ tự tạo thư mục),
   dán toàn bộ nội dung file `build-apk.yml` đi kèm, Commit.
4. Vào tab **Actions → Build APK**. Nếu chưa tự chạy thì bấm **Run workflow**
   (có thể đổi *Tên phiên bản app* và *Kiến trúc CPU*; `arm64-v8a` là đủ cho máy đời mới, APK nhẹ hơn).
5. Lần đầu khoảng 10–20 phút. Xong: vào **Releases** của repo tải file `.apk` rồi cài
   (hoặc Actions → lần chạy → Artifacts).
6. Lần sau chỉ cần thay `project.zip` mới (hoặc Run workflow): APK mới **cài đè** lên bản cũ, giữ nguyên bot & cài đặt.

### Nếu build báo lỗi đỏ
Mở lần chạy lỗi trong tab Actions, kéo lên đầu trang: mục **Summary** có khung **"❌ Build lỗi"** đã gom sẵn đoạn lỗi quan trọng.
Chỉ cần copy khung đó gửi lại là đủ, không cần đọc cả log dài.

## Tính năng
- **Hướng dẫn bắt đầu nhanh** (lần mở đầu tiên): nhập server → tự kiểm tra và đặt đúng phiên bản → thêm bot → bật chạy nền.
  Lần đầu app **không tự chạy bot** cho tới khi bạn làm xong (tránh vào nhầm server mẫu). Mở lại hướng dẫn: Cài đặt → *Hiện lại hướng dẫn*.
- **Bot**: thêm / xóa / đổi tên, **thêm nhiều bot một lần** (Bot1, Bot2, ...), chạy – dừng – khởi động lại từng bot hoặc tất cả, tự chạy khi mở app.
- **Trạng thái từng bot**: thời gian online, đếm ngược "thử lại sau Ns", và **lý do mất kết nối** kèm gợi ý tiếng Việt
  (timeout, sai cổng, sai phiên bản, bị kick, server đầy, cần đăng nhập Xbox...).
- **Mỗi bot** có thể: dùng server chung hoặc server/cổng/phiên bản riêng; lệnh tự chạy sau khi vào
  (vd `5|/login matkhau`); tự nhắn định kỳ.
- **Cài đặt**: địa chỉ – cổng – **phiên bản Minecraft** (danh sách lấy từ thư viện, gõ tay cũng được),
  offline/online (Xbox), kiểm tra server (ping, báo phiên bản), tự kết nối lại + thời gian chờ,
  giãn cách khi khởi động, độ dài nhật ký, ghi file log.
- Chat từ bot đang chọn hoặc **tất cả bot**; **lọc nhật ký** (Chat / Hệ thống / Lỗi); nút **Sao chép** nhật ký để gửi lại khi cần hỗ trợ;
  nút "↓ Tin mới" khi đang cuộn lên đọc; radar vị trí người chơi gần bot; danh sách online.
- Chữ đang gõ không bị mất khi có bot đổi trạng thái; nút bấm to hơn dễ chạm; màn hình lỗi khởi động có nút **Sao chép lỗi**.
- Sao chép / nhập lại toàn bộ cấu hình (JSON).
- Dữ liệu lưu trong bộ nhớ riêng của app (`config.json`).

## Lưu ý
- Bấm Back/Home chỉ ẩn giao diện, bot vẫn chạy. Muốn dừng hẳn: Cài đặt → **Thoát hẳn app** hoặc nút *Thoát* trên thông báo.
- Để Android không tắt bot khi tiết kiệm pin: Cài đặt → **Cho phép chạy nền (pin)**.
- Lệnh bắt đầu bằng `/` được gửi như tin nhắn chat (giống file gốc) — hợp với server PocketMine/Nukkit.
- Muốn đổi phiên bản Minecraft mà app chưa có trong danh sách: build lại (workflow luôn lấy `bedrock-protocol` mới nhất).
- APK ký bằng khóa debug cố định đi kèm trong project (chỉ để tự dùng/sideload).

## Cấu trúc
- `nodejs-project/` — `main.js` (quản lý bot + HTTP nội bộ), `public/index.html` (giao diện)
- `app/` — Android (Kotlin): `MainActivity` (WebView), `NodeService` (chạy nền), `NodeRuntime` (giải nén & khởi động Node), `cpp/` (cầu nối JNI tới libnode.so)
- `tools/smoke.js` — kiểm tra trong CI
