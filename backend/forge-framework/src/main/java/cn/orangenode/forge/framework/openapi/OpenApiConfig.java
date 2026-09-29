package cn.orangenode.forge.framework.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAPI 文档装配。
 *
 * <p>项目只有一个后台管理前端与一种管理员身份，因此只装配一个管理端分组，
 * 匹配前缀 {@code /api/admin/**}，与接口前缀约定一致。分组只影响文档展示，
 * 不改变接口权限，权限仍由安全过滤链与权限注解决定。</p>
 *
 * <p>文档说明为中文，并声明 Bearer 认证方案；对外 ID 在控制器与 DTO 中使用字符串类型，
 * 因此文档中同样体现为 string，不存在“后端字符串、文档 int64”的不一致。</p>
 *
 * <p>是否开启文档与界面由 {@code springdoc.*} 配置控制，生产环境默认关闭。</p>
 */
@Slf4j
@Configuration
public class OpenApiConfig {

    /**
     * 声明文档基础信息与 Bearer 认证方案。
     *
     * @return OpenAPI 定义
     */
    @Bean
    public OpenAPI forgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Orange Forge 接口文档")
                        .version("v1")
                        .description("""
                                统一约定：应用可处理的接口一律返回 HTTP 200，调用方必须读取响应体 code 判断结果；
                                code=0 表示成功，400/401/403/404/405/409/413/415/429/500/503 表示具体失败语义，
                                失败响应 data 为 null 或结构化错误详情，traceId 用于关联服务端日志。
                                对外 ID 全部为字符串，时间使用带时区的 ISO 8601。""")
                        .contact(new Contact().name("Orange Forge")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("Opaque")
                                .description("受保护接口使用 Authorization: Bearer <token>")));
    }

    /**
     * 管理端接口分组。
     *
     * @return 管理端分组定义
     */
    @Bean
    public GroupedOpenApi adminApi() {
        log.info("OpenAPI 管理端分组已装配，匹配前缀 /api/admin/**");
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("管理端接口")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
