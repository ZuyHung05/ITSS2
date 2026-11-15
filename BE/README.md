# Backend Hanare

Java 17 / Spring Boot, PostgreSQL, Spring Security, Flyway.

| Package | Vai trò |
| --- | --- |
| `controller` | Nhận HTTP request, gọi service, trả response hoặc trang HTML |
| `entity` | Entity ánh xạ bảng database và enum |
| `repository` | Truy vấn, thêm và cập nhật dữ liệu PostgreSQL |
| `service` | Quy tắc nghiệp vụ, phân quyền project/task, checkpoint và thông báo |
| `dto` | Request body và dữ liệu trả về API |
| `exception` | Lỗi nghiệp vụ và xử lý lỗi API tập trung |
| `config` | Security/session, lịch scheduler, cấu hình seed demo |

Tất cả package nằm trong `src/main/java/com/example/demo`. Migration ở `src/main/resources/db/migration`, schema tham khảo ở `database`, test ở `src/test/java`.

Chạy từ thư mục `BE`:

```bash
./mvnw spring-boot:run
```

Đặt mật khẩu DB trong `BE/application-local.properties` đang bị Git bỏ qua, hoặc biến môi trường `DB_PASSWORD`. Khi chạy JAR cũng đứng trong `BE` để Spring đọc đúng file local:

```bash
./mvnw package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

Khi phát triển FE riêng, chạy BE trên cổng 8081 rồi mở terminal khác trong `FE` và chạy `npm run dev`; truy cập FE ở `http://127.0.0.1:5173`. Maven vẫn đóng gói HTML/CSS/JS từ `../FE` để hỗ trợ cách chạy một cổng. Xem [README chung](../README.md) để chạy profile demo và các script kiểm thử.
