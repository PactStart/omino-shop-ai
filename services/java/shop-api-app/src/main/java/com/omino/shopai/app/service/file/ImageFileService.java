package com.omino.shopai.app.service.file;

import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把对话图片落到本地目录，并返回相对对象键。对象存储未接入时用这一处。
 */
@Service
public class ImageFileService {

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final Path root;

    /**
     * 注入存放目录。目录不存在时在第一次上传时创建。
     *
     * @param dir 根目录
     */
    public ImageFileService(@Value("${app.files.dir:var/uploads}") String dir) {
        this.root = Path.of(dir);
    }

    /**
     * 保存一张图片。类型或大小不对时不写盘。
     *
     * @param userId 当前用户，只用来分目录
     * @param file 上传文件，字段名 file
     * @return 相对对象键
     */
    public String store(String userId, MultipartFile file) {
        if (userId == null || userId.isBlank() || !userId.chars().allMatch(Character::isLetterOrDigit)) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "用户不正确");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "请选择图片", "file", "NOT_EMPTY");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "图片不能超过 8MB", "file", "TOO_LARGE");
        }
        byte[] bytes = read(file);
        if (bytes.length > MAX_BYTES) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "图片不能超过 8MB", "file", "TOO_LARGE");
        }
        String extension = extension(file.getContentType(), bytes);
        String name = Ids.ulid() + extension;
        Path dir = root.resolve(userId);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), bytes);
        } catch (IOException ex) {
            throw new ApiBusinessException(ErrorCodes.INTERNAL, "图片没有保存");
        }
        return userId + "/" + name;
    }

    /**
     * 读出上传内容。读失败当作参数错误，不把底层异常返回给客户端。
     *
     * @param file 上传文件
     * @return 文件字节
     */
    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "图片读取失败", "file", "UNREADABLE");
        }
    }

    /**
     * 按声明类型或文件头决定后缀。两者都不是图片则拒绝。
     *
     * @param contentType 浏览器声明的类型，可空
     * @param bytes 文件头
     * @return 带点的后缀
     */
    private static String extension(String contentType, byte[] bytes) {
        String declared = contentType == null ? "" : contentType.toLowerCase();
        int semi = declared.indexOf(';');
        if (semi >= 0) {
            declared = declared.substring(0, semi).trim();
        }
        if ("image/jpeg".equals(declared) || "image/jpg".equals(declared)) {
            return ".jpg";
        }
        if ("image/png".equals(declared)) {
            return ".png";
        }
        if ("image/webp".equals(declared)) {
            return ".webp";
        }
        if ("image/gif".equals(declared)) {
            return ".gif";
        }
        if (starts(bytes, new byte[] {(byte) 0xff, (byte) 0xd8})) {
            return ".jpg";
        }
        if (starts(bytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47})) {
            return ".png";
        }
        if (starts(bytes, new byte[] {'G', 'I', 'F'})) {
            return ".gif";
        }
        if (bytes.length >= 12 && starts(bytes, new byte[] {'R', 'I', 'F', 'F'})
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return ".webp";
        }
        throw new ApiBusinessException(ErrorCodes.VALIDATION, "只接受 jpeg、png、webp、gif", "file", "UNSUPPORTED");
    }

    /**
     * 文件头是否以给定字节开头。
     *
     * @param bytes 文件内容
     * @param prefix 期望的文件头
     * @return 匹配则为真
     */
    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
