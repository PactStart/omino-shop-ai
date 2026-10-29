package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.pay.AlipayNotifyVerifier;
import com.omino.shopai.app.adapter.pay.WechatPayNotifyVerifier;
import com.omino.shopai.app.service.credit.CreditBillingService;
import com.omino.shopai.app.service.credit.CreditBook;
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

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * CreditController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "CreditController")
@RequestMapping("/v1")
public class CreditController {

    private final CreditPurchaseService purchases;
    private final CreditBillingService billing;
    private final CreditBook book;
    private final WechatPayNotifyVerifier wechat;
    private final AlipayNotifyVerifier alipay;
/**
 * 积分查询和下单。回调先验签再入账。
 */

    public CreditController(
            CreditPurchaseService purchases,
            CreditBillingService billing,
            CreditBook book,
            WechatPayNotifyVerifier wechat,
            AlipayNotifyVerifier alipay) {
        this.purchases = purchases;
        this.billing = billing;
        this.book = book;
        this.wechat = wechat;
        this.alipay = alipay;
    }

    @Operation(summary = "查询 /credits")
    @GetMapping("/credits")
    public ApiResponse<Map<String, String>> credits(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(billing.balance(userId), trace(), now());
    }

    @Operation(summary = "查询 /credits/ledger")
    @GetMapping("/credits/ledger")
    public ApiResponse<List<CreditLedger>> ledger(@RequestAttribute("userId") String userId) {
        return ApiResponse.ok(book.ledger(userId), trace(), now());
    }

    @Operation(summary = "查询 /credit-packages")
    @GetMapping("/credit-packages")
    public ApiResponse<List<CreditPackage>> packages() {
        return ApiResponse.ok(billing.packages(), trace(), now());
    }

    @Operation(summary = "提交 /credit-orders")
    @PostMapping("/credit-orders")
    public ApiResponse<CreditOrder> place(@RequestAttribute("userId") String userId, @RequestBody PlaceRequest request) {
        return ApiResponse.ok(purchases.place(userId, request.packageId(), request.channel(), request.clientOrderId()), trace(), now());
    }
/**
 * 按订单号读购买单。不是本人的单当不存在。
 */

    @Operation(summary = "查询 /credit-orders/{id}")
    @GetMapping("/credit-orders/{id}")
    public ApiResponse<CreditOrder> order(@RequestAttribute("userId") String userId, @PathVariable String id) {
        CreditOrder order = book.findOrder(id);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "订单不存在");
        }
        return ApiResponse.ok(order, trace(), now());
    }

    @Operation(summary = "提交 /pay/wechat/notify")
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
 * 先验签。未通过则回答 failure，已入账的重放仍回答 success。
 */

    @Operation(summary = "提交 /pay/alipay/notify")
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

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 购买积分。渠道只允许微信、支付宝或钱包。
 */

    public record PlaceRequest(String packageId, String channel, String clientOrderId) {
    }
}
