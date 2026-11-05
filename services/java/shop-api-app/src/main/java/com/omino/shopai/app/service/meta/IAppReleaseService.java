package com.omino.shopai.app.service.meta;

import com.omino.shopai.entity.Announcement;
import com.omino.shopai.entity.AppRelease;

import java.util.List;
import java.util.Map;

/**
 * 版本发布、检查更新、运行时配置、公告和协议地址。
 */
public interface IAppReleaseService {

    /**
     * 发布一个版本。版本号不在日期范围内，或同端同渠道已有该版本号，则拒绝。
     *
     * @param release 待发布的版本
     * @return 已写入的版本
     */
    AppRelease publish(AppRelease release);

    /**
     * 按端和渠道检查是否有比当前版本更新的已发布版本。本渠道没有时改用该端的默认渠道。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 无更新时只有 action=none；有更新时带版本、说明和下载地址
     */
    Map<String, Object> checkUpdate(String clientType, String channel, int versionCode);

    /**
     * 把命中端、渠道和版本区间的配置按优先级从低到高浅合并。后写入的键盖过先写入的键。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 合并后的配置
     */
    Map<String, Object> runtimeConfig(String clientType, String channel, int versionCode);

    /**
     * 按端、渠道、版本和时间筛出当前该看到的已发布公告。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @param versionCode 当前版本号
     * @return 当前可见的公告
     */
    List<Announcement> announcements(String clientType, String channel, int versionCode);

    /**
     * 读取用户协议和隐私政策地址。地址来自环境，可为空字符串。
     *
     * @return agreementUrl 与 privacyUrl
     */
    Map<String, String> legal();
}
