package com.billiard.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.billiard.auth.dto.RegisterRequest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void responseStatusUsesExplicitReasonAsMessage() {
        ProblemDetail detail = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
        );

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getDetail()).isEqualTo("Email already exists");
        assertThat(detail.getProperties()).containsEntry("message", "Email already exists");
    }

    @Test
    void validationErrorsAreAggregatedIntoReadableDetail() throws NoSuchMethodException {
        RegisterRequest request = new RegisterRequest("", "", "", null);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "registerRequest");
        bindingResult.addError(new FieldError(
                "registerRequest",
                "email",
                request.email(),
                false,
                null,
                null,
                "must be a well-formed email address"
        ));
        bindingResult.addError(new FieldError(
                "registerRequest",
                "password",
                request.password(),
                false,
                null,
                null,
                "size must be between 8 and 72"
        ));

        Method method = ValidationFixture.class.getDeclaredMethod("register", RegisterRequest.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0),
                bindingResult
        );

        ProblemDetail detail = handler.handleMethodArgumentNotValid(exception);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getTitle()).isEqualTo("Validation failed");
        assertThat(detail.getDetail())
                .isEqualTo("email: must be a well-formed email address; password: size must be between 8 and 72");
        assertThat(detail.getProperties()).containsEntry("message", detail.getDetail());
        assertThat(detail.getProperties()).containsKey("violations");
        assertThat(detail.getProperties().get("violations"))
                .isEqualTo(List.of(
                        java.util.Map.of("field", "email", "message", "must be a well-formed email address"),
                        java.util.Map.of("field", "password", "message", "size must be between 8 and 72")
                ));
    }

    @Test
    void unexpectedExceptionsReturnSafeMessage() {
        ProblemDetail detail = handler.handleUnexpectedException(new IllegalStateException("boom"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(detail.getTitle()).isEqualTo("Unexpected server error");
        assertThat(detail.getDetail()).isEqualTo("An unexpected server error occurred. Please try again.");
        assertThat(detail.getProperties()).containsEntry(
                "message",
                "An unexpected server error occurred. Please try again."
        );
    }

    private static final class ValidationFixture {

        @SuppressWarnings("unused")
        private void register(RegisterRequest request) {
        }
    }
}
