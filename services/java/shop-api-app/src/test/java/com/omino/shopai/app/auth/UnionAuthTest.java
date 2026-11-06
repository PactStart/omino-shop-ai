package com.omino.shopai.app.auth;

import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.PddGoodsImpl;
import com.omino.shopai.app.adapter.platform.TaobaoGoodsImpl;
import com.omino.shopai.app.adapter.platform.VipGoodsImpl;
import com.omino.shopai.app.dto.UnionAuthDto;
import com.omino.shopai.app.goods.MemoryGoods;
import com.omino.shopai.app.service.auth.UnionAuthService;
import com.omino.shopai.app.util.SdkSessions;
import com.omino.shopai.app.web.app.AuthorizationController;
import com.omino.shopai.entity.PlatformAuthorization;
import com.omino.shopai.enums.AuthStatus;
import com.omino.shopai.enums.Platform;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.exception.PlatformCallException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omino.shopai.mapper.PlatformAuthorizationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnionAuthTest {

    private static final String USER = "01USER0000000000000000000A";
    private static final String SECRET = "test-secret-test-secret-test-secret";

    @BeforeAll
    static void tableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PlatformAuthorization.class);
    }

    @Test
    void jdNeedsNoAuthorizationAndDoesNotCallPlatforms() throws Exception {
        PlatformAuthorizationMapper mapper = mock(PlatformAuthorizationMapper.class);
        UnionAuthService service = service(mapper, new MemoryGoods(), new TaobaoGoodsImpl("", ""), new PddGoodsImpl("", ""), new VipGoodsImpl("", ""), null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthorizationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        mvc.perform(post("/v1/me/authorizations/status")
                        .requestAttr("userId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"jd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.platform").value("jd"))
                .andExpect(jsonPath("$.data.required").value(false))
                .andExpect(jsonPath("$.data.authorized").value(true))
                .andExpect(jsonPath("$.data.authUrl").value(nullValue()));
        verifyNoInteractions(mapper);

        ApiBusinessException ex = assertThrows(ApiBusinessException.class, () -> service.revoke(USER, "jd"));
        assertEquals(ErrorCodes.VALIDATION, ex.code());
        assertEquals("京东不需要授权", ex.getMessage());
    }

    @Test
    void missingKeysDoNotOpenClients() {
        SdkSessions.opened.set(0);
        assertThrows(PlatformCallException.class, () -> new PddGoodsImpl("", "").bound(USER, "501"));
        assertThrows(PlatformCallException.class, () -> new VipGoodsImpl("", "").bound(USER));
        assertThrows(PlatformCallException.class, () -> new VipGoodsImpl("", "").unbind(USER));
        assertThrows(PlatformCallException.class, () -> new TaobaoGoodsImpl("", "").authorizeUrl("state"));
        assertThrows(PlatformCallException.class, () -> new TaobaoGoodsImpl("", "").filing("code"));
        assertEquals(0, SdkSessions.opened.get());
    }

    @Test
    void taobaoCallbackStoresRelationWithoutReturningIt() {
        AtomicReference<PlatformAuthorization> row = new AtomicReference<>();
        ScriptTaobao taobao = new ScriptTaobao();
        UnionAuthService service = service(store(row), new MemoryGoods(), taobao, new PddGoodsImpl("", ""), new VipGoodsImpl("", ""), null);
        UnionAuthDto pending = service.status(USER, "taobao");
        assertFalse(pending.authorized());
        assertTrue(pending.authUrl().startsWith("https://oauth.example/authorize?state="));
        String state = taobao.state;

        UnionAuthDto done = service.completeTaobao("once", state);
        assertTrue(done.authorized());
        assertEquals(null, done.authUrl());
        assertFalse(String.valueOf(done).contains("2552715009"));
        assertEquals(AuthStatus.AUTHORIZED, row.get().getStatus());
        assertEquals("2552715009", row.get().getRelationId());
        assertEquals(1, taobao.filings);

        service.revoke(USER, "taobao");
        assertEquals(AuthStatus.REVOKED, row.get().getStatus());
        assertEquals(null, row.get().getRelationId());
    }

    @Test
    void repeatedTaobaoCodeDoesNotFileTwice() {
        AtomicReference<PlatformAuthorization> row = new AtomicReference<>();
        AtomicReference<String> cache = new AtomicReference<>();
        StringRedisTemplate redis = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return codes(cache);
            }
        };
        ScriptTaobao taobao = new ScriptTaobao();
        UnionAuthService service = service(store(row), new MemoryGoods(), taobao, new PddGoodsImpl("", ""), new VipGoodsImpl("", ""), redis);
        String state = stateOf(service, taobao);
        service.completeTaobao("once", state);
        service.completeTaobao("once", state);
        assertEquals(1, taobao.filings);
    }

    @Test
    void pddWithoutJdIdentityDoesNotAskPdd() {
        ScriptPdd pdd = new ScriptPdd();
        UnionAuthService service = service(store(new AtomicReference<>()), new MemoryGoods(), new TaobaoGoodsImpl("", ""), pdd, new VipGoodsImpl("", ""), null);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class, () -> service.status(USER, "pdd"));
        assertEquals(ErrorCodes.VALIDATION, ex.code());
        assertFalse(ex.getMessage().toLowerCase().contains("pid"));
        assertEquals(0, pdd.calls);
    }

    @Test
    void pddUnauthorizedReturnsOneLinkAndAuthorizedWritesTheRow() {
        MemoryGoods goods = new MemoryGoods();
        goods.relation(USER, "jd", "authorized", "501");
        ScriptPdd pdd = new ScriptPdd();
        AtomicReference<PlatformAuthorization> row = new AtomicReference<>();
        UnionAuthService service = service(store(row), goods, new TaobaoGoodsImpl("", ""), pdd, new VipGoodsImpl("", ""), null);
        UnionAuthDto pending = service.status(USER, "pdd");
        assertFalse(pending.authorized());
        assertEquals("https://pdd.example/auth", pending.authUrl());
        assertFalse(pending.authUrl().contains("501"));

        pdd.bound = true;
        UnionAuthDto done = service.status(USER, "pdd");
        assertTrue(done.authorized());
        assertEquals(null, done.authUrl());
        assertEquals(AuthStatus.AUTHORIZED, row.get().getStatus());
        assertEquals(Platform.PDD, row.get().getPlatform());
    }

    @Test
    void vipReusesOpenIdAndUnbindKeepsIt() {
        ScriptVip vip = new ScriptVip();
        AtomicReference<PlatformAuthorization> row = new AtomicReference<>();
        UnionAuthService service = service(store(row), new MemoryGoods(), new TaobaoGoodsImpl("", ""), new PddGoodsImpl("", ""), vip, null);
        UnionAuthDto pending = service.status(USER, "vip");
        assertEquals(USER, vip.lastOpenId);
        assertEquals("https://vip.example/bind", pending.authUrl());
        assertFalse(pending.authUrl().contains(vip.lastOpenId));
        assertEquals(AuthStatus.NONE, row.get().getStatus());

        vip.bound = true;
        assertTrue(service.status(USER, "vip").authorized());
        service.revoke(USER, "vip");
        assertEquals(1, vip.unbinds);
        assertEquals(AuthStatus.REVOKED, row.get().getStatus());
        assertEquals(USER, row.get().getRelationId());
    }

    private static String stateOf(UnionAuthService service, ScriptTaobao taobao) {
        service.status(USER, "taobao");
        return taobao.state;
    }

    private static UnionAuthService service(
            PlatformAuthorizationMapper mapper,
            MemoryGoods goods,
            TaobaoGoodsImpl taobao,
            PddGoodsImpl pdd,
            VipGoodsImpl vip,
            StringRedisTemplate redis) {
        return new MemoryUnionAuth(mapper, goods, taobao, pdd, vip, redis);
    }

    /**
     * 推广身份仍从内存商品读，授权行仍走假的 Mapper。
     */
    private static final class MemoryUnionAuth extends UnionAuthService {

        private final MemoryGoods goods;

        /**
         * 绑定内存商品上的推广身份。
         *
         * @param mapper 授权表
         * @param goods 内存商品
         * @param taobao 淘宝适配器
         * @param pdd 拼多多适配器
         * @param vip 唯品会适配器
         * @param redis 授权码缓存，可空
         */
        private MemoryUnionAuth(
                PlatformAuthorizationMapper mapper,
                MemoryGoods goods,
                TaobaoGoodsImpl taobao,
                PddGoodsImpl pdd,
                VipGoodsImpl vip,
                StringRedisTemplate redis) {
            super(mapper, taobao, pdd, vip, redis, SECRET);
            this.goods = goods;
        }

        /**
         * 从内存商品读取推广身份。
         *
         * @param userId 当前用户
         * @param platform 平台码
         * @return 推广身份；没有则空
         */
        @Override
        protected java.util.Optional<String> relationId(String userId, String platform) {
            return goods.relationId(userId, platform);
        }

        /**
         * 从内存商品丢掉推广身份。
         *
         * @param userId 当前用户
         * @param platform 平台码
         */
        @Override
        protected void forgetRelation(String userId, String platform) {
            goods.forgetRelation(userId, platform);
        }
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> codes(AtomicReference<String> cache) {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[] {ValueOperations.class},
                (proxy, method, args) -> {
                    if ("get".equals(method.getName()) && args != null && args.length == 1) {
                        return cache.get();
                    }
                    if ("set".equals(method.getName()) && args != null && args.length == 3) {
                        cache.set((String) args[1]);
                        return null;
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return false;
                    }
                    if (type == long.class || type == int.class) {
                        return 0;
                    }
                    return null;
                });
    }

    private static PlatformAuthorizationMapper store(AtomicReference<PlatformAuthorization> row) {
        PlatformAuthorizationMapper mapper = mock(PlatformAuthorizationMapper.class);
        when(mapper.selectOne(any())).thenAnswer(invocation -> row.get());
        when(mapper.insert(any(PlatformAuthorization.class))).thenAnswer(invocation -> {
            row.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.update(isNull(), any())).thenAnswer(invocation -> 1);
        return mapper;
    }

    private static final class ScriptTaobao extends TaobaoGoodsImpl {
        private String state;
        private int filings;

        private ScriptTaobao() {
            super("", "");
        }

        @Override
        public String authorizeUrl(String state) {
            this.state = state;
            return "https://oauth.example/authorize?state=" + state;
        }

        @Override
        public String filing(String code) {
            filings++;
            return "2552715009";
        }
    }

    private static final class ScriptPdd extends PddGoodsImpl {
        private boolean bound;
        private int calls;

        private ScriptPdd() {
            super("", "");
        }

        @Override
        public boolean bound(String userId, String pid) {
            calls++;
            return bound;
        }

        @Override
        public String authUrl(String userId, String pid) {
            calls++;
            return "https://pdd.example/auth";
        }
    }

    private static final class ScriptVip extends VipGoodsImpl {
        private boolean bound;
        private int unbinds;
        private String lastOpenId;

        private ScriptVip() {
            super("", "");
        }

        @Override
        public boolean bound(String openId) {
            lastOpenId = openId;
            return bound;
        }

        @Override
        public String authUrl(String userId, String openId) {
            lastOpenId = openId;
            return "https://vip.example/bind";
        }

        @Override
        public void unbind(String openId) {
            unbinds++;
            lastOpenId = openId;
        }
    }
}
