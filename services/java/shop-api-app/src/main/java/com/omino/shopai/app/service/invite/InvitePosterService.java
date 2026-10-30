package com.omino.shopai.app.service.invite;

import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.InvitePosterTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
/**
 * InvitePosterService 编排用例，事务和校验放在这里。
 */

@Service
public class InvitePosterService {

    private final IInviteCatalog catalog;
    private final Clock clock;
    private final String landBase;
/**
 * 只下发邀请码、模板和贴码矩形，不在服务端合成海报。
 */

    public InvitePosterService(
            IInviteCatalog catalog,
            Clock clock,
            @Value("${INVITE_LAND_BASE_URL:http://127.0.0.1:5176}") String landBase) {
        this.catalog = catalog;
        this.clock = clock;
        this.landBase = landBase.endsWith("/") ? landBase.substring(0, landBase.length() - 1) : landBase;
    }
/**
 * 读出本人邀请码。停用账号不发落地地址。
 */

    public InviteViewDto invite(String userId) {
        AppUser user = catalog.findById(userId).orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在"));
        return new InviteViewDto(user.getInviteCode(), landBase + "/i/" + user.getInviteCode(), 0);
    }
/**
 * 列出仍生效的模板，供端上左右滑。
 */

    public List<PosterViewDto> posters() {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return catalog.posters().stream()
                .filter(row -> ActiveStatus.ACTIVE == row.getStatus())
                .filter(row -> row.getStartsAt() == null || !now.isBefore(row.getStartsAt()))
                .filter(row -> row.getEndsAt() == null || now.isBefore(row.getEndsAt()))
                .sorted(Comparator.comparing(row -> row.getSort() == null ? 0 : row.getSort()))
                .map(this::view)
                .toList();
    }

    public Map<String, Boolean> lookup(String code) {
        AppUser user = catalog.findByInviteCode(code).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "邀请码不存在");
        }
        return Map.of("valid", true);
    }
/**
 * 收成端上能画的模板。二维码内容由端上生成。
 */

    private PosterViewDto view(InvitePosterTemplate row) {
        String key = row.getImageObjectKey() == null ? "" : row.getImageObjectKey();
        String imageUrl = key.startsWith("http://") || key.startsWith("https://") ? key : landBase + "/" + key;
        return new PosterViewDto(
                row.getId(),
                row.getName(),
                imageUrl,
                row.getCanvasWidth(),
                row.getCanvasHeight(),
                row.getQrX(),
                row.getQrY(),
                row.getQrWidth(),
                row.getQrHeight(),
                row.getSort() == null ? 0 : row.getSort());
    }
/**
 * 邀请码和落地地址。停用账号不带地址。
 */

    public record InviteViewDto(String inviteCode, String url, int invitedCount) {
    }
/**
 * 模板图和贴码矩形。端上自己画二维码。
 */

    public record PosterViewDto(
            String id,
            String name,
            String imageUrl,
            Integer canvasWidth,
            Integer canvasHeight,
            Integer qrX,
            Integer qrY,
            Integer qrWidth,
            Integer qrHeight,
            int sort) {
    }
}
