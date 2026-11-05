package com.omino.shopai.app.adapter.platform.limitrate;

import com.taobao.api.TaobaoResponse;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 淘宝账号维度限流。{@code subCode=9100} 在响应体里，SDK 不会因此抛异常。
 */
@Component
public class TaobaoLimitSignal implements UnionLimitSignal {

    private static final String CODE = "9100";
    private static final Pattern THROWN = Pattern.compile("sub_?code[\"\\s:=]+9100", Pattern.CASE_INSENSITIVE);

    /**
     * 只看淘宝。
     *
     * @return taobao
     */
    @Override
    public String platform() {
        return "taobao";
    }

    /**
     * 响应里的 subCode 优先。客户端若把同一段错误抛出来，再按 subCode 文本认一次。
     *
     * @param response 官方响应，可空
     * @param error 客户端异常，可空
     * @return 9100 和说明；不是这次限流则空
     */
    @Override
    public UnionLimitHit hit(Object response, Exception error) {
        if (response instanceof TaobaoResponse taobao && CODE.equals(taobao.getSubCode())) {
            return new UnionLimitHit(CODE, taobao.getSubMsg());
        }
        String message = error == null ? null : error.getMessage();
        if (message != null && THROWN.matcher(message).find()) {
            return new UnionLimitHit(CODE, message);
        }
        return null;
    }
}
