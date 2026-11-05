package com.omino.shopai.app.dto;

/**
 * 模板图和贴码矩形。端上自己画二维码。
 *
 * @param id 模板主键
 * @param name 模板名称
 * @param imageUrl 模板图地址
 * @param canvasWidth 画布宽
 * @param canvasHeight 画布高
 * @param qrX 二维码左上角横坐标
 * @param qrY 二维码左上角纵坐标
 * @param qrWidth 二维码宽
 * @param qrHeight 二维码高
 * @param sort 展示顺序
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
