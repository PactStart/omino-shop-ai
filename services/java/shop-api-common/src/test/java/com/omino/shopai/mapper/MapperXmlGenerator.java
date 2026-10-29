package com.omino.shopai.mapper;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.TemplateType;

import java.nio.file.Path;
import java.util.Map;

/**
 * 只在改表后、本地库已经迁完时手工运行。CI 不调用。
 * 不生成 Entity、Service、Controller。标准 XML 可以被重新生成；{@code *.custom.xml} 不是生成目标，不会被覆盖。
 */
public final class MapperXmlGenerator {

    private MapperXmlGenerator() {
    }

    public static void main(String[] args) {
        String host = env("PG_HOST", "127.0.0.1");
        String port = env("PG_PORT", "5432");
        String database = env("PG_DATABASE", "omino_shop_ai");
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        generate(url, env("PG_USERNAME", "omino"), env("PG_PASSWORD", "omino-local"),
                Path.of("src/main/java"), Path.of("src/main/resources/mapper"));
    }

    public static void generate(String jdbcUrl, String username, String password, Path javaDir, Path xmlDir) {
        FastAutoGenerator.create(jdbcUrl, username, password)
                .globalConfig(builder -> builder.disableOpenDir().author("omino").outputDir(javaDir.toString()))
                .packageConfig(builder -> builder.parent("com.omino.shopai").mapper("mapper")
                        .pathInfo(Map.of(
                                OutputFile.mapper, javaDir.resolve("com/omino/shopai/mapper").toString(),
                                OutputFile.xml, xmlDir.toString())))
                .strategyConfig(builder -> builder
                        .addInclude(
                                "app_user",
                                "conversation",
                                "conversation_message",
                                "goods_snapshot",
                                "goods_favorite",
                                "goods_collection",
                                "goods_collection_item",
                                "click_event",
                                "platform_authorization",
                                "user_event",
                                "app_user_installed_app",
                                "app_order",
                                "order_sync_cursor",
                                "wallet",
                                "wallet_ledger",
                                "withdrawal",
                                "credit_account",
                                "credit_ledger",
                                "credit_package",
                                "credit_order",
                                "llm_usage",
                                "invite_poster_template",
                                "kb_article",
                                "ticket",
                                "ticket_log",
                                "app_release",
                                "app_runtime_config",
                                "announcement",
                                "inbox_message",
                                "chat_suggestion",
                                "agent_prompt",
                                "llm_model",
                                "admin_audit_log")
                        .entityBuilder().disable()
                        .serviceBuilder().disable()
                        .controllerBuilder().disable()
                        .mapperBuilder().enableFileOverride())
                .templateConfig(builder -> builder.disable(
                        TemplateType.ENTITY,
                        TemplateType.SERVICE,
                        TemplateType.SERVICE_IMPL,
                        TemplateType.CONTROLLER))
                .execute();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
