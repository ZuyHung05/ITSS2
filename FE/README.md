# Frontend Hanare

FE chạy riêng bằng Node.js (không cần `npm install`). BE tiếp tục xử lý đăng nhập, session, CSRF và API.

Mở hai terminal từ thư mục gốc dự án:

```bash
# Terminal 1: backend, dùng cấu hình DB trong BE/application-local.properties
cd BE
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo -Dspring-boot.run.arguments=--server.port=8081
```

```bash
# Terminal 2: frontend
cd FE
npm run dev
```

Mở **http://127.0.0.1:5173** để dùng giao diện. `FE/dev-server.mjs` phục vụ HTML/CSS/JS từ thư mục FE và chuyển tiếp `/api`, đăng nhập, đăng xuất tới BE trên cổng 8081. Nhờ đó trình duyệt dùng cùng origin cho cookie và CSRF. Thay đổi trong `FE/templates` và `FE/static` chỉ cần tải lại trang, không cần build BE.

Nếu BE dùng cổng khác:

```bash
HANARE_BE_URL=http://127.0.0.1:8082 npm run dev
```

Đổi cổng FE bằng `HANARE_FE_PORT=5174`. Chỉ đưa `HANARE_BE_URL` tới BE bạn tin cậy vì FE server sẽ chuyển tiếp cookie đăng nhập tới địa chỉ đó.

`templates/` chứa các trang HTML; `static/assets/` chứa CSS/JS. BE vẫn có thể phục vụ FE khi chạy một cổng theo cách cũ, nhưng khi phát triển giao diện hãy mở cổng 5173.
