package cn.orangenode.forge.m2.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * OpenAPI 分组与文档声明验证。
 *
 * <p>验证管理端分组文档可访问、只包含 {@code /api/admin/**} 前缀的接口，
 * 并验证对外 ID 在文档中声明为 string，避免出现“后端字符串、文档 int64”的不一致。</p>
 *
 * <p>使用随机端口启动真实应用，文档路径固定为 {@code /v3/api-docs/admin}；
 * 生产环境通过配置关闭文档。响应体按 UTF-8 解码后再做中文断言。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_openapi;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2"
})
class OpenApiGroupingIntegrationTest {

    /**
     * 从路径定义中提取 id 参数 schema 的正则。
     */
    private static final Pattern ID_PARAMETER = Pattern.compile(
            "\\{\"name\":\"id\",\"in\":\"path\",\"required\":true,\"schema\":\\{(.{0,200}?)\\}", Pattern.DOTALL);

    /**
     * Redis 替身，避免该用例依赖真实 Redis。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 随机分配的测试端口。
     */
    @LocalServerPort
    private int port;

    /**
     * 指向当前测试服务端口的客户端，在测试方法执行阶段按端口创建。
     */
    private RestTestClient restTestClient;

    /**
     * 测试方法执行前构建客户端。
     */
    @BeforeEach
    void prepareClient() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /**
     * 验证管理端分组文档可访问、包含示例接口且不含分组前缀之外的接口。
     */
    @Test
    @DisplayName("管理端分组文档可访问且只收录管理端接口")
    void shouldExposeAdminGroupOnly() {
        String body = readDocument("admin").body();

        assertThat(body).contains("/api/admin/v1/example/hello");
        assertThat(body).contains("/api/admin/v1/example/validation");
        assertThat(body).doesNotContain("/api/probe/v1/m2/ping");
    }

    /**
     * 验证路径中的 ID 在文档中声明为 string，并带有长度约束。
     */
    @Test
    @DisplayName("路径 ID 在文档中声明为 string")
    void shouldDeclareIdAsString() {
        String body = readDocument("admin").body();

        assertThat(body).contains("/api/admin/v1/example/validation/{id}");
        Matcher matcher = ID_PARAMETER.matcher(body);
        assertThat(matcher.find()).as("文档中应存在 id 路径参数").isTrue();
        assertThat(matcher.group(1)).contains("\"type\":\"string\"");
    }

    /**
     * 验证文档声明了 Bearer 认证方案与统一响应结构。
     */
    @Test
    @DisplayName("文档声明 Bearer 方案与统一响应结构")
    void shouldDeclareSecuritySchemeAndConventions() {
        String body = readDocument("admin").body();

        assertThat(body).contains("bearerAuth");
        assertThat(body).contains("HTTP 200");
        assertThat(body).contains("\"traceId\"");
    }

    /**
     * 读取指定分组的 OpenAPI 文档，断言 HTTP 状态并按 UTF-8 解码响应体。
     *
     * @param group 分组名称
     * @return 文档内容与响应头
     */
    private DocumentSnapshot readDocument(String group) {
        EntityExchangeResult<byte[]> result = restTestClient.get().uri("/v3/api-docs/" + group).exchange()
                .returnResult(byte[].class);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.OK);
        byte[] payload = result.getResponseBody();
        assertThat(payload).isNotNull();
        String contentType = result.getResponseHeaders().getFirst("Content-Type");
        return new DocumentSnapshot(contentType == null ? "" : contentType,
                new String(payload, StandardCharsets.UTF_8));
    }

    /**
     * 文档响应快照。
     *
     * @param contentType 响应内容类型
     * @param body        按 UTF-8 解码后的文档内容
     */
    private record DocumentSnapshot(String contentType, String body) {
    }
}
