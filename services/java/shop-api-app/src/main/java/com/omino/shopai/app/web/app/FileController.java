package com.omino.shopai.app.web.app;

import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.ImageObjectDto;
import com.omino.shopai.app.service.file.ImageFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对话图片上传。只返回对象键，不合成、不识别。
 */
@RestController
@Tag(name = "FileController")
@RequestMapping("/v1/files")
public class FileController {

    private final ImageFileService images;

    /**
     * 注入图片保存。
     *
     * @param images 图片保存
     */
    public FileController(ImageFileService images) {
        this.images = images;
    }

    /**
     * 保存一张对话图片。
     *
     * @param userId 当前用户
     * @param file 字段名为 file 的图片
     * @return 对象键信封
     */
    @Operation(summary = "上传一张对话图片")
    @PostMapping("/image")
    public ApiResponse<ImageObjectDto> image(
            @RequestAttribute("userId") String userId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new ImageObjectDto(images.store(userId, file)));
    }
}
