package dot.kt.priceChecker.exceptions.common;

import dot.kt.priceChecker.exceptions.sheets.SheetAccessException;
import dot.kt.priceChecker.exceptions.sheets.SheetValidationException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentAccountChange() {
        return problem(HttpStatus.CONFLICT, "ACCOUNT_CHANGED", "The account changed during this request. Retry the operation.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication() {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid credentials or authentication is required.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied() {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action.");
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleParameters() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETERS", "Check the request parameters.");
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException exception) {
        return problem(exception.getStatus(), exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(SheetValidationException.class)
    ResponseEntity<ProblemDetail> handleSheetValidation(SheetValidationException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setProperty("code", exception.getCode());
        detail.setProperty("importId", exception.getImportId());
        detail.setProperty("errors", exception.getErrors());
        return ResponseEntity.status(exception.getStatus()).body(detail);
    }

    @ExceptionHandler(SheetAccessException.class)
    ResponseEntity<ProblemDetail> handleSheetAccess(SheetAccessException exception) {
        HttpStatus status = switch (exception.getKind()) {
            case INVALID_URL -> HttpStatus.BAD_REQUEST;
            case NOT_PUBLIC, NOT_FOUND, LIMIT_EXCEEDED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UPSTREAM -> HttpStatus.BAD_GATEWAY;
        };
        return problem(status, "SHEET_" + exception.getKind().name(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the request fields.");
        detail.setProperty("code", "VALIDATION_FAILED");
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage())).toList();
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleMalformedRequest() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Supply a valid JSON request body.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraint() {
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with an existing record.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setProperty("code", code);
        return ResponseEntity.status(status).body(detail);
    }

    private record FieldError(String field, String message) {
    }
}
