# Chi tiêu ngay — Android MVP (viết độc lập)

Ứng dụng mẫu Android Kotlin xây dựng mới, **không sao chép mã** từ CashflowTracking. Mục tiêu: đọc thông báo biến động số dư từ những ứng dụng ngân hàng đã chọn, phân tích số tiền, lập giao dịch chờ và nhắc người dùng phân loại ngay.

## Luồng chính

1. Người dùng cấp **Truy cập thông báo** và **Cho phép thông báo**.
2. Android `NotificationListenerService` lọc theo gói ứng dụng ngân hàng, trích tiêu đề/nội dung.
3. Parser xác nhận đây là giao dịch có số tiền; nếu không đủ thông tin sẽ bỏ qua (không tự đoán số tiền).
4. SQLite lưu trạng thái `pending`; nhắc ngay bằng thông báo ưu tiên cao, mở thẳng form giao dịch khi chạm.
5. Không thể bấm **Lưu** khi chưa xác nhận Thu/Chi/Chuyển nội bộ, danh mục, mô tả. Khoản `pending` không được tính vào báo cáo theo danh mục.
6. Nếu chưa xử lý, nhắc lại khoảng 30 phút sau (độ trễ thực tế phụ thuộc Android); các khoản chờ luôn hiện khi mở app.
7. Báo cáo tổng thu, tổng chi, chênh lệch và chi tiêu theo danh mục của tháng. Chuyển tiền giữa tài khoản không tính thu/chi.

Có nút **Thử giao dịch giả** để kiểm tra màn hình bắt buộc phân loại mà không cần phát sinh giao dịch thật.

## Cách build APK (qua GitHub Actions)

1. Tạo repository **riêng** trên GitHub và đẩy **các file trong thư mục này** lên branch `main`.
2. Trong GitHub mở **Actions → Android APK (debug) → Run workflow**.
3. Khi build xanh, mở lần chạy đó → **Artifacts → ChiTieuNgay-debug-apk**.
4. Tải ZIP, giải nén file `app-debug.apk`, chuyển sang Android và cài (chỉ cài từ nguồn bạn tin cậy).

Build trên máy có Android SDK 35, Java 17, Gradle 8.9:

```sh
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

File APK sẽ ở `app/build/outputs/apk/debug/app-debug.apk`.

## Giới hạn và lưu ý quan trọng

- **Bản MVP để thử nghiệm, chưa phải ứng dụng tài chính được kiểm thử để dùng thật.** Chưa xác thực trên điện thoại hoặc từng ngân hàng. Cần thử từng mẫu thông báo bằng dữ liệu giả trước.
- Android 10+ hạn chế tự mở Activity từ nền. App chỉ hiện thông báo ưu tiên cao và chuyển đến form **khi người dùng chạm**; không thể bảo đảm một popup luôn đè lên mọi ứng dụng đang dùng. Android 14+ hạn chế full-screen intent cho các app không phải cuộc gọi/báo thức.
- Quyền truy cập thông báo rất nhạy cảm. Ứng dụng mẫu lọc gói ngân hàng trên thiết bị, không có `INTERNET` permission; SQLite chỉ được lưu trong vùng riêng của app. Tắt sao lưu tự động. Không yêu cầu mật khẩu/OTP.
- Mẫu parser hiện mới là heuristic phổ thông, không đảm bảo chính xác từng mẫu ngân hàng; nếu nhiều số xuất hiện cùng một thông báo mà không đủ tín hiệu, sẽ bỏ qua thay vì ghi sai. Các mẫu app không được liệt kê trong cài đặt sẽ không được nhận diện.
- Cơ chế chống trùng theo nội dung và cửa sổ hai phút có thể bỏ sót giao dịch thực sự giống nhau; cần cải tiến theo mã tham chiếu giao dịch của từng ngân hàng.
- Bộ nhắc 30 phút dùng alarm không chính xác (`setAndAllowWhileIdle`) và có thể bị hệ thống trì hoãn. Nếu người dùng tắt thông báo, trạng thái chờ vẫn còn trong app.
- Chưa có xuất Excel/CSV, chỉnh sửa giao dịch đã duyệt, bảo vệ bằng PIN hoặc sinh trắc, bộ nhận diện riêng cho từng ngân hàng, xử lý SMS/email hoặc khôi phục từ bản sao lưu. Đây là các bước tiếp theo.
- Repo CashflowTracking được dùng để **nghiên cứu khả năng**; nguồn gốc công khai không đồng nghĩa quyền tái sử dụng. Tại ngày kiểm tra, repo gốc không có giấy phép ở cấp root, file `apps/extensions/flet_bank_notification_listener/LICENSE` chỉ chứa placeholder. Không sao chép / tái phân phối phần code của repo đó.

## Vì sao tên gói ngân hàng có thể không hoạt động?

Bộ lọc ban đầu dùng các chuỗi `mbbank,vietcombank,vcb,...` trong package name. Tên package thực tế tùy phiên bản ứng dụng: vào **Chọn ứng dụng ngân hàng được theo dõi** để sửa chuỗi. Chỉ nên cấp quyền sau khi đã xem và hiểu mã nguồn.
