package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.InviteViewDto;
import com.omino.shopai.app.dto.PosterViewDto;
import com.omino.shopai.app.service.invite.IInvitePosterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
/**
 * InviteController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InviteController")
@RequestMapping("/v1/invite")
public class InviteController {

    private final IInvitePosterService invites;

    public InviteController(IInvitePosterService invites) {
        this.invites = invites;
    }
    /**
     * 读取当前用户的邀请码和邀请链接。
     *
     * @param userId 当前用户
     * @return 邀请信息信封
     */
    @Operation(summary = "读取我的邀请信息")
    @GetMapping
    public ApiResponse<InviteViewDto> invite(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(invites.invite(userId));
    }
    /**
     * 列出当前生效的邀请海报模板和贴码矩形。
     *
     * @return 海报模板信封
     */
    @Operation(summary = "列出可用的邀请海报模板")
    @GetMapping("/posters")
    public ApiResponse<List<PosterViewDto>> posters() {
        return ApiResponse.ok(invites.posters());
    }
    /**
     * 检查邀请码是否存在。
     *
     * @param code 邀请码
     * @return 是否存在
     */
    @Operation(summary = "检查邀请码是否存在")
    @GetMapping("/lookup")
    public ApiResponse<Map<String, Boolean>> lookup(@RequestParam("code") String code) {
        return ApiResponse.ok(invites.lookup(code));
    }


}
