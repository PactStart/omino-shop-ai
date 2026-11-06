package com.omino.shopai.app.service.goods;

import com.omino.shopai.app.dto.ConvertedLinkDto;
import com.omino.shopai.app.dto.GoodsCardDto;
import com.omino.shopai.app.dto.GoodsCommandDto;
import com.omino.shopai.app.dto.GoodsDetailDto;
import com.omino.shopai.app.dto.GoodsParseDto;
import com.omino.shopai.app.dto.GoodsSearchRequestDto;
import com.omino.shopai.app.dto.KeywordListDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.GoodsSnapshot;

import java.util.List;

/**
 * 商品详情、搜索和点击后转链。密钥不出这个进程。
 */
public interface IGoodsService {

    /**
     * 读取详情。有商品号时只读快照，否则按平台商品号走缓存和联盟。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码，没有商品号时必填
     * @param platformGoodsId 平台商品号，没有商品号时必填
     * @return 商品详情
     */
    GoodsDetailDto detail(String userId, String goodsId, String platform, String platformGoodsId);

    /**
     * 在一个平台上按关键词搜品。没有推广身份则不出站。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param keyword 关键词
     * @return 商品卡
     */
    List<GoodsCardDto> search(String userId, String platform, String keyword);

    /**
     * 分页搜品。排序只接受销量、价格、佣金、优惠券四种。价格、品牌、有券和翻页游标都在请求里。
     *
     * @param userId 当前用户
     * @param request 搜索条件
     * @return 商品卡分页，平台给出游标时带 nextId
     */
    PageDto<GoodsCardDto> searchPage(String userId, GoodsSearchRequestDto request);

    /**
     * 按已输入的词做补全。
     *
     * @param keyword 已输入的词
     * @return 补全词
     */
    KeywordListDto suggestions(String keyword);

    /**
     * 读取热搜词。
     *
     * @return 热搜词
     */
    KeywordListDto hotWords();

    /**
     * 生成一件商品的口令。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @return 口令
     */
    GoodsCommandDto command(String userId, String goodsId, String platform, String platformGoodsId);

    /**
     * 解析一段链接或口令。只支持淘宝和唯品会。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param content 链接或口令
     * @return 商品卡和链接
     */
    GoodsParseDto parse(String userId, String platform, String content);

    /**
     * 查与一件商品相似的商品。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码
     * @param platformGoodsId 平台商品号
     * @param page 页码
     * @param size 每页条数
     * @return 商品卡分页
     */
    PageDto<GoodsCardDto> similar(
            String userId, String goodsId, String platform, String platformGoodsId, Integer page, Integer size);

    /**
     * 猜你喜欢。
     *
     * @param userId 当前用户
     * @param platform 平台码
     * @param page 页码
     * @param size 每页条数
     * @return 商品卡分页
     */
    PageDto<GoodsCardDto> guess(String userId, String platform, Integer page, Integer size);

    /**
     * 校验授权和推广身份后生成购买链接，成功才记点击。
     *
     * @param userId 当前用户
     * @param goodsId 本系统商品号，可空
     * @param platform 平台码，没有商品号时必填
     * @param platformGoodsId 平台商品号，没有商品号时必填
     * @param preferApp 是否优先打开已安装的电商 APP；空按优先
     * @return 购买链接
     */
    ConvertedLinkDto convert(
            String userId,
            String goodsId,
            String platform,
            String platformGoodsId,
            Boolean preferApp);

    /**
     * 已授权之后才向官方要购买链接。未安装对应 APP 时不下发 deeplink。
     *
     * @param goods 要转链的商品
     * @param openInApp 是否下发 deeplink
     * @param userId 当前用户
     * @param promotionId 推广身份
     * @return 购买链接
     */
    ConvertedLinkDto issue(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId);
}
