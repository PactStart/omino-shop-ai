package com.omino.shopai.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 联盟出站的本地每秒配额。每次调用现读，改 yml 后重启生效。
 * Nacos 接到本模块之后，给这个类加上 RefreshScope，数字就可以热更新。
 */
@Component
@ConfigurationProperties(prefix = "union.limit")
public class UnionLimitProperties {

    /**
     * 键是平台，或平台加接口。例如 {@code taobao}、{@code taobao:order.details}。
     * 缺省和 0 都不在本地扣配额。
     */
    private Map<String, Integer> qps = new LinkedHashMap<>();

    /**
     * 当前配额表。
     *
     * @return 平台或接口到每秒次数的映射
     */
    public Map<String, Integer> getQps() {
        return qps;
    }

    /**
     * 换上配置里的配额表。空表按不限制处理。
     *
     * @param qps 平台或接口到每秒次数的映射
     */
    public void setQps(Map<String, Integer> qps) {
        this.qps = qps == null ? new LinkedHashMap<>() : qps;
    }
}
