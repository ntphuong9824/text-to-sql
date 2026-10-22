package com.fsoft.training.config;

import com.fsoft.training.auth.AuthDtos.ErrorResponse;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fsoft.training.auth.UsernameTakenException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> onInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return badRequest(message.isBlank() ? "Dữ liệu không hợp lệ" : message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> onInvalidParameter(HandlerMethodValidationException ex) {
        return badRequest("Dữ liệu không hợp lệ");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> onUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("Nội dung yêu cầu không hợp lệ");
    }

    @ExceptionHandler(UsernameTakenException.class)
    ResponseEntity<ErrorResponse> onUsernameTaken(UsernameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("Tên đăng nhập đã tồn tại"));
    }

    @ExceptionHandler(com.fsoft.training.conversation.ConversationNotFoundException.class)
    ResponseEntity<ErrorResponse> onConversationNotFound(
            com.fsoft.training.conversation.ConversationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Không tìm thấy hội thoại"));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Yêu cầu không hợp lệ";
            case 404 -> "Không tìm thấy";
            case 405 -> "Phương thức không được hỗ trợ";
            case 415 -> "Định dạng nội dung không được hỗ trợ";
            default -> status.is5xxServerError()
                    ? "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau" : "Yêu cầu không thể xử lý";
        };
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> onUnexpected(Exception ex) {
        // Spring MVC exceptions (404, 405, 415, 400...) implement ErrorResponse and carry their own status.
        if (ex instanceof org.springframework.web.ErrorResponse withStatus) {
            HttpStatusCode status = withStatus.getStatusCode();
            log.debug("Request failed with status {}", status, ex);
            return ResponseEntity.status(status).body(new ErrorResponse(messageFor(status)));
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau"));
    }

    private static ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }
}
