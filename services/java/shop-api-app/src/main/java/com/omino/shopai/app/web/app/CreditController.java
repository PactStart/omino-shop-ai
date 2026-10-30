package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.web.app.dto.PlaceRequestDto;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.pay.AlipayNotifyImpl;
import com.omino.shopai.app.adapter.pay.WechatPayNotifyImpl;
import com.omino.shopai.app.service.credit.CreditBillingImpl;
import com.omino.shopai.app.service.credit.ICredit;
import com.omino.shopai.app.service.credit.CreditPurchaseService;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
/**
 * CreditController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "CreditController")
@RequestMapping("/v1")
public class CreditController {

    private final CreditPurchaseService purchases;
    private final CreditBillingImpl billing;
    private final ICredit book;
    private final WechatPayNotifyImpl wechat;
    private final AlipayNotifyImpl alipay;
/**
 * 积分查询和下单。回调先验签再入账。
 */

    public CreditController(
            CreditPurchaseService purchases,
            CreditBillingImpl billing,
            ICredit book,
            WechatPayNotifyImpl wechat,
            AlipayNotifyImpl alipay) {
        this.purchases = purchases;
        this.billing = billing;
        this.book = book;
        this.wechat = wechat;
        this.alipay = alipay;
    }
    /**
     * 读取当前用户的积分余额。
     *
     * @param userId 当前用户
     * @return 余额信封
     */
    @Operation(summary = "读取积分余额")
    @GetMapping("/credits")
    public ApiResponse<Map<String, String>> credits(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(billing.balance(userId));
    }
    /**
     * 列出当前用户的积分流水。
     *
     * @param userId 当前用户
     * @return 流水信封
     */
    @Operation(summary = "列出积分流水")
    @GetMapping("/credits/ledger")
    public ApiResponse<List<CreditLedger>> ledger(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(book.ledger(userId));
    }
    /**
     * 列出可购买的积分套餐。
     *
     * @return 套餐信封
     */
    @Operation(summary = "列出可购买的积分套餐")
    @GetMapping("/credit-packages")
    public ApiResponse<List<CreditPackage>> packages() {
        return ApiResponse.ok(billing.packages());
    }
    /**
     * 用选定渠道下单一笔积分套餐。
     *
     * @param userId 当前用户
     * @param request 套餐、渠道和客户端订单号
     * @return 积分订单信封
     */
    @Operation(summary = "下单一笔积分套餐")
    @PostMapping("/credit-orders")
    public ApiResponse<CreditOrder> place(@RequestAttribute("userId") String userId, @RequestBody PlaceRequestDto request) {
        return ApiResponse.ok(purchases.place(userId, request.packageId(), request.channel(), request.clientOrderId()));
    }
    /**
     * 读取当前用户的一笔积分订单。
     *
     * @param userId 当前用户
     * @param id 订单编号
     * @return 积分订单信封
     */
    @Operation(summary = "读取一笔积分订单")
    @GetMapping("/credit-orders/{id}")
    public ApiResponse<CreditOrder> order(@RequestAttribute("userId") String userId, @PathVariable String id) {
        CreditOrder order = book.findOrder(id);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "订单不存在");
        }
        return ApiResponse.ok(order);
    }
    /**
     * 校验微信支付回调并入账。验签失败则拒绝入账。
     *
     * @param serial 微信平台证书序列号，可空
     * @param timestamp 时间戳，可空
     * @param nonce 随机串，可空
     * @param signature 签名，可空
     * @param body 微信原文
     * @return 给微信的应答
     */
    @Operation(summary = "接收微信支付结果")
    @PostMapping("/pay/wechat/notify")
    public Map<String, String> wechatNotify(
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestBody String body) {
        if (!wechat.accepted(serial, timestamp, nonce, signature, body)) {
            return Map.of("code", "FAIL", "message", "验签失败");
        }
        purchases.settle(text(body, "orderId"), text(body, "txnId"), true);
        return Map.of("code", "SUCCESS", "message", "成功");
    }
    /**
     * 校验支付宝回调并入账。验签失败则拒绝入账。
     *
     * @param body 支付宝原文
     * @return 给支付宝的应答
     */
    @Operation(summary = "接收支付宝支付结果")
    @PostMapping("/pay/alipay/notify")
    public String alipayNotify(@RequestBody String body) {
        if (!alipay.accepted(body)) {
            return "failure";
        }
        purchases.settle(text(body, "orderId"), text(body, "txnId"), true);
        return "success";
    }
/**
 * 把回调体读成字符串。空体按验签失败处理。
 */

    private static String text(String body, String key) {
        String mark = "\"" + key + "\"";
        int at = body == null ? -1 : body.indexOf(mark);
        if (at < 0) {
            return null;
        }
        int colon = body.indexOf(':', at + mark.length());
        int start = body.indexOf('"', colon + 1);
        int end = body.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return null;
        }
        return body.substring(start + 1, end);
    }
}
