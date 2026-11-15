# Hanare — Solution 1

Ứng dụng Spring Boot 4.1.2 / Java 17, PostgreSQL (Supabase), Thymeleaf và Spring Security session. Triển khai giao việc/xác nhận kế hoạch, checkpoint thứ Sáu, dashboard tuần, Yêu cầu hỗ trợ và thông báo trong ứng dụng theo [đặc tả](HANARE_SOLUTION1_AGENT_PLAN.md).

## Cấu trúc dự án

```text
FE/
├── templates/                    # Các trang HTML Thymeleaf
└── static/assets/                # app.css, app.js
BE/
├── src/main/java/com/example/demo/
│   ├── controller/               # API và điều hướng trang
│   ├── entity/                   # Entity ánh xạ bảng DB, enum
│   ├── repository/               # Truy vấn PostgreSQL
│   ├── service/                  # Quy tắc nghiệp vụ, checkpoint jobs
│   ├── dto/                      # Request và dữ liệu trả về
│   ├── exception/                # Lỗi và xử lý lỗi tập trung
│   └── config/                   # Security, scheduler, demo seed
├── src/main/resources/           # Cấu hình và Flyway migrations
├── src/test/java/                # Unit / integration tests
├── database/                     # Schema SQL tham khảo
├── pom.xml
└── mvnw
scripts/                          # Script hỗ trợ dùng từ thư mục gốc
```

Mở `BE/pom.xml` để import backend trong IDE. FE có máy chủ Node riêng cho phát triển; BE xử lý API và đăng nhập. BE vẫn đóng gói FE để hỗ trợ cách chạy một cổng cũ.

## Chạy FE và BE riêng

Mở hai terminal từ thư mục gốc dự án:

```bash
# Terminal 1 — BE trên cổng 8081
cd BE
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.arguments=--server.port=8081
```

```bash
# Terminal 2 — FE trên cổng 5173
cd FE
npm run dev
```

Mở `http://127.0.0.1:5173`. FE tự chuyển tiếp API/đăng nhập sang BE. Không cần `npm install`; máy chủ FE dùng các thư viện có sẵn của Node.js. Xem [FE/README.md](FE/README.md) nếu muốn đổi cổng.

## Chạy BE một cổng theo cách cũ

Cần Java 17+ và PostgreSQL. Maven Wrapper đã có sẵn.

Cách 1: export biến môi trường rồi chạy:

```bash
cd BE
export DB_PASSWORD='your-database-password'
./mvnw spring-boot:run
```

Cách 2: tạo `BE/application-local.properties` (hoặc `application-local.properties` khi đang đứng trong `BE`). File này được Spring tự import và bị `.gitignore` bỏ qua:

```properties
spring.datasource.password=your-database-password
```

Mở `http://localhost:8080`. Nếu port 8080 đang bận, thêm `-Dspring-boot.run.arguments=--server.port=8081` vào lệnh Maven. Phiên demo trong lần triển khai này chạy ở `http://localhost:8081`. Có thể đổi DB bằng `DB_URL` và `DB_USERNAME`. Biến `.env` không được tự đọc; `BE/.env.example` chỉ là mẫu tham khảo. Khi triển khai HTTPS, đặt `SESSION_COOKIE_SECURE=true`.

Flyway chạy trước Hibernate, tạo **10 bảng nghiệp vụ** và bảng lịch sử migration trên DB trống; Hibernate dùng `validate`. V2 bật RLS cho các bảng shared/Solution 1 để client Supabase anon/authenticated không đọc trực tiếp dữ liệu custom-auth; backend dùng tài khoản PostgreSQL tin cậy và kiểm tra quyền session. `BE/database/hanare_mvp_v2_schema.sql` là schema chuẩn để tham khảo. Không chạy script tạo bảng/baseline thủ công lên DB có dữ liệu. Các migration cũ chưa từng áp dụng được lưu tại `docs/legacy-migrations`; không thuộc Flyway locations đang hoạt động. Không có thao tác tự xóa dữ liệu.

## Dữ liệu demo (chủ động bật)

Profile mặc định không tạo tài khoản/dữ liệu giả. Để tạo một project demo riêng với 1 TL + 6 thành viên, đặt mật khẩu demo tối thiểu 12 ký tự rồi chạy:

