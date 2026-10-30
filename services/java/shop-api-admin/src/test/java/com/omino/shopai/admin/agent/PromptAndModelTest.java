package com.omino.shopai.admin.agent;

import com.omino.shopai.admin.service.agent.AdminModelService;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.support.AgentPrompts;
import com.omino.shopai.support.LlmKeyCipher;
import com.omino.shopai.support.ModelSecret;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAndModelTest {

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
