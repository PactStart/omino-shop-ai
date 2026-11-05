package com.omino.shopai.support;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.enums.RichTextFormat;
import com.omino.shopai.exception.ApiBusinessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * 把公告、站内信和版本说明收成纯文本或有限 HTML。
 */
public final class RichText {

    private static final Safelist ALLOWED = Safelist.none()
            .addTags("p", "br", "strong", "em", "a", "ul", "ol", "li")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https");

    private RichText() {
    }

    /**
     * 清洗后的正文。
     *
     * @param format 纯文本或 HTML；空则按纯文本
     * @param body 入库后的正文
     */
    public record Normalized(RichTextFormat format, String body) {
    }

    /**
     * 按格式整理正文。HTML 只留段落、换行、加粗、斜体、列表和 http/https 链接。
     *
     * @param format 请求里的格式，可空
     * @param body 原始正文
     * @return 可入库的格式和正文
     */
    public static Normalized normalize(RichTextFormat format, String body) {
        if (body == null || body.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "正文不能为空");
        }
        RichTextFormat kind = format == null ? RichTextFormat.PLAIN : format;
        if (kind == RichTextFormat.PLAIN) {
            return new Normalized(RichTextFormat.PLAIN, body.trim());
        }
        String cleaned = clean(body);
        if (Jsoup.parse(cleaned).text().isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "正文不能为空");
        }
        return new Normalized(RichTextFormat.HTML, cleaned);
    }

    /**
     * 丢掉白名单以外的标签和属性，留下其中的文字。
     *
     * @param html 运营提交的 HTML
     * @return 清洗后的片段
     */
    private static String clean(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        document.select("div").tagName("p");
        document.select("b").tagName("strong");
        document.select("i").tagName("em");
        Document safe = Jsoup.parseBodyFragment(Jsoup.clean(document.body().html(), ALLOWED));
        for (Element link : safe.select("a")) {
            if (!link.hasAttr("href")) {
                link.unwrap();
            }
        }
        return safe.body().html().trim();
    }
}