```bash
cd BE
export HANARE_DEMO_PASSWORD='choose-a-private-demo-password'
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Hoặc thêm `hanare.demo.password=...` vào file local và chạy với profile `demo`. Seed lưu hash BCrypt và dùng ID ổn định, không nhân bản khi khởi động lại. Tài khoản:

- TL: `lead@hanare.demo`
- Thành viên: `member1@hanare.demo` đến `member6@hanare.demo`
- Mật khẩu: giá trị demo bạn tự đặt ở trên (khác mật khẩu PostgreSQL).

Project demo có task nháp, task đến hạn thứ Sáu/Chủ nhật, task quá hạn, task đến hạn hai tuần sau, task DONE và thành viên không có task. Một thành viên có cả ACHIEVED và NOT_ACHIEVED để minh họa. Đây là dữ liệu demo được ghi thật vào DB, chỉ nên bật với DB/project dùng để demo. Seed không tự cập nhật lịch demo đã tạo ở những tuần trước.

## Luồng dùng

1. TL vào Công việc của tôi → Công việc của nhóm → Tạo công việc, chọn thành viên và ngày tạm tính.
2. Thành viên mở task nháp, chỉnh deliverable/ngày và Lưu bản nháp. Task vẫn DRAFT, xuất hiện trong hàng đợi TL.
3. TL Xác nhận kế hoạch. Thành viên cập nhật execution status; timeline chỉ được sửa sau khi TL Mở lại bản nháp.
4. Thứ Sáu trước 22:00, thành viên báo cáo các task CONFIRMED còn dang dở đến hạn trong tuần hoặc quá hạn. DONE được đếm riêng, không tự sinh report.
5. Thành viên chọn Yêu cầu hỗ trợ từ task bất kỳ của mình. TL vào Duyệt và hỗ trợ → Yêu cầu hỗ trợ, ghi chú và Đánh dấu đã xử lý.

Các trang: `/projects`, `/projects/{id}`, `/projects/{id}/tasks`, `/projects/{id}/tasks/{taskId}`, `/projects/{id}/checkpoint`, `/projects/{id}/review` (TL).

## Scheduler và chạy demo

App cần đang chạy vào **thứ Sáu 15:30 và 22:00, Asia/Ho_Chi_Minh**. Reminder: tối đa một thông báo/member/project/Friday. Summary: tối đa một thông báo/TL/project/Friday khi có vấn đề. Job khóa dòng project trong transaction trước khi kiểm tra/chèn thông báo để an toàn khi chạy lặp/đồng thời. Không có public scheduler endpoint ở profile mặc định.

Với profile `demo`, TL có nút chạy job trong Duyệt và hỗ trợ → Báo cáo tuần, hoặc dùng script đăng nhập bình thường và có CSRF:

```bash
python3 scripts/demo-checkpoint.py --project PROJECT_UUID --date 2026-11-13 --job remind
python3 scripts/demo-checkpoint.py --project PROJECT_UUID --date 2026-11-13 --job finalize
```

Các lệnh `python3 scripts/...` được chạy từ thư mục gốc repository. Script hỏi mật khẩu ứng dụng qua `getpass`. Demo job có thể chọn một thứ Sáu cụ thể để mô phỏng thông báo; không mở lại report đã đóng và không bỏ qua quyền TL.

## Kiểm thử

```bash
cd BE
./mvnw test
./mvnw -DskipTests package
cd ..
node --check FE/static/assets/app.js
```

Integration test khởi động PostgreSQL tạm trên port ngẫu nhiên, chạy Flyway và Hibernate validate. Mọi URL/password DB test được override; không ghi vào Supabase. Unit test bao phủ giờ Việt Nam, ranh giới tuần, cutoff và trạng thái màu; integration test bao phủ đăng nhập/session/CSRF, quyền sở hữu, kế hoạch khóa, report upsert đồng thời, thông báo idempotent, Yêu cầu hỗ trợ và riêng tư dữ liệu.

Để chạy lại walkthrough HTTP trên DB tạm (không chạm Supabase):

```bash
(cd BE && ./mvnw test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -DincludeScope=test)
python3 scripts/run-browser-demo.py
# Terminal khác:
python3 scripts/smoke-web.py
```

Fixture chạy ở `http://localhost:8082`, đồng hồ cố định thứ Sáu 13/11/2026 lúc 18:00 để thử report. Password **chỉ của fixture** là `Browser-demo-test-only-2026`. Dừng bằng Ctrl+C khi kiểm tra xong.

Chi tiết triển khai, kết quả đã chạy và giới hạn: [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md).

## Giới hạn MVP

Dashboard mô tả báo cáo tuần, không chấm điểm con người. Không biết chính xác thời điểm task hoàn thành vì schema không có `completed_at`; view quá khứ chỉ gần đúng. NOT_ACHIEVED thứ Sáu không đồng nghĩa trễ deadline Chủ nhật. Thông báo hiển thị khi tải lại/poll mỗi phút. Solution 2 hiện chưa có route, nên nút tạo volunteer help bị vô hiệu hóa; các ID tích hợp được giữ nguyên. Không có màn hình Plan A/B, email/push, tự chia lại việc hay lịch sử audit giả.


## Bộ acceptance test đầy đủ (test plan Codex)

Xem [tests/solution1/README.md](tests/solution1/README.md) để chạy 81 kịch bản kỹ thuật và các kiểm thử bổ sung bằng Playwright trên PostgreSQL tạm. [SOLUTION1_TEST_RESULTS.md](SOLUTION1_TEST_RESULTS.md) ghi kết quả thực thi, log, ảnh và bằng chứng API/DB cho từng ID.

Server test riêng: `http://127.0.0.1:18083`, fixture thứ Sáu 20/11/2026; không dùng Supabase. Mọi control reset/clock chỉ tồn tại trong test classpath và yêu cầu khóa test ngẫu nhiên. Kịch bản khảo sát sinh viên cần người tham gia thật và được báo BLOCKED khi chưa có dữ liệu.
