package com.omino.shopai.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void validationUses40001() throws Exception {
        Method method = Sample.class.getDeclaredMethod("take", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Sample(), "sample");
        binding.addError(new FieldError("sample", "text", "must not be blank"));
        ResponseEntity<ApiResponse<Void>> response = handler.validation(
                new MethodArgumentNotValidException(parameter, binding));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ErrorCodes.VALIDATION, response.getBody().code());
        assertNull(response.getBody().data());
    }

    @Test
    void unauthenticatedIsHttp401And40100() {
        ResponseEntity<ApiResponse<Void>> response = handler.unauthenticated(new UnauthenticatedException());

        assertEquals(401, response.getStatusCode().value());
        assertEquals(ErrorCodes.UNAUTHENTICATED, response.getBody().code());
    }

    @Test
    void unknownErrorHidesSqlAndSecrets() {
        ResponseEntity<ApiResponse<Void>> response = handler.unknown(
                new RuntimeException("SELECT password FROM app_user WHERE token='secret'"));

        assertEquals(ErrorCodes.INTERNAL, response.getBody().code());
        assertEquals("内部错误", response.getBody().message());
        assertFalse(response.getBody().message().contains("SELECT"));
        assertFalse(response.getBody().message().contains("secret"));
    }

    static class Sample {
        void take(String text) {
        }
    }
}
