package com.omino.shopai.app.dto;

/**
 * 上传成功后的图片对象键。
 *
 * @param imageObjectKey 相对对象键，再作为图片消息开 run
 */
public record ImageObjectDto(String imageObjectKey) {
}
