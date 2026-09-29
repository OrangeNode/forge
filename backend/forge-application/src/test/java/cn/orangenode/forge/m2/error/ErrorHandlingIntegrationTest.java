package cn.orangenode.forge.m2.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import cn.orangenode.forge.m2.M2TestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 统一错误出口与参数校验的端到端验证。
 *
 * <p>在随机端口启动真实应用并使用内存数据库：逐项断言成功与失败路径都返回 HTTP 200，
 * 且 {@code body.code} 与错误语义一致，同时断言响应体符合四字段协议。</p>
 *
 * <p>Redis 使用替身并在需要时模拟连接失败，用于验证依赖故障返回 503 且不继续执行业务；
 * 本用例不连接任何外部中间件。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_error;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    // M3 起真实安全策略要求认证；本用例只验证统一错误出口，因此只放行本用例的探针路径，
    // 认证与授权行为由 M3 的认证、权限集成用例验证。
    "forge.system.bootstrap.enabled=false",
    "forge.security.permit-all-paths[0]=/api/admin/v1/m2/probe/**",
    "forge.security.permit-all-paths[1]=/error"
})
class ErrorHandlingIntegrationTest {

    /**
     * Redis 替身，用于验证依赖故障路径。
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
     * 测试方法执行前构建客户端，并让 Redis 替身抛出连接失败异常以验证失败语义。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepareRedisFailure() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 验证成功路径返回 HTTP 200、code=0，且追踪编号与响应头一致。
     */
    @Test
    @DisplayName("成功路径返回 HTTP 200、code=0 与追踪编号")
    void shouldReturnUnifiedSuccess() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/success", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":0");
        assertThat(snapshot.body()).contains("\"message\":\"操作成功\"");
        String traceId = snapshot.headers().getFirst("X-Trace-Id");
        assertThat(traceId).isNotBlank();
        assertThat(snapshot.body()).contains("\"traceId\":\"" + traceId + "\"");
    }

    /**
     * 验证错误出口声明 UTF-8 编码，避免中文提示在客户端出现乱码。
     */
    @Test
    @DisplayName("错误出口声明 UTF-8 编码")
    void shouldDeclareUtf8Charset() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/business-conflict", null,
                null);

        assertThat(snapshot.headers().getFirst("Content-Type")).containsIgnoringCase("charset=UTF-8");
    }

    /**
     * 验证业务异常使用自带结果码，并保留中文说明。
     */
    @Test
    @DisplayName("业务异常返回 HTTP 200 与自带 code")
    void shouldMapBusinessException() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/business-conflict", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":409");
        assertThat(snapshot.body()).contains("业务状态冲突");
        assertThat(snapshot.body()).contains("\"data\":null");
    }

    /**
     * 验证未预期异常返回 500 且不泄漏内部实现细节。
     */
    @Test
    @DisplayName("未预期异常返回 500 且不泄漏内部细节")
    void shouldMapUnexpectedException() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/unexpected", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":500");
        assertThat(snapshot.body()).doesNotContain("内部实现细节");
        assertThat(snapshot.body()).doesNotContain("IllegalStateException");
    }

    /**
     * 验证显式抛出的依赖不可用异常返回 503。
     */
    @Test
    @DisplayName("依赖不可用异常返回 503")
    void shouldMapDependencyUnavailable() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/dependency-down", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":503");
    }

    /**
     * 验证 Redis 连接失败返回 503，且接口不再返回业务数据。
     */
    @Test
    @DisplayName("Redis 故障返回 503 且不返回业务数据")
    void shouldReturnServiceUnavailableWhenRedisFails() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/redis-down", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":503");
        assertThat(snapshot.body()).contains("\"data\":null");
        assertThat(snapshot.body()).doesNotContain("不应到达");
    }

    /**
     * 验证未注册的数据源名称返回 503，而不是回退主库静默成功。
     */
    @Test
    @DisplayName("未注册数据源返回 503 且不回退主库")
    void shouldReturnServiceUnavailableForUnknownDataSource() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/unknown-datasource", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":503");
        assertThat(snapshot.body()).doesNotContain("不应到达");
    }

    /**
     * 验证方法参数越界返回 400 与字段错误结构。
     */
    @Test
    @DisplayName("方法参数越界返回 400 与字段错误")
    void shouldRejectOutOfRangeMethodParameter() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/method-parameter?pageSize=101", null,
                null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":400");
        assertThat(snapshot.body()).contains("fieldErrors");
        assertThat(snapshot.body()).contains("每页条数不能超过 100");
    }

    /**
     * 验证缺少必需参数返回 400 与字段错误。
     */
    @Test
    @DisplayName("缺少必需参数返回 400")
    void shouldRejectMissingRequiredParameter() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M2TestSupport.ERROR_PROBE + "/method-parameter", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":400");
        assertThat(snapshot.body()).contains("fieldErrors");
    }

    /**
     * 验证请求体嵌套校验失败返回 400，且错误明细包含嵌套字段路径。
     */
    @Test
    @DisplayName("嵌套请求体校验失败返回 400 与嵌套字段路径")
    void shouldRejectInvalidNestedBody() {
        String body = """
                {"name":"名称超出长度限制的取值","nested":{"code":"编码超出长度"}}
                """;

        ResponseSnapshot snapshot = exchange(HttpMethod.POST, M2TestSupport.ERROR_PROBE + "/body", body,
                MediaType.APPLICATION_JSON);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":400");
        assertThat(snapshot.body()).contains("fieldErrors");
        assertThat(snapshot.body()).contains("\"field\":\"name\"");
        assertThat(snapshot.body()).contains("\"field\":\"nested.code\"");
    }

    /**
     * 验证校验失败不回显被拒绝的原值。
     */
    @Test
    @DisplayName("校验失败不回显被拒绝的原值")
    void shouldNotEchoRejectedValues() {
        String body = """
                {"name":"名称超出长度限制的取值","nested":{"code":"编码超出长度"}}
                """;

        ResponseSnapshot snapshot = exchange(HttpMethod.POST, M2TestSupport.ERROR_PROBE + "/body", body,
                MediaType.APPLICATION_JSON);

        assertThat(snapshot.body()).doesNotContain("名称超出长度限制的取值");
        assertThat(snapshot.body()).doesNotContain("编码超出长度");
    }

    /**
     * 验证请求体 JSON 语法错误返回 400，而不是 500。
     */
    @Test
    @DisplayName("非法 JSON 返回 400")
    void shouldRejectMalformedJson() {
        ResponseSnapshot snapshot = exchange(HttpMethod.POST, M2TestSupport.ERROR_PROBE + "/body", "{\"name\": ",
                MediaType.APPLICATION_JSON);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":400");
    }

    /**
     * 验证未认证访问未知路径时先被安全过滤链拒绝，返回 HTTP 200 与 401。
     *
     * <p>M3 收紧安全策略后未知路径不再默认放行：先要求认证，认证通过后才由框架错误出口给出 404。
     * 已认证访问未知路径返回 404 的行为由 M3 认证集成用例验证。</p>
     */
    @Test
    @DisplayName("未认证访问未知路径返回 HTTP 200 与 401")
    void shouldReturnUnauthorizedForUnknownPathWithoutToken() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/m2/not-exists", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":401");
        assertThat(snapshot.body()).contains("\"traceId\":\"");
    }

    /**
     * 验证不支持的方法走框架默认错误出口，返回 HTTP 200 与 405。
     */
    @Test
    @DisplayName("方法不支持返回 HTTP 200 与 405")
    void shouldReturnUnifiedMethodNotAllowed() {
        ResponseSnapshot snapshot = exchange(HttpMethod.DELETE, M2TestSupport.ERROR_PROBE + "/success", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":405");
    }

    /**
     * 验证不支持的媒体类型返回 HTTP 200 与 415。
     */
    @Test
    @DisplayName("媒体类型不支持返回 HTTP 200 与 415")
    void shouldReturnUnifiedUnsupportedMediaType() {
        ResponseSnapshot snapshot = exchange(HttpMethod.POST, M2TestSupport.ERROR_PROBE + "/text-body", "plain text",
                MediaType.TEXT_PLAIN);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":415");
    }

    /**
     * 发送请求并按 UTF-8 解码响应体，便于同时断言 HTTP 状态、响应头与中文提示。
     *
     * <p>接口返回中文 JSON，这里显式按 UTF-8 解码原始字节，
     * 不依赖测试客户端对响应头 charset 的推断。</p>
     *
     * @param method      请求方法
     * @param uri         请求路径
     * @param body        请求体，允许为 {@code null}
     * @param contentType 请求媒体类型，允许为 {@code null}
     * @return 原始响应结果
     */
    private EntityExchangeResult<byte[]> exchangeBytes(HttpMethod method, String uri, String body,
            MediaType contentType) {
        RestTestClient.RequestBodySpec request = restTestClient.method(method).uri(uri);
        if (contentType != null) {
            request = request.contentType(contentType);
        }
        RestTestClient.RequestHeadersSpec<?> spec = body == null ? request : request.body(body);
        return spec.exchange().returnResult(byte[].class);
    }

    /**
     * 发送请求并返回 UTF-8 解码后的响应体。
     *
     * @param method      请求方法
     * @param uri         请求路径
     * @param body        请求体，允许为 {@code null}
     * @param contentType 请求媒体类型，允许为 {@code null}
     * @return 响应状态、响应头与 UTF-8 响应体
     */
    private ResponseSnapshot exchange(HttpMethod method, String uri, String body, MediaType contentType) {
        EntityExchangeResult<byte[]> result = exchangeBytes(method, uri, body, contentType);
        byte[] payload = result.getResponseBody();
        String text = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
        return new ResponseSnapshot(result.getStatus(), result.getResponseHeaders(), text);
    }

    /**
     * 一次响应的关键信息，避免测试代码直接依赖客户端的字符集推断。
     *
     * @param status  HTTP 状态
     * @param headers 响应头
     * @param body    UTF-8 解码后的响应体
     */
    private record ResponseSnapshot(HttpStatusCode status, HttpHeaders headers, String body) {
    }
}
