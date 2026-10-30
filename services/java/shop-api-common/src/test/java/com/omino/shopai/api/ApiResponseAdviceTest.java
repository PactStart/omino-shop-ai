package com.omino.shopai.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApiResponseAdviceTest {

    private final ApiResponseAdvice advice = new ApiResponseAdvice();

    @Test
    void stampsTheRequestTraceOntoTheEnvelope() {
        MockHttpServletRequest servlet = new MockHttpServletRequest();
        servlet.setAttribute(ApiResponse.TRACE_ATTRIBUTE, "trace-1");
        Object stamped = advice.beforeBodyWrite(
                ApiResponse.ok(Map.of("goodsId", "g1")),
                null,
                MediaType.APPLICATION_JSON,
                null,
                new ServletServerHttpRequest(servlet),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        ApiResponse<?> body = (ApiResponse<?>) stamped;
        assertEquals(0, body.code());
        assertEquals("g1", ((Map<?, ?>) body.data()).get("goodsId"));
        assertEquals("trace-1", body.traceId());
        assertFalse(body.ts().isBlank());
    }

    @Test
    void leavesPlainTextCallbacksAlone() {
        String body = "success";
        Object written = advice.beforeBodyWrite(
                body,
                null,
                MediaType.TEXT_PLAIN,
                null,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse()));
        assertSame(body, written);
    }
}
