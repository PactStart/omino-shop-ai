package com.omino.shopai.app.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.PddGoodsImpl;
import com.omino.shopai.app.adapter.platform.TaobaoGoodsImpl;
import com.omino.shopai.app.adapter.platform.VipGoodsImpl;
import com.omino.shopai.app.dto.UnionAuthDto;
import com.omino.shopai.app.service.goods.IGoodsGate;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.enums.AuthStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.exception.PlatformCallException;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * 消费者查询和取消联盟授权。京东不向官方询问。
 */
@Service
public class UnionAuthService {

    private static final int STATE_TTL_SECONDS = 1800;

    private final PlatformAuthorizationMapper authorizations;
    private final IGoodsGate gate;
    private final TaobaoGoodsImpl taobao;
    private final PddGoodsImpl pdd;
    private final VipGoodsImpl vip;
    private final StringRedisTemplate redis;
    private final String stateSecret;

    /**
     * 授权状态以本表为准，拼多多和唯品会再问官方。
     *
     * @param authorizations 授权表
     * @param gate 推广身份缓存
     * @param taobao 淘宝授权链接和备案
     * @param pdd 拼多多备案查询和授权页
     * @param vip 唯品会授权检查、备案链接和解绑
     * @param redis 淘宝授权码去重；测试可以不传
     * @param stateSecret 给淘宝 state 签名，与访问令牌同一把密钥
     */
    public UnionAuthService(
            PlatformAuthorizationMapper authorizations,
            IGoodsGate gate,
            TaobaoGoodsImpl taobao,
            PddGoodsImpl pdd,
            VipGoodsImpl vip,
            StringRedisTemplate redis,
            @Value("${JWT_ACCESS_SECRET:}") String stateSecret) {
        this.authorizations = authorizations;
        this.gate = gate;
        this.taobao = taobao;
        this.pdd = pdd;
        this.vip = vip;
        this.redis = redis;
        this.stateSecret = stateSecret;
    }

    /**
     * 查询一个平台。未授权时带上一条可打开的链接。
     *
     * @param userId 当前用户
     * @param platformCode 平台码
     * @return 授权状态；不含推广身份
     */
    public UnionAuthDto status(String userId, String platformCode) {
        Platform platform = requirePlatform(platformCode);
        try {
            return switch (platform) {
                case JD -> new UnionAuthDto(platform.code(), false, true, null);
                case TAOBAO -> taobaoStatus(userId);
                case PDD -> pddStatus(userId);
                case VIP -> vipStatus(userId);
            };
        } catch (PlatformCallException ex) {
            throw upstream();
        }
    }

