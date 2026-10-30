package com.omino.shopai.support;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;

import java.util.List;
/**
 * 八个提示词键。缺一则这次对话不可用。
 */

public final class AgentPrompts {

    public static final List<String> KEYS = List.of(
            "intent.router",
            "clarify",
            "reply.shop",
            "reply.account",
            "reply.order",
            "reply.earn",
            "reply.support",
            "reply.chitchat");

    private AgentPrompts() {
    }
/**
 * 只接受八个固定键。未知键按不存在拒绝。
 */

    public static void requireKey(String key) {
        if (!KEYS.contains(key)) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "提示词不存在");
        }
    }
/**
 * 正文不能为空。空白会被当成没配提示词。
 */

    public static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "正文不能为空");
        }
        return content.trim();
    }
}
