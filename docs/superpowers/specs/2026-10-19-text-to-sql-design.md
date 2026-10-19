# Thiết kế: Trợ lý phân tích dữ liệu bằng ngôn ngữ tự nhiên (Text-to-SQL)

Ngày: 2026-10-19

## 1. Mục đích và lý do

Đây là bài tập thuộc mảng Hybrid (Data Engineering + Applied AI). Người dùng gõ câu hỏi bằng ngôn ngữ tự nhiên, tiếng Việt hoặc tiếng Anh (ví dụ: "doanh thu tháng 9 theo khu vực"). Hệ thống sinh SQL, chạy trên warehouse rồi trả về câu SQL, bảng kết quả và biểu đồ.

Bài tập nhằm học ba thứ:
- đưa metadata/schema vào context của LLM,
- kiểm soát rủi ro khi LLM sinh code,
- phối hợp giữa Data Engineering và AI.

Thiết kế cần làm nổi bật cả ba điểm này.

## 2. Mục tiêu (sản phẩm tối thiểu theo đề)

1. Chat UI: hỏi câu nào cũng thấy được SQL đã sinh, bảng kết quả và biểu đồ (khi dữ liệu phù hợp).
1b. Hội thoại nhiều lượt: hiểu được câu hỏi nối tiếp dựa trên các lượt trước, ví dụ "còn tháng 10 thì sao?", "chỉ lấy châu Âu thôi", "vẽ theo quý" *(người dùng yêu cầu)*.
2. Guardrail: chỉ cho phép SELECT.
3. Few-shot lấy từ bộ câu hỏi mẫu.
4. Bộ eval khoảng 40 câu hỏi có SQL đáp án, kèm lệnh chạy tự động và báo cáo tỉ lệ đúng.
5. Đăng ký, đăng nhập, và danh sách các cuộc hội thoại cũ của mỗi người dùng, lưu bền trong database *(người dùng yêu cầu)*.

## 3. Không làm (non-goals)

- Không phân quyền theo vai trò (admin/user), không giới hạn dữ liệu theo người dùng: ai đăng nhập cũng hỏi được toàn bộ warehouse. *(Quyết định của tôi.)*
- Không xác thực email, không quên/đặt lại mật khẩu, không đăng nhập bằng Google/OAuth. *(Quyết định của tôi.)*
- Không đổi tên cuộc hội thoại, không tìm kiếm trong lịch sử, không chia sẻ hội thoại. *(Quyết định của tôi.)*
- Không dùng dữ liệu thật hay dữ liệu từ đề tài 1/4 (người dùng chưa có).
- Không deploy lên cloud, chỉ cần chạy local.

## 4. Anti-goals (chạy được nhưng vẫn coi là hỏng)

- LLM chạy được bất kỳ lệnh ghi hoặc xóa nào, hoặc đọc được bảng ngoài warehouse. Đặc biệt nghiêm trọng: SQL do LLM sinh đọc được bảng `app.users` (mật khẩu đã băm) hay lịch sử hội thoại.
- Người dùng xem, sửa hoặc xóa được hội thoại của người khác, chẳng hạn bằng cách đổi id trên URL.
- Mật khẩu lưu dạng rõ, hoặc JWT secret / API key bị commit vào repo.
- Guardrail chỉ dò từ khóa bằng regex (có thể lách bằng comment, chuỗi, nhiều câu lệnh).
- Câu hỏi trong bộ eval xuất hiện lại trong few-shot (rò đáp án làm điểm eval ảo).
- Eval chấm bằng cách so chuỗi SQL thay vì so kết quả.

## 5. Ràng buộc và công nghệ (người dùng đã chọn)