    /**
     * 取消一个平台的授权。京东直接拒绝。
     *
     * @param userId 当前用户
     * @param platformCode 平台码
     * @return 取消后的状态；不再附带授权链接
     */
    public UnionAuthDto revoke(String userId, String platformCode) {
        Platform platform = requirePlatform(platformCode);
        if (platform == Platform.JD) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "京东不需要授权");
        }
        try {
            return switch (platform) {
                case TAOBAO -> revokeLocal(userId, platform, true);
                case PDD -> revokeLocal(userId, platform, false);
                case VIP -> revokeVip(userId);
                case JD -> throw new ApiBusinessException(ErrorCodes.VALIDATION, "京东不需要授权");
            };
        } catch (PlatformCallException ex) {
            throw upstream();
        }
    }

    /**
     * 淘宝浏览器回调。state 通过后才换票备案。
     *
     * @param code 授权码
     * @param state 签发授权链接时写入的签名
     * @return 备案成功后的淘宝授权状态
     */
    public UnionAuthDto completeTaobao(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权码不能为空");
        }
        String userId = userIdOf(state);
        try {
            String relation = filedRelation(code);
            write(userId, Platform.TAOBAO, AuthStatus.AUTHORIZED, relation, true);
            return new UnionAuthDto(Platform.TAOBAO.code(), true, true, null);
        } catch (PlatformCallException ex) {
            throw upstream();
        }
    }

    /**
     * 淘宝没有官方的「是否已授权」查询，只认本表。
     *
     * @param userId 当前用户
     * @return 已备案则不再给链接
     */
    private UnionAuthDto taobaoStatus(String userId) {
        PlatformAuthorization row = load(userId, Platform.TAOBAO);
        if (row != null && row.getStatus() == AuthStatus.AUTHORIZED && present(row.getRelationId())) {
            return new UnionAuthDto(Platform.TAOBAO.code(), true, true, null);
        }
        return new UnionAuthDto(Platform.TAOBAO.code(), true, false, taobao.authorizeUrl(sign(userId)));
    }

    /**
     * 拼多多的备案挂在京东推广身份上。没有这条身份就不去问官方。
     *
     * @param userId 当前用户
     * @return 未备案时带授权链接
     */
    private UnionAuthDto pddStatus(String userId) {
        String pid = gate.relationId(userId, Platform.JD.code())
                .filter(UnionAuthService::present)
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.VALIDATION, "京东推广身份未配置"));
        if (pdd.bound(userId, pid)) {
            write(userId, Platform.PDD, AuthStatus.AUTHORIZED, null, false);
            return new UnionAuthDto(Platform.PDD.code(), true, true, null);
        }
        // 官方已解绑时，本表不能继续显示已授权，否则转链还会放行。
        PlatformAuthorization row = load(userId, Platform.PDD);
        if (row != null && row.getStatus() == AuthStatus.AUTHORIZED) {
            write(userId, Platform.PDD, AuthStatus.REVOKED, null, false);
        }
        return new UnionAuthDto(Platform.PDD.code(), true, false, pdd.authUrl(userId, pid));
    }

    /**
     * 唯品会的 openId 一旦生成就固定，取消授权也不换。
     *
     * @param userId 当前用户
     * @return 未授权时带备案链接
     */
    private UnionAuthDto vipStatus(String userId) {
        String openId = openId(userId);
        if (vip.bound(openId)) {
            write(userId, Platform.VIP, AuthStatus.AUTHORIZED, openId, true);
            return new UnionAuthDto(Platform.VIP.code(), true, true, null);
        }
        PlatformAuthorization row = load(userId, Platform.VIP);
        if (row != null && row.getStatus() == AuthStatus.AUTHORIZED) {
            write(userId, Platform.VIP, AuthStatus.REVOKED, openId, true);
        }
        return new UnionAuthDto(Platform.VIP.code(), true, false, vip.authUrl(userId, openId));
    }

    /**
     * 没有 openId 时先落一条 none，保证检查和链接用同一个身份。
     *
     * @param userId 当前用户
     * @return 本表里的 openId
     */
    private String openId(String userId) {
        PlatformAuthorization row = load(userId, Platform.VIP);
        if (row != null && present(row.getRelationId())) {
            return row.getRelationId();
        }
        write(userId, Platform.VIP, AuthStatus.NONE, userId, true);
        return userId;
    }

    /**
     * 淘宝清空推广身份。拼多多只改状态，下次查询仍以官方为准。
     *
     * @param userId 当前用户
     * @param platform 淘宝或拼多多
     * @param clearRelation 为真时把 relation_id 写成空
     * @return 未授权且不带链接
     */
    private UnionAuthDto revokeLocal(String userId, Platform platform, boolean clearRelation) {
        PlatformAuthorization row = load(userId, platform);
        if (row == null || row.getStatus() != AuthStatus.AUTHORIZED) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "尚未授权，不能取消");
        }
        write(userId, platform, AuthStatus.REVOKED, "", clearRelation);
        return new UnionAuthDto(platform.code(), true, false, null);
    }

    /**
     * 先官方解绑。失败则本表保持原样，避免客户端以为已经取消。
     *
     * @param userId 当前用户
     * @return 未授权且不带链接
     */
    private UnionAuthDto revokeVip(String userId) {
        PlatformAuthorization row = load(userId, Platform.VIP);
        if (row == null || row.getStatus() != AuthStatus.AUTHORIZED || !present(row.getRelationId())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "尚未授权，不能取消");
        }
        vip.unbind(row.getRelationId());
        write(userId, Platform.VIP, AuthStatus.REVOKED, row.getRelationId(), true);
        return new UnionAuthDto(Platform.VIP.code(), true, false, null);
    }

    /**
     * 同一授权码十分钟内只向淘宝提交一次。
     *
     * @param code 授权码
     * @return 备案得到的推广身份
     */
    private String filedRelation(String code) {
        String key = "union:taobao:code:" + sha256(code);
        if (redis != null) {
            String cached = redis.opsForValue().get(key);
            if (present(cached)) {
                return cached;
            }
        }
        String relation = taobao.filing(code);
        if (redis != null) {
            redis.opsForValue().set(key, relation, Duration.ofMinutes(10));
        }
        return relation;
    }

    /**
     * 写入授权行，并丢掉推广身份缓存。
     *
     * @param userId 当前用户
     * @param platform 平台
     * @param status 新状态
     * @param relationId 推广身份；不替换时忽略
     * @param replaceRelation 为真时覆盖 relation_id，空串表示清空
     */
    private void write(String userId, Platform platform, AuthStatus status, String relationId, boolean replaceRelation) {
        PlatformAuthorization row = load(userId, platform);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (row == null) {
            row = new PlatformAuthorization();
            row.setId(Ids.ulid());
            row.setUserId(userId);
            row.setPlatform(platform);
            row.setStatus(status);
            row.setRelationId(replaceRelation ? blankToNull(relationId) : null);
            if (status == AuthStatus.AUTHORIZED) {
                row.setAuthorizedAt(now);
            }
            if (status == AuthStatus.REVOKED) {
                row.setRevokedAt(now);
            }
            authorizations.insert(row);
        } else {
            LambdaUpdateWrapper<PlatformAuthorization> update = new LambdaUpdateWrapper<PlatformAuthorization>()
                    .eq(PlatformAuthorization::getId, row.getId())
                    .set(PlatformAuthorization::getStatus, status);
            if (replaceRelation) {
                update.set(PlatformAuthorization::getRelationId, blankToNull(relationId));
            }
            if (status == AuthStatus.AUTHORIZED) {
                update.set(PlatformAuthorization::getAuthorizedAt, now);
                update.set(PlatformAuthorization::getRevokedAt, null);
            }
            if (status == AuthStatus.REVOKED) {
                update.set(PlatformAuthorization::getRevokedAt, now);
            }
            authorizations.update(null, update);
            row.setStatus(status);
            if (replaceRelation) {
                row.setRelationId(blankToNull(relationId));
            }
        }
        gate.forgetRelation(userId, platform.code());
    }

    /**
     * 按用户和平台取唯一一行。
     *
     * @param userId 当前用户
     * @param platform 平台
     * @return 授权行；没有则空
     */
    private PlatformAuthorization load(String userId, Platform platform) {
        return authorizations.selectOne(new LambdaQueryWrapper<PlatformAuthorization>()
                .eq(PlatformAuthorization::getUserId, userId)
                .eq(PlatformAuthorization::getPlatform, platform));
    }

    /**
     * 给回调带上用户和过期时间。签名不对就不能备案。
     *
     * @param userId 当前用户
     * @return userId.过期秒.hmac
     */
    private String sign(String userId) {
        requireSecret();
        long exp = Instant.now().getEpochSecond() + STATE_TTL_SECONDS;
        String body = userId + "." + exp;
        return body + "." + hmac(body);
    }

    /**
     * 验签并取出用户。过期或被改过都拒绝。
     *
     * @param state 回调里的 state
     * @return 签发时的用户
     */
    private String userIdOf(String state) {
        requireSecret();
        if (state == null) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权状态无效");
        }
        int last = state.lastIndexOf('.');
        if (last <= 0 || last == state.length() - 1) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权状态无效");
        }
        String body = state.substring(0, last);
        String sig = state.substring(last + 1);
        if (!MessageDigest.isEqual(hmac(body).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权状态无效");
        }
        int split = body.indexOf('.');
        if (split <= 0 || split == body.length() - 1) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权状态无效");
        }
        long exp;
        try {
            exp = Long.parseLong(body.substring(split + 1));
        } catch (NumberFormatException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权状态无效");
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "授权状态无效");
        }
        return body.substring(0, split);
    }

    /**
     * 密钥没配时不能签发或核验 state。
     */
    private void requireSecret() {
        if (stateSecret == null || stateSecret.isBlank()) {
            throw new ApiBusinessException(ErrorCodes.INTERNAL, "内部错误");
        }
    }

    /**
     * 用访问令牌密钥做 HMAC。只比较十六进制摘要。
     *
     * @param body 待签名正文
     * @return 小写十六进制
     */
    private String hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ApiBusinessException(ErrorCodes.INTERNAL, "内部错误");
        }
    }

    /**
     * 授权码不进 Redis 键，只存摘要。
     *
     * @param code 授权码
     * @return 十六进制摘要
     */
    private static String sha256(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ApiBusinessException(ErrorCodes.INTERNAL, "内部错误");
        }
    }

    /**
     * 只接受四个平台。
     *
     * @param platformCode 平台码
     * @return 平台枚举
     */
    private static Platform requirePlatform(String platformCode) {
        try {
            Platform platform = Platform.fromCode(platformCode);
            if (platform == null) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
            }
            return platform;
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "平台不正确");
        }
    }

    /**
     * 官方失败统一成 50010，不带官方原文。
     *
     * @return 平台失败
     */
    private static ApiBusinessException upstream() {
        return new ApiBusinessException(ErrorCodes.UPSTREAM_PLATFORM, "平台查询失败");
    }

    /**
     * 空白推广身份按没有处理。
     *
     * @param value 待检查的字符串
     * @return 非空白时为真
     */
    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 清空推广身份时写成 null，避免空串被当成有效身份。
     *
     * @param value 推广身份
     * @return 空白则空
     */
    private static String blankToNull(String value) {
        return present(value) ? value : null;
    }
}
