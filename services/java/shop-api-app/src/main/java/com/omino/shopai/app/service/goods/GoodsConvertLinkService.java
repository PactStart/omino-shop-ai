package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.adapter.platform.PlatformCallException;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.stereotype.Service;
/**
 * GoodsConvertLinkService 编排用例，事务和校验放在这里。
 */

@Service
public class GoodsConvertLinkService {

    private final GoodsSnapshotStore snapshots;
    private final GoodsGateStore gate;
    private final LinkIssuer issuer;
    private final UnionRelation relations;
/**
 * 点击后才转链。未授权或未安装则拒绝。
 */

    public GoodsConvertLinkService(
            GoodsSnapshotStore snapshots,
            GoodsGateStore gate,
            LinkIssuer issuer,
            UnionRelation relations) {
        this.snapshots = snapshots;
        this.gate = gate;
        this.issuer = issuer;
        this.relations = relations;
    }
/**
 * 转换链接。
 */

    public ConvertedLink convert(
            String userId,
            String goodsId,
            String platform,
            String platformGoodsId,
            Boolean preferApp) {
        String resolved = platform;
        if ((resolved == null || resolved.isBlank()) && goodsId != null && !goodsId.isBlank()) {
            resolved = snapshots.findById(goodsId)
                    .map(GoodsSnapshot::getPlatform)
                    .map(Platform::code)
                    .orElse(null);
        }
        GoodsQueryService.requirePlatform(resolved);
        if (!gate.authorized(userId, resolved)) {
            throw new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH");
        }
        String promotionId = relations.find(userId, UnionIds.source(resolved))
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH"));
        GoodsSnapshot goods = resolve(goodsId, resolved, platformGoodsId);
        String platformCode = goods.getPlatform() == null ? null : goods.getPlatform().code();
        boolean openInApp = !Boolean.FALSE.equals(preferApp) && gate.installed(userId, platformCode);
        ConvertedLink link;
        try {
            link = issuer.issue(goods, openInApp, userId, promotionId);
        } catch (PlatformCallException ex) {
            throw new ApiBusinessException(ErrorCodes.UPSTREAM_PLATFORM, "平台查询失败");
        }
        gate.click(userId, platformCode, goods.getId(), goods.getPlatformGoodsId());
        return link;
    }
/**
 * 从商品号或请求里的平台得到枚举。认不出则拒绝。
 */

    private GoodsSnapshot resolve(String goodsId, String platform, String platformGoodsId) {
        if (goodsId != null && !goodsId.isBlank()) {
            return snapshots.findById(goodsId)
                    .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在"));
        }
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "商品不能为空");
        }
        return snapshots.findByPlatform(platform, platformGoodsId)
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "商品不存在"));
    }
}