| Phần | Lựa chọn |
|------|----------|
| Backend | Spring Boot 4.1.1, Java 21, Maven. Dùng khung sẵn có trong repo (`com.fsoft.training`) |
| Frontend | React |
| Database / warehouse | PostgreSQL chạy qua `compose.yaml` |
| Dataset | Chinook (bản PostgreSQL) |
| LLM | Gemini qua API key free (Google AI Studio), người dùng tự đăng ký. Phải đổi được nhà cung cấp chỉ bằng cấu hình |

Hệ quả của việc dùng key free: hạn mức request/phút thấp. Lệnh chạy eval phải có giãn cách hoặc retry khi gặp 429.

## 6. Kiến trúc

```
React chat UI  ──HTTP + JWT──▶  Spring Boot API ──(role app_rw)──▶ schema app
                                                   (users, conversations, messages)
                              │
              ┌───────────────┼──────────────────┐
              ▼               ▼                  ▼
      Prompt builder     LLM client         SQL guardrail
   (schema + semantic   (OpenAI-compatible  (JSqlParser)
    + few-shot)          endpoint)               │
                                                 ▼
                                   Postgres, role analyst_ro
                                   (schema dw, read-only, timeout)
```

### 6.1 Lớp dữ liệu (phần DE)

- **Schema `chinook`**: dữ liệu Chinook gốc, nạp bằng script SQL lúc khởi tạo container (`docker-entrypoint-initdb.d`) hoặc migration. Cách nạp do builder chọn.
- **Schema `dw`**: star schema dựng từ `chinook` bằng script SQL có thể chạy lại. *(Đề xuất của tôi, người dùng đã đồng ý.)*
  - `fact_sales`: mỗi dòng ứng với một invoice line. Gồm khóa ngày, khách hàng, track, nhân viên, `quantity`, `unit_price`, `revenue = quantity * unit_price`, `invoice_id`.
  - `dim_date`: ngày, tháng, quý, năm, tên tháng.
  - `dim_customer`: quốc gia, thành phố, công ty, và cột `region` (bảng ánh xạ quốc gia → khu vực, ví dụ Bắc Mỹ / Nam Mỹ / Châu Âu / Châu Á–Thái Bình Dương). Bảng ánh xạ do builder định nghĩa và ghi vào semantic layer.
  - `dim_track`: tên bài, album, nghệ sĩ, thể loại, media type, thời lượng.
  - `dim_employee`: nhân viên hỗ trợ khách hàng (support rep).
  - Builder được phép điều chỉnh chi tiết cột, miễn trả lời được các câu hỏi trong bộ eval.
- **Semantic layer**: một file (YAML hoặc JSON) trong repo, mô tả từng bảng và cột của `dw`. Gồm định nghĩa chỉ số (doanh thu, số đơn, số khách...), từ đồng nghĩa tiếng Việt/Anh (khu vực = region, doanh thu = revenue...), và giá trị mẫu cho các cột phân loại. Đây là nguồn duy nhất để sinh phần schema trong prompt.
- **Lưu ý về ngày**: các bản Chinook khác nhau có khoảng năm khác nhau. Câu hỏi không nêu năm (như "tháng 9") cần một quy tắc rõ ràng ghi trong semantic layer, ví dụ gộp mọi năm hoặc lấy năm mới nhất có dữ liệu. Builder chọn một cách, ghi lại, và bộ eval phải theo đúng quy tắc đó.

### 6.2 Sinh SQL

1. Nhận câu hỏi.
2. Chọn khoảng 3–5 ví dụ few-shot từ file câu hỏi mẫu (tách riêng khỏi bộ eval), theo độ giống với câu hỏi. Để đơn giản, đo độ giống bằng độ trùng từ hoặc từ khóa, chưa cần embedding. *(Quyết định của tôi. Builder có thể nâng lên embedding nếu muốn.)*
3. Ghép prompt theo thứ tự: chỉ dẫn hệ thống (dialect PostgreSQL, chỉ SELECT, chỉ dùng schema `dw`, trả về SQL thuần) + schema/semantic + few-shot + câu hỏi.
4. Gọi LLM qua interface `LlmClient`. Bản cài đặt mặc định gọi endpoint OpenAI-compatible, mà Gemini, Groq và Ollama đều hỗ trợ. Base URL, model và key nằm trong cấu hình hoặc biến môi trường, không commit key. *(Quyết định của tôi.)*
5. Tách SQL ra khỏi câu trả lời (bỏ code fence nếu có).

