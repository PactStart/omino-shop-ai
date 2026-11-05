package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 公告、站内信和版本说明的正文格式。库存的是 code，不是枚举名。 */
public enum RichTextFormat {
    PLAIN("plain"),
    HTML("html");

    @EnumValue
    @JsonValue
    private final String code;

    RichTextFormat(String code) {
        this.code = code;
    }

    /**
     * 写入数据库和 JSON 的取值。
     *
     * @return plain 或 html
     */
    public String code() {
        return code;
    }

    /**
     * 把库存或请求里的取值还原成枚举。空串视为未填。无法识别时抛出，避免悄悄落成 null。
     *
     * @param code 请求或库存中的取值
     * @return 对应枚举；空串返回 null
     */
    @JsonCreator
    public static RichTextFormat fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (RichTextFormat value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(code);
    }
}
