package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.ConvertedLinkDto;

import com.omino.shopai.exception.PlatformCallException;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.GoodsSnapshot;
import org.springframework.stereotype.Service;
/**
 * GoodsConvertLinkService 编排用例，事务和校验放在这里。
 */

@Service
public class GoodsConvertLinkService {

    private final IGoodsSnapshot snapshots;
    private final IGoodsGate gate;
    private final ILinkIssuer issuer;

    /**
     * 点击后才转链。未授权或没有推广身份则拒绝。
     *
     * @param snapshots 商品快照
     * @param gate 授权、推广身份和点击
     * @param issuer 向联盟要购买链接
     */
    public GoodsConvertLinkService(
            IGoodsSnapshot snapshots,
            IGoodsGate gate,
            ILinkIssuer issuer) {
        this.snapshots = snapshots;
        this.gate = gate;
        this.issuer = issuer;
    }
/**
 * 转换链接。
 */

    public ConvertedLinkDto convert(
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
        Platform parsed = GoodsQueryService.requirePlatform(resolved);
        // 京东没有用户授权。其它三个平台必须先是已授权，否则不能拿推广身份去转链。
        if (parsed != Platform.JD && !gate.authorized(userId, parsed.code())) {
            throw new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH");
        }
        String promotionId = gate.relationId(userId, parsed.relationPlatform().code())
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NEED_AUTH, "请先完成联盟授权", "NEED_AUTH"));
        GoodsSnapshot goods = resolve(goodsId, resolved, platformGoodsId);
        String platformCode = goods.getPlatform() == null ? null : goods.getPlatform().code();
        boolean openInApp = !Boolean.FALSE.equals(preferApp) && gate.installed(userId, platformCode);
        ConvertedLinkDto link;
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