### 6.2b Hội thoại nhiều lượt

- Mỗi cuộc hội thoại có một `conversationId` do server cấp và thuộc về một người dùng. Frontend gửi kèm id này ở mọi câu hỏi, và có nút "Hội thoại mới" để bắt đầu lại.
- Lịch sử được lưu bền trong schema `app` (xem mục 6.7). Ngữ cảnh gửi cho LLM lấy từ các lượt đã lưu: câu hỏi gốc, câu hỏi đã viết lại, SQL cuối cùng, tên cột kết quả. Không gửi dữ liệu dòng cho LLM.
- Xử lý theo hai bước *(quyết định của tôi)*:
  1. **Viết lại câu hỏi**: nếu đã có lượt trước, gọi LLM một lần với khoảng 3 lượt gần nhất (câu hỏi + SQL) để viết lại câu hiện tại thành một câu hỏi độc lập, đầy đủ nghĩa. Ví dụ: "còn tháng 10 thì sao?" → "doanh thu tháng 10 theo khu vực". Nếu câu hỏi đã tự đủ nghĩa thì giữ nguyên.
  2. **Sinh SQL** như mục 6.2, dùng câu đã viết lại. Prompt kèm SQL của lượt trước làm gợi ý, để LLM sửa từ SQL đó thay vì viết lại từ đầu. Few-shot được chọn theo câu đã viết lại.
- Lợi ích của cách hai bước: chọn few-shot chính xác hơn, dễ debug (thấy được LLM hiểu câu hỏi thế nào), và có thể chấm riêng bước viết lại.
- Câu đã viết lại được trả về trong API (trường `rewrittenQuestion`) và hiện mờ dưới câu hỏi trong UI, để người dùng thấy hệ thống hiểu câu của mình ra sao.
- Đổi lại, mỗi câu nối tiếp tốn thêm một lượt gọi LLM. Với free tier điều này chấp nhận được.

### 6.3 Guardrail (nhiều lớp)

1. **Phân tích cú pháp** bằng JSqlParser: chỉ chấp nhận đúng một câu lệnh và phải là `SELECT` (cho phép CTE `WITH ... SELECT`). Mọi loại khác đều bị từ chối.
2. **Danh sách bảng cho phép**: mọi bảng được tham chiếu phải thuộc schema `dw`. Chặn `app`, `chinook`, `pg_catalog`, `information_schema` và các hàm nguy hiểm như `pg_sleep` hoặc `pg_read_file`.
3. **Giới hạn số dòng**: nếu câu không có LIMIT, tự thêm LIMIT (ví dụ 1000).
4. **Tầng database**: SQL do LLM sinh chạy qua một DataSource riêng, đăng nhập bằng role `analyst_ro`. Role này chỉ có quyền `USAGE` + `SELECT` trên schema `dw`, không có quyền gì trên `app` hay `chinook`, có `default_transaction_read_only = on` và `statement_timeout` khoảng 10 giây. Phần còn lại của ứng dụng (người dùng, lịch sử) dùng DataSource thứ hai với role `app_rw`, chỉ có quyền trên schema `app`. Đây là lớp chặn cuối cùng, kể cả khi các lớp 1–3 bị lọt.
5. **Tự sửa lỗi**: nếu SQL chạy lỗi (lỗi DB, không phải bị guardrail chặn), gửi lỗi kèm SQL lại cho LLM để sửa, tối đa 2 lần. SQL bị guardrail chặn thì từ chối luôn và báo lý do cho người dùng.
6. Ghi log mỗi request: câu hỏi, SQL, kết quả guardrail, thời gian chạy, lỗi nếu có.

