package com.omino.shopai.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpAccessLogFilterTest {

    @Test
    void logsBusinessCodeAndLeavesTheBodyIntact() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            HttpAccessLogFilter filter = new HttpAccessLogFilter();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/internal/v1/goods/detail");
            request.addHeader("X-Internal-Token", "secret-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setCharacterEncoding("UTF-8");
            String body = "{\"code\":40400,\"message\":\"商品不存在\",\"data\":null}";
            FilterChain chain = (req, res) -> res.getWriter().write(body);

            filter.doFilter(request, response, chain);

            assertEquals(body, response.getContentAsString());
            assertEquals(1, appender.list.size());
            String line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.contains("POST /api/app/internal/v1/goods/detail"));
            assertTrue(line.contains("status=200"));
            assertTrue(line.contains("code=40400"));
            assertTrue(line.contains("message=商品不存在"));
            assertTrue(line.contains("query=-"));
            assertTrue(line.contains("body=-"));
            assertFalse(line.contains("secret-token"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    @Test
    void logsQueryAndBodyWhenTheCallFails() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            HttpAccessLogFilter filter = new HttpAccessLogFilter();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/internal/v1/goods/detail");
            request.setQueryString("platform=jd&token=secret-token");
            request.setCharacterEncoding("UTF-8");
            request.setContent("{\"platform\":\"jd\",\"password\":\"secret-token\"}".getBytes(StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setCharacterEncoding("UTF-8");
            FilterChain chain = (req, res) -> {
                ServletInputStream input = req.getInputStream();
                input.readAllBytes();
                res.getWriter().write("{\"code\":40400,\"message\":\"商品不存在\",\"data\":null}");
            };

            filter.doFilter(request, response, chain);

            String line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.contains("query=platform=jd&token=***"));
            assertTrue(line.contains("body={\"platform\":\"jd\",\"password\":\"***\"}"));
            assertFalse(line.contains("secret-token"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    @Test
    void omitsQueryAndBodyWhenTheCallSucceeds() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            HttpAccessLogFilter filter = new HttpAccessLogFilter();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/internal/v1/goods/search");
            request.setQueryString("platform=jd");
            request.setCharacterEncoding("UTF-8");
            request.setContent("{\"keyword\":\"耳机\"}".getBytes(StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setCharacterEncoding("UTF-8");
            FilterChain chain = (req, res) -> {
                req.getInputStream().readAllBytes();
                res.getWriter().write("{\"code\":0,\"message\":\"ok\",\"data\":{}}");
            };

            filter.doFilter(request, response, chain);

            String line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.contains("code=0"));
            assertFalse(line.contains("query="));
            assertFalse(line.contains("耳机"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    @Test
    void keepsChineseWhenTheServletCharsetIsLatin1() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            HttpAccessLogFilter filter = new HttpAccessLogFilter();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/v1/goods/detail");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setCharacterEncoding("ISO-8859-1");
            byte[] json = "{\"code\":50000,\"message\":\"内部错误\"}".getBytes(StandardCharsets.UTF_8);
            FilterChain chain = (req, res) -> res.getOutputStream().write(json);

            filter.doFilter(request, response, chain);

            String line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.contains("message=内部错误"));
            assertFalse(line.contains("å"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    @Test
    void skipsActuator() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            HttpAccessLogFilter filter = new HttpAccessLogFilter();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> res.getWriter().write("{\"status\":\"UP\"}"));

            assertTrue(appender.list.isEmpty());
            assertEquals("{\"status\":\"UP\"}", response.getContentAsString());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }
}
