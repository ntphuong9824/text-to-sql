### Task 8: Few-shot

**Files:**
- Create: `src/main/resources/fewshot/examples.yaml`, `nl2sql/FewShotExample.java`, `nl2sql/FewShotSelector.java`, `nl2sql/TextNormalizer.java`
- Test: `nl2sql/FewShotSelectorTest.java` (unit), `nl2sql/FewShotExamplesValidTest.java` (integration)

**Interfaces:**
- Consumes: `SqlGuard` (Task 4), `SqlExecutor` (Task 5).
- Produces:
  - `record FewShotExample(String question, String sql)`.
  - `TextNormalizer.normalize(String) -> String`: static, lowercase, bỏ dấu tiếng Việt (`Normalizer` NFD + bỏ dấu, `đ` → `d`), bỏ dấu câu, gộp khoảng trắng.
  - `TextNormalizer.tokens(String) -> Set<String>`: tách từ, bỏ stopword ngắn (`la`, `cua`, `the`, `of`, `cho`...). Dùng thêm bigram để "doanh thu" khớp thành cụm.
  - `FewShotSelector`: `@Component`, `List<FewShotExample> select(String question, int k)`. Chấm điểm bằng Jaccard trên tokens+bigram, giữ thứ tự ổn định khi bằng điểm. `List<FewShotExample> all()`.

- [ ] **Step 1:** Viết khoảng 25 ví dụ trong `examples.yaml`:
  - trộn tiếng Việt và tiếng Anh;
  - bao các mẫu: doanh thu theo region, theo tháng, theo năm, top N nghệ sĩ/thể loại/bài hát, số đơn theo quốc gia, nhân viên bán tốt nhất, so sánh hai năm, khách mua nhiều nhất, thời lượng trung bình theo thể loại...;
  - SQL luôn ghi `dw.` trước tên bảng và theo đúng quy tắc gộp năm.
- [ ] **Step 2: Viết test** `FewShotExamplesValidTest`: với mỗi ví dụ, `SqlGuard.check` phải allowed và `SqlExecutor.execute` chạy không lỗi.
- [ ] **Step 3: Viết test** `FewShotSelectorTest`:
  - `select("doanh thu theo khu vực", 3)` có ví dụ về region trong 3 kết quả đầu.
  - `select("DOANH THU THEO KHU VUC", 3)` trả cùng kết quả như câu có dấu.
  - `select("", 3)` trả 3 ví dụ, không ném lỗi.
  - `normalize("Doanh thu Đông Nam Á")` = `"doanh thu dong nam a"`.
- [ ] **Step 4:** Chạy. Kỳ vọng: FAIL. Implement. Chạy lại. Kỳ vọng: PASS.
- [ ] **Step 5:** Commit: `feat(nl2sql): add few-shot examples and similarity selector`.

---

