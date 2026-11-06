# Documents Hub cho Android (lưu trên Google Drive)

Đăng nhập bằng tài khoản Google là dùng được. Tệp nằm trong **Google Drive của chính bạn** (dùng chung 15 GB), không cần Firebase, không cần thẻ thanh toán.

## Cách lưu trữ
```
Drive/
└── Documents Hub/
    ├── Công việc/      <- mỗi phân loại là một thư mục con
    ├── Học tập/
    └── ...
```
- Tệp là tệp thật, mở được từ app Drive hoặc máy tính.
- Ghi chú = phần mô tả của tệp trên Drive; số trang PDF và thời lượng lưu trong thuộc tính riêng của app.
- Thùng rác = thùng rác của Drive (Drive tự xóa vĩnh viễn sau 30 ngày).
- Quyền `drive.file`: app **chỉ thấy tệp và thư mục do chính nó tạo**. Tệp bạn tự chép vào thư mục bằng web/Drive sẽ không hiện trong app (giới hạn của quyền này, đổi lại không đọc được phần còn lại của Drive).
- Kéo xuống để làm mới danh sách (không đồng bộ tức thời như Firestore).

## Cài đặt (làm một lần, miễn phí)
1. Vào https://console.cloud.google.com → tạo project mới.
2. **APIs & Services → Library** → bật **Google Drive API**.
3. **OAuth consent screen** (Google Auth Platform): loại **External**, điền tên app và email hỗ trợ; ở **Scopes** thêm `.../auth/drive.file`. Khi app còn ở chế độ *Testing*, thêm email của bạn vào **Test users**. Quyền `drive.file` không thuộc nhóm nhạy cảm nên khi muốn cho người khác dùng, bấm **Publish app** (theo mình biết không phải xác minh, bạn kiểm tra lại trên trang consent).
4. **Credentials → Create credentials → OAuth client ID → Android**: package name `vn.sdevice.documentshub`, SHA-1 lấy bằng `./gradlew signingReport` (bản debug và bản release dùng khóa khác nhau, tạo mỗi loại một client).
5. Mở project bằng Android Studio, Sync Gradle, chạy trên máy thật hoặc emulator có Google Play. Không cần `google-services.json`.

## Cấu trúc mã
```
data/DriveAuth.kt    xin quyền drive.file (Identity Authorization), lấy access token
data/DriveApi.kt     gọi Drive REST bằng OkHttp (liệt kê, tạo, sửa, tải lên resumable, tải xuống)
data/Repository.kt   ánh xạ tài liệu/phân loại/thùng rác sang Drive
ui/MainViewModel.kt  trạng thái và thao tác
ui/HomeScreen.kt     danh sách, ngăn điều hướng, thùng rác, phân loại
ui/ViewerScreen.kt   PDF / ảnh / video / âm thanh / văn bản (video, âm thanh phát trực tiếp từ Drive)
```

## Giới hạn hiện tại
- Tải lên chạy khi app còn mở; nếu hệ điều hành đóng app giữa chừng thì phải thêm lại tệp đó.
- Tệp tối đa 1 GB mỗi lần tải lên.
- Đăng xuất xóa dữ liệu tạm trên máy; mỗi lần đăng nhập app mở hộp chọn tài khoản Google để bạn chọn lại. Muốn thu hồi quyền hẳn, vào myaccount.google.com → Bảo mật → Ứng dụng của bên thứ ba. Tệp trên Drive vẫn còn nguyên.
- Tệp bạn tự chép hoặc di chuyển vào thư mục "Documents Hub" bằng app Drive hoặc web sẽ không hiện trong app (giới hạn của quyền `drive.file`). Muốn đưa tệp có sẵn trên Drive vào app, bấm "Thêm tệp" rồi chọn Google Drive trong trình chọn tệp của Android; app sẽ tải một bản sao lên thư mục của nó.
