## Global Constraints

- Java 21, Spring Boot 4.1.1, package gốc `com.fsoft.training`. Lưu ý Boot 4 dùng Jackson 3 (`tools.jackson.*`) và đã đổi tên starter (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`...). Kiểm tra tên trong Boot 4.1 BOM, đừng đoán theo Boot 3.
- Image `postgres:17.6` (ghim, không dùng `latest`). Database `textsql`.
- SQL do LLM sinh **chỉ** chạy qua DataSource `analyst` (role `analyst_ro`). Không bao giờ chạy qua DataSource `app`.
- LIMIT tối đa 1000 dòng. `statement_timeout` = 10s. Số lần tự sửa lỗi tối đa = 2.
- Không commit API key hay JWT secret. Đọc từ biến môi trường `LLM_API_KEY` và `JWT_SECRET` (secret ≥ 32 byte). Có file `.env.example` để mô tả.
- Mật khẩu role DB dev (`app_rw_dev`, `analyst_ro_dev`) được hardcode trong script init, chỉ dùng cho môi trường local. Config đọc từ env, mặc định là các giá trị này.
- Quy tắc ngày *(quyết định khi lập plan)*: câu hỏi không nêu năm (vd "tháng 9") nghĩa là **gộp mọi năm**. Ghi quy tắc này trong semantic layer, và eval phải theo đúng.
- Câu hỏi có thể bằng tiếng Việt hoặc tiếng Anh. Thông báo cho người dùng viết bằng tiếng Việt.
- Few-shot và eval không được trùng câu hỏi (so sánh sau khi chuẩn hóa: lowercase, bỏ dấu, gộp khoảng trắng).
- Hội thoại không thuộc người đang đăng nhập thì trả **404**.
- Integration test cần Docker Desktop đang chạy (Testcontainers).

*(Quyết định khi lập plan: bỏ dependency `spring-boot-docker-compose`, người dùng tự chạy `docker compose up -d`. Lý do: tính năng auto-connection của nó chỉ tạo được một DataSource, trong khi ta cần hai.)*

## Review Focus

1. **LLM trả SQL lẫn chữ**: có code fence ```` ```sql ````, có câu giải thích trước hoặc sau, có dấu `;` ở cuối. Phải tách được đúng một câu SQL. Test nằm ở Task 9 (`SqlExtractorTest`).
2. **Thiếu `LLM_API_KEY`, Gemini trả 429/5xx hoặc timeout**: app vẫn khởi động được. Người dùng nhận thông báo lỗi tiếng Việt (status `ERROR`), không phải lỗi 500 kèm stacktrace. Test nằm ở Task 7 và Task 11.
3. **Kết quả 0 dòng, có giá trị `null`, kiểu `numeric`/`date`**: JSON trả về hợp lệ, không ném lỗi chia cho 0 hay NPE. Biểu đồ rơi về `TABLE` khi 0 dòng. Test nằm ở Task 5 và Task 6.
4. **JWT hết hạn hoặc sai trong lúc dùng**: frontend xóa token và chuyển về `/login`, không treo ở màn hình trắng. Test nằm ở Task 13 (`api.test.ts`).
5. **Câu hỏi có dấu hoặc không dấu** ("doanh thu" / "doanh thu" gõ không dấu / "DOANH THU"): few-shot vẫn chọn đúng ví dụ. Test nằm ở Task 8.

