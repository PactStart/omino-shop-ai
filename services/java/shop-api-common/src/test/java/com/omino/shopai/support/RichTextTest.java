package com.omino.shopai.support;

import com.omino.shopai.enums.RichTextFormat;
import com.omino.shopai.exception.ApiBusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextTest {

    @Test
    void plainKeepsMarkupAsText() {
        RichText.Normalized text = RichText.normalize(null, "  <b>你好</b>  ");
        assertTrue(text.format() == RichTextFormat.PLAIN);
        assertTrue(text.body().equals("<b>你好</b>"));
    }

    @Test
    void htmlKeepsTheAllowlistAndDropsScripts() {
        String body = RichText.normalize(RichTextFormat.HTML,
                "<p onclick=\"x\">你好</p><script>alert(1)</script><img src=\"x\"><b>粗</b><i>斜</i>").body();
        assertTrue(body.contains("你好"));
        assertTrue(body.contains("<strong>粗</strong>"));
        assertTrue(body.contains("<em>斜</em>"));
        assertFalse(body.toLowerCase().contains("script"));
        assertFalse(body.toLowerCase().contains("onclick"));
        assertFalse(body.toLowerCase().contains("<img"));
    }

    @Test
    void htmlKeepsHttpsLinksAndDropsScriptLinks() {
        String kept = RichText.normalize(RichTextFormat.HTML, "<a href=\"https://example.com/a\">去</a>").body();
        assertTrue(kept.contains("https://example.com/a"));
        String dropped = RichText.normalize(RichTextFormat.HTML, "<a href=\"javascript:alert(1)\">去</a>").body();
        assertFalse(dropped.toLowerCase().contains("javascript"));
        assertTrue(dropped.contains("去"));
    }

    @Test
    void emptyHtmlIsRejected() {
        assertThrows(ApiBusinessException.class, () -> RichText.normalize(RichTextFormat.HTML, "<script>alert(1)</script>"));
        assertThrows(ApiBusinessException.class, () -> RichText.normalize(RichTextFormat.PLAIN, "  "));
    }
}
