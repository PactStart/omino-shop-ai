package com.omino.shopai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.enums.MallActionType;
import com.omino.shopai.enums.MallZoneCode;
import com.omino.shopai.exception.ApiBusinessException;

import java.util.List;
import java.util.Set;

/**
 * 商城条目的版位和点击参数校验。两个产出物共用，避免各写一套类型码。
 */
public final class MallActions {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PAGES = Set.of("mallSearch", "chat", "orders", "me", "invite", "credits");
    private static final Set<String> CLIENTS = Set.of("android", "ios", "h5");

    private MallActions() {
    }

    /**
     * 校验一条商城条目的版位、分类、标题和点击参数。
     *
     * @param zone 版位码
     * @param categoryId 分类主键，banner 必须为空
     * @param title 标题，金刚区必填
     * @param image 图片地址或对象键
     * @param actionType 点击类型码
     * @param action 点击参数，必须是对象
     */
    public static void checkEntry(
            String zone, String categoryId, String title, String image, String actionType, JsonNode action) {
        MallZoneCode place = zoneOf(zone);
        if (image == null || image.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "图片不能为空");
        }
        if (place == MallZoneCode.NAV) {
            if (categoryId == null || categoryId.isBlank()) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "请选择分类");
            }
            if (title == null || title.isBlank()) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "名称不能为空");
            }
        } else if (categoryId != null && !categoryId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "轮播图不能归类");
        }
        if (title != null && title.length() > 32) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "名称过长");
        }
        checkAction(actionOf(actionType), action == null || action.isNull() ? JSON.createObjectNode() : action);
    }

    /**
     * 把端列表收成 JSON 数组。空表示全端。
     *
     * @param clients 端列表，可空
     * @return JSON 数组文本
     */
    public static String clients(List<String> clients) {
        if (clients == null || clients.isEmpty()) {
            return "[]";
        }
        for (String client : clients) {
            if (!CLIENTS.contains(client)) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "端不正确");
            }
        }
        return write(clients);
    }

    /**
     * 把渠道列表收成 JSON 数组。空表示全渠道。
     *
     * @param channels 渠道列表，可空
     * @return JSON 数组文本
     */
    public static String channels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return "[]";
        }
        for (String channel : channels) {
            if (channel == null || !channel.matches("[A-Za-z0-9_-]{1,32}")) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "渠道不正确");
            }
        }
        return write(channels);
    }

    /**
     * 判断 JSON 数组是否放过当前值。空数组表示不限制。
     *
     * @param jsonList JSON 数组文本，可空
     * @param value 当前端或渠道
     * @return 不限制或包含该值时为真
     */
    public static boolean allows(String jsonList, String value) {
        if (jsonList == null || jsonList.isBlank() || jsonList.trim().equals("[]")) {
            return true;
        }
        return jsonList.contains("\"" + value + "\"");
    }

    /**
     * 把点击参数文本读成对象。坏数据返回空对象，调用方可以跳过这一条。
     *
     * @param raw JSON 对象文本
     * @return 对象；解析失败时是空对象
     */
    public static ObjectNode readAction(String raw) {
        if (raw == null || raw.isBlank()) {
            return JSON.createObjectNode();
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (node instanceof ObjectNode object) {
                return object;
            }
        } catch (Exception ignored) {
            // 坏的点击参数不让整页失败，这一条由调用方丢掉。
        }
        return null;
    }

    /**
     * 把对象写成紧凑 JSON。
     *
     * @param value 要写入的值
     * @return JSON 文本
     */
    public static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "点击参数不正确");
        }
    }

    /**
     * 识别版位。不认识的码拒绝。
     *
     * @param zone 版位码
     * @return 版位
     */
    public static MallZoneCode zoneOf(String zone) {
        try {
            MallZoneCode place = MallZoneCode.fromCode(zone);
            if (place == null) {
                throw new IllegalArgumentException("blank");
            }
            return place;
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "版位不正确");
        }
    }

    /**
     * 识别点击类型。不认识的码拒绝。
     *
     * @param actionType 类型码
     * @return 点击类型
     */
    private static MallActionType actionOf(String actionType) {
        try {
            MallActionType type = MallActionType.fromCode(actionType);
            if (type == null) {
                throw new IllegalArgumentException("blank");
            }
            return type;
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "点击类型不正确");
        }
    }

    /**
     * 按类型检查参数里该有的字段。
     *
     * @param type 点击类型
     * @param action 点击参数
     */
    private static void checkAction(MallActionType type, JsonNode action) {
        if (!action.isObject()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "点击参数不正确");
        }
        switch (type) {
            case NONE -> {
                // 不可点，不要求字段。
            }
            case PAGE -> {
                String page = text(action, "page");
                if (!PAGES.contains(page)) {
                    throw new ApiBusinessException(ErrorCodes.VALIDATION, "内部页面不正确");
                }
            }
            case GOODS -> {
                boolean byId = text(action, "goodsId") != null;
                boolean byPlatform = text(action, "platform") != null && text(action, "platformGoodsId") != null;
                if (!byId && !byPlatform) {
                    throw new ApiBusinessException(ErrorCodes.VALIDATION, "商品不能为空");
                }
            }
            case SEARCH -> {
                if (text(action, "keyword") == null) {
                    throw new ApiBusinessException(ErrorCodes.VALIDATION, "关键词不能为空");
                }
            }
            case MINIPROGRAM -> {
                if (text(action, "userName") == null && text(action, "appId") == null) {
                    throw new ApiBusinessException(ErrorCodes.VALIDATION, "小程序标识不能为空");
                }
            }
            case DEEPLINK -> {
                if (text(action, "scheme") == null && text(action, "universalLink") == null) {
                    throw new ApiBusinessException(ErrorCodes.VALIDATION, "拉起地址不能为空");
                }
                http(text(action, "universalLink"), "链接不正确");
                http(text(action, "fallbackUrl"), "链接不正确");
            }
            case WEBVIEW -> {
                String url = text(action, "url");
                if (url == null) {
                    throw new ApiBusinessException(ErrorCodes.VALIDATION, "链接不正确");
                }
                http(url, "链接不正确");
            }
            default -> throw new ApiBusinessException(ErrorCodes.VALIDATION, "点击类型不正确");
        }
    }

    /**
     * 读取对象里的非空字符串。
     *
     * @param node 对象
     * @param field 字段名
     * @return 去掉空白后的值；没有时为空
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 有值时必须是 http 或 https。
     *
     * @param url 链接，可空
     * @param message 不合法时的提示
     */
    private static void http(String url, String message) {
        if (url == null) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, message);
        }
    }
}
