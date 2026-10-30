package com.omino.shopai.app.service.goods;

import com.omino.shopai.enums.Platform;
import com.omino.shopai.enums.AuthStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.AppUserInstalledApp;
import com.omino.shopai.entity.ClickEvent;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.mapper.AppUserInstalledAppMapper;
import com.omino.shopai.mapper.ClickEventMapper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisGoodsGateStore implements GoodsGateStore {

    private final PlatformAuthorizationMapper authorizations;
    private final AppUserInstalledAppMapper installedApps;
    private final ClickEventMapper clicks;
/**
 * 联盟授权、安装态和点击记录的 MyBatis 访问。
 */

    public MybatisGoodsGateStore(
            PlatformAuthorizationMapper authorizations,
            AppUserInstalledAppMapper installedApps,
            ClickEventMapper clicks) {
        this.authorizations = authorizations;
        this.installedApps = installedApps;
        this.clicks = clicks;
    }
/**
 * 该用户是否已授权这个平台。
 */

    @Override
    public boolean authorized(String userId, String platform) {
        PlatformAuthorization row = authorizations.selectOne(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId)
                .eq(PlatformAuthorization::getPlatform, platform));
        return row != null && AuthStatus.AUTHORIZED == row.getStatus();
    }
/**
 * 端上是否报过已安装对应 APP。
 */

    @Override
    public boolean installed(String userId, String platform) {
        AppUserInstalledApp row = installedApps.selectOne(new LambdaQueryWrapper<AppUserInstalledApp>()
                .eq(AppUserInstalledApp::getUserId, userId)
                .eq(AppUserInstalledApp::getPlatform, platform));
        return row != null && Integer.valueOf(1).equals(row.getInstalled());
    }
/**
 * 记一次转链点击，供后续归因。
 */

    @Override
    public void click(String userId, String platform, String goodsId, String platformGoodsId) {
        ClickEvent event = new ClickEvent();
        event.setId(Ids.ulid());
        event.setUserId(userId);
        event.setPlatform(Platform.fromCode(platform));
        event.setGoodsId(goodsId);
        event.setPlatformGoodsId(platformGoodsId);
        event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        clicks.insert(event);
    }
/**
 * 列出点击，供内部核对。
 */

    @Override
    public int clicks(String userId) {
        Long count = clicks.selectCount(new LambdaQueryWrapper<ClickEvent>().eq(ClickEvent::getUserId, userId));
        return count == null ? 0 : count.intValue();
    }
}