### 6.4 Kết quả và biểu đồ

- API trả về: `conversationId`, `rewrittenQuestion`, `sql`, `columns` (tên và kiểu), `rows`, `chart` (gợi ý), `error` nếu có.
- Gợi ý biểu đồ dựa trên quy tắc theo hình dạng kết quả *(quyết định của tôi)*:
  - 1 cột thời gian + 1 cột số → biểu đồ đường,
  - 1 cột phân loại + 1 cột số → biểu đồ cột (dùng biểu đồ tròn khi ít nhóm và là tỉ trọng),
  - 1 dòng 1 giá trị → thẻ số,
  - còn lại → chỉ hiện bảng.

### 6.5 Frontend React

- Đặt trong thư mục `frontend/` của repo, dùng Vite. Builder chọn TS hay JS và thư viện biểu đồ (gợi ý Recharts).
- Màn hình chat: ô nhập câu hỏi. Mỗi câu trả lời hiện SQL (thu gọn hoặc mở được), bảng kết quả và biểu đồ. Khi bị chặn hoặc lỗi thì báo rõ lý do.
- Gợi ý vài câu hỏi mẫu để người dùng bấm thử.
- Trang đăng ký và đăng nhập. Chưa đăng nhập thì mọi route khác chuyển về trang đăng nhập. Có nút đăng xuất.
- Thanh bên trái liệt kê các cuộc hội thoại cũ (mới nhất ở trên) và nút "Hội thoại mới". Bấm vào một hội thoại cũ thì nạp lại toàn bộ các lượt: câu hỏi, SQL, bảng, biểu đồ. Có thể hỏi tiếp ngay trong hội thoại đó. Mỗi mục có nút xóa (kèm xác nhận).

### 6.7 Tài khoản và lịch sử hội thoại

- **Đăng ký**: username (duy nhất) và mật khẩu, tối thiểu 8 ký tự. Mật khẩu băm bằng BCrypt. *(Quyết định của tôi.)*
- **Đăng nhập**: trả về JWT (access token, hạn khoảng 24 giờ), ký bằng secret lấy từ biến môi trường. Frontend lưu token và gửi qua header `Authorization: Bearer`. Không có refresh token: hết hạn thì đăng nhập lại. *(Quyết định của tôi. Chọn JWT vì frontend React chạy tách origin lúc dev và dễ test bằng Postman. Đổi lại phải chấp nhận rủi ro XSS khi lưu token ở localStorage, ở mức bài tập là chấp nhận được.)*
- **Spring Security**: stateless. Mở `/api/auth/**`, mọi `/api/**` còn lại bắt buộc có JWT hợp lệ. Cấu hình CORS cho origin của frontend lúc dev.
- **Schema `app`**, quản lý bằng Flyway *(quyết định của tôi)*:
  - `users`: id, username, password_hash, created_at.
  - `conversations`: id (UUID, để không đoán được), user_id, title, created_at, updated_at. Tiêu đề lấy từ câu hỏi đầu tiên, cắt khoảng 60 ký tự.
  - `messages`: id, conversation_id, thứ tự lượt, câu hỏi gốc, câu đã viết lại, SQL cuối cùng, trạng thái (ok / blocked / error), thông báo lỗi, gợi ý biểu đồ, **ảnh chụp kết quả** (JSONB gồm cột và dòng, giới hạn theo LIMIT ở mục 6.3), created_at.
  - Lưu ảnh chụp kết quả để khi mở lại hội thoại cũ thì hiện ngay, không phải chạy lại SQL và không tốn thêm lượt gọi LLM. *(Quyết định của tôi.)*
