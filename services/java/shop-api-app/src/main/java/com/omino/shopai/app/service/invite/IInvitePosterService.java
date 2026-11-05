package com.omino.shopai.app.service.invite;

import com.omino.shopai.app.dto.InviteViewDto;
import com.omino.shopai.app.dto.PosterViewDto;

import java.util.List;
import java.util.Map;

/**
 * 邀请码和海报模板。只下发模板图和贴码矩形，不在服务端合成海报。
 */
public interface IInvitePosterService {

    /**
     * 读出本人邀请码。停用账号不发落地地址。
     *
     * @param userId 当前用户
     * @return 邀请码和落地地址
     */
    InviteViewDto invite(String userId);

    /**
     * 列出仍生效的模板，供端上左右滑。
     *
     * @return 当前可画的海报模板
     */
    List<PosterViewDto> posters();

    /**
     * 检查邀请码是否可用。停用或已删除的账号按不存在处理。
     *
     * @param code 邀请码
     * @return 只含 valid=true
     */
    Map<String, Boolean> lookup(String code);
}
