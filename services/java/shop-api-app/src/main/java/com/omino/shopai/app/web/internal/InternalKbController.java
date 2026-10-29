package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.kb.KbSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
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

    @Operation(summary = "提交 /search")
    @PostMapping("/search")
    public ApiResponse<KbSearchService.KbHit> search(@RequestBody SearchRequest request) {
        return ApiResponse.ok(search.search(request.query()), trace(), now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 搜索词和平台。
 */

    public record SearchRequest(String query) {
    }
}