- **API** (gợi ý, builder chốt tên):
  - `POST /api/auth/register`, `POST /api/auth/login`
  - `GET /api/conversations`: danh sách của người đang đăng nhập.
  - `GET /api/conversations/{id}`: đầy đủ các lượt.
  - `DELETE /api/conversations/{id}`
  - `POST /api/chat`: nhận `{conversationId?, question}`. Không có id thì tạo hội thoại mới.
- **Kiểm tra quyền sở hữu**: mọi thao tác theo `conversationId` đều kiểm tra hội thoại có thuộc người đang đăng nhập không. Nếu không thuộc thì trả 404, không trả 403, để không lộ việc id đó có tồn tại.
- **Eval runner**: gọi thẳng service bên trong, không đi qua HTTP/JWT, và không ghi vào lịch sử của người dùng thật. *(Quyết định của tôi.)*

### 6.6 Bộ eval

- File (JSON hoặc YAML) gồm khoảng 40 mục `{id, question, gold_sql, difficulty}`, hỏi bằng cả tiếng Việt và tiếng Anh, phân theo độ khó:
  - dễ: một bảng, lọc hoặc đếm,
  - trung bình: join, group by,
  - khó: theo thời gian, top-N trong nhóm, so sánh giữa các kỳ.
- Thêm khoảng 5 chuỗi hội thoại nhiều lượt (mỗi chuỗi 2–3 lượt). Mỗi lượt có `gold_sql` riêng và được chấm như câu thường. Runner phải chạy tuần tự trong cùng một hội thoại. Báo cáo tách riêng tỉ lệ đúng của các lượt nối tiếp. Các chuỗi này tính thêm, ngoài 40 câu đơn.
- Thêm vài câu "tấn công" (yêu cầu xóa hoặc sửa dữ liệu, prompt injection) với kết quả mong đợi là bị từ chối.
- Lệnh chạy eval (ví dụ một profile hoặc runner của Spring, hoặc một endpoint): với mỗi câu, sinh SQL, chạy, rồi so **tập kết quả** với kết quả của `gold_sql`. Bỏ qua thứ tự dòng trừ khi gold có ORDER BY, bỏ qua tên cột, làm tròn số thực.
- Báo cáo: tỉ lệ đúng tổng và theo độ khó, danh sách câu sai kèm SQL sinh ra. Xuất ra file (Markdown hoặc HTML).
- Few-shot và eval là hai file tách biệt, không trùng câu hỏi.
- Câu "tấn công" tính là đạt khi không có gì được thực thi ngoài một SELECT chỉ đọc trên `dw`: bị guardrail chặn hoặc LLM tự từ chối đều được. *(Quyết định của tôi.)*
- Thừa cột hoặc đổi thứ tự cột: tính là đúng nếu kết quả sinh ra chứa đủ các cột của gold, khớp theo giá trị. *(Quyết định của tôi.)*
- Không đặt ngưỡng accuracy bắt buộc. Mục tiêu là có eval chạy được và báo cáo trung thực. *(Quyết định của tôi.)*

## 7. Phần để builder tự quyết

- Cấu trúc package, tên lớp, cách viết test.
- Cách nạp Chinook (init script hay Flyway) và chi tiết cột trong `dw`.
- Định dạng file semantic và file eval.
- Model Gemini cụ thể (một bản flash có trong free tier).
- Thư viện UI và biểu đồ phía React.
- Ghim phiên bản image Postgres thay vì `latest`.

## 8. Ghi chú

- Repo hiện chưa phải git repo, nên chưa commit được tài liệu này. Cần `git init` trước khi bắt đầu implement.
- `pom.xml` hiện mới có security, postgres, lombok. Cần thêm web, jdbc (hoặc JPA cho schema `app`), validation, Flyway, thư viện JWT (hoặc `oauth2-resource-server` của Spring), JSqlParser...
- Cần có test cho các phần bảo mật: guardrail, không đọc được `app` qua `analyst_ro`, và không truy cập được hội thoại của người khác.
