package com.omino.shopai.app.service.invite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.InviteViewDto;
import com.omino.shopai.app.dto.PosterViewDto;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.InvitePosterTemplate;
import com.omino.shopai.enums.ActiveStatus;
import com.omino.shopai.enums.UserStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.mapper.InvitePosterTemplateMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 邀请码和海报模板。只下发模板图和贴码矩形，不在服务端合成海报。
 */
@Service
public class InvitePosterServiceImpl implements IInvitePosterService {

    private final InvitePosterTemplateMapper posters;
    private final AppUserMapper users;
    private final Clock clock;
    private final String landBase;

    /**
     * 注入海报模板、用户、时钟和落地页根地址。根地址去掉末尾斜杠。
     *
     * @param posters 海报模板表
     * @param users 用户表
     * @param clock 判断模板是否生效
     * @param landBase 落地页根地址，没有配置时用本地默认
     */
    public InvitePosterServiceImpl(
            InvitePosterTemplateMapper posters,
            AppUserMapper users,
            Clock clock,
            @Value("${INVITE_LAND_BASE_URL:http://127.0.0.1:5176}") String landBase) {
        this.posters = posters;
        this.users = users;
        this.clock = clock;
        this.landBase = landBase.endsWith("/") ? landBase.substring(0, landBase.length() - 1) : landBase;
    }

    /**
     * 读出本人邀请码。停用账号不发落地地址。
     *
     * @param userId 当前用户
     * @return 邀请码和落地地址
     */
    @Override
    public InviteViewDto invite(String userId) {
        AppUser user = findById(userId).orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "用户不存在"));
        return new InviteViewDto(user.getInviteCode(), landBase + "/i/" + user.getInviteCode(), 0);
    }

    /**
     * 列出仍生效的模板，供端上左右滑。
     *
     * @return 当前可画的海报模板
     */
    @Override
    public List<PosterViewDto> posters() {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return loadPosters().stream()
                .filter(row -> ActiveStatus.ACTIVE == row.getStatus())
                .filter(row -> row.getStartsAt() == null || !now.isBefore(row.getStartsAt()))
                .filter(row -> row.getEndsAt() == null || now.isBefore(row.getEndsAt()))
                .sorted(Comparator.comparing(row -> row.getSort() == null ? 0 : row.getSort()))
                .map(this::view)
                .toList();
    }

    /**
     * 检查邀请码是否可用。停用或已删除的账号按不存在处理。
     *
     * @param code 邀请码
     * @return 只含 valid=true
     */
    @Override
    public Map<String, Boolean> lookup(String code) {
        AppUser user = findByInviteCode(code).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "邀请码不存在");
        }
        return Map.of("valid", true);
    }

    /**
     * 按排序读出全部海报模板。是否生效由调用方判断。
     *
     * @return 海报模板
     */
    protected List<InvitePosterTemplate> loadPosters() {
        return posters.selectList(new LambdaQueryWrapper<InvitePosterTemplate>().orderByAsc(InvitePosterTemplate::getSort));
    }

    /**
     * 按主键读取用户。
     *
     * @param id 用户主键
     * @return 用户；没有则空
     */
    protected Optional<AppUser> findById(String id) {
        return Optional.ofNullable(users.selectById(id));
    }

    /**
     * 按邀请码读取用户。
     *
     * @param code 邀请码
     * @return 用户；没有则空
     */
    protected Optional<AppUser> findByInviteCode(String code) {
        return Optional.ofNullable(users.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getInviteCode, code)));
    }

    /**
     * 收成端上能画的模板。二维码内容由端上生成。
     *
     * @param row 海报模板
     * @return 模板图和贴码矩形
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
}
