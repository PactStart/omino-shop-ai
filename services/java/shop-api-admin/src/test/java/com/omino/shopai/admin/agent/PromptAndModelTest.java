package com.omino.shopai.admin.agent;

import com.omino.shopai.admin.service.agent.AdminModelService;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.support.AgentPrompts;
import com.omino.shopai.support.LlmKeyCipher;
import com.omino.shopai.support.ModelSecret;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAndModelTest {

    @Test
    void defaultPromptsIncludeTheRequiredSections() {
        for (String text : AgentPrompts.DEFAULTS.values()) {
            for (String title : new String[] {"角色：", "背景：", "上下文：", "目标：", "输出格式：", "约束：", "示例："}) {
                assertTrue(text.contains(title));
            }
        }
        assertTrue(AgentPrompts.DEFAULTS.get("intent.router").contains("shop.search"));
        assertTrue(AgentPrompts.DEFAULTS.get("reply.shop").contains("不要新增数字"));
    }

    @Test
    void missingPromptKeepsStoredTextAndFillsTheRest() {
        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("intent.router", "  运营写的路由  ");
        stored.put("reply.shop", "  ");
        Map<String, String> filled = AgentPrompts.fill(stored);
        assertEquals(8, filled.size());
        assertEquals("运营写的路由", filled.get("intent.router"));
        assertEquals(AgentPrompts.DEFAULTS.get("reply.shop"), filled.get("reply.shop"));
        assertEquals(AgentPrompts.DEFAULTS.get("clarify"), filled.get("clarify"));
    }

    @Test
    void unknownPromptAndBlankContentAreRejected() {
        ApiBusinessException missing = assertThrows(ApiBusinessException.class, () -> AgentPrompts.requireKey("reply.other"));
        assertEquals(ErrorCodes.NOT_FOUND, missing.code());
        ApiBusinessException blank = assertThrows(ApiBusinessException.class, () -> AgentPrompts.requireContent("  "));
        assertEquals(ErrorCodes.VALIDATION, blank.code());
    }

    @Test
    void modelViewHidesTheKeyAndAnOmittedUpdateKeepsIt() {
        LlmKeyCipher cipher = new LlmKeyCipher("0123456789abcdef0123456789abcdef");
        byte[] encoded = cipher.encrypt("sk-test-secret-1234");
        ModelSecret.ModelKeyView view = ModelSecret.view(encoded, cipher);
        assertTrue(view.apiKeySet());
        assertEquals("1234", view.apiKeyMask());
        assertFalse(view.toString().contains("sk-test-secret"));
        assertArrayEquals(encoded, ModelSecret.keepOrReplace(encoded, null, cipher));
        assertArrayEquals(encoded, ModelSecret.keepOrReplace(encoded, " ", cipher));
    }

    @Test
    void duplicateModelNameNamesTheFieldAndPromptsHaveNoCreateOrDelete() throws Exception {
        ApiBusinessException duplicate = assertThrows(ApiBusinessException.class, () -> AdminModelService.rejectDuplicate(1));
        assertEquals(ErrorCodes.VALIDATION, duplicate.code());
        assertEquals("name", duplicate.field());
        var response = new ApiExceptionHandler().business(duplicate);
        assertEquals("name", response.getBody().error().field());
        Class<?> prompts = Class.forName("com.omino.shopai.admin.web.AdminAgentPromptController");
        for (Method method : prompts.getDeclaredMethods()) {
            assertNull(method.getAnnotation(PostMapping.class));
            assertNull(method.getAnnotation(DeleteMapping.class));
        }
    }
}
