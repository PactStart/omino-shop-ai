package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.kb.KbSearchService;
import com.omino.shopai.app.web.internal.dto.KbSearchRequestDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * InternalKbController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InternalKbController")
@RequestMapping("/internal/v1/kb")
public class InternalKbController {

    private final KbSearchService search;

    public InternalKbController(KbSearchService search) {
        this.search = search;
    }
    /**
     * 按问题检索知识库。
     *
     * @param request 问题文本
     * @return 命中的文章信封
     */
    @Operation(summary = "检索知识库")
    @PostMapping("/search")
    public ApiResponse<KbSearchService.KbHitDto> search(@RequestBody KbSearchRequestDto request) {
        return ApiResponse.ok(search.search(request.query()));
    }
}
