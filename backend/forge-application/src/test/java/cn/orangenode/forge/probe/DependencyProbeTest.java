package cn.orangenode.forge.probe;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * M1 核心依赖组合探针。
 *
 * <p>在真实 MySQL 与 Redis 上启动完整应用，验证 Spring Boot 4.1、MyBatis-Plus、
 * dynamic-datasource、Flyway、Redis、Jackson 与 Web 层可以共同装配并对外服务。</p>
 *
 * <p>本用例依赖外部中间件，因此以环境变量 {@code FORGE_MASTER_URL} 为开关：
 * 该变量存在时必须真实通过，不存在时整类跳过，跳过原因记录在 M1 任务文档中。
 * 连接参数由 FORGE_MASTER_URL、FORGE_MASTER_USERNAME、FORGE_MASTER_PASSWORD、
 * FORGE_REDIS_HOST、FORGE_REDIS_PORT、FORGE_REDIS_PASSWORD 提供。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "FORGE_MASTER_URL", matches = ".+")
class DependencyProbeTest {

    /**
     * 随机分配的测试端口，避免与开发运行占用的 8080 冲突。
     */
    @LocalServerPort
    private int port;

    /**
     * Redis 字符串模板，用于验证过期写入与读取。
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 验证示例接口返回 HTTP 200 与 code=0，并携带追踪编号与 ISO 8601 时间。
     *
     * @throws Exception 请求发送或读取失败时由测试框架报告
     */
    @Test
    @DisplayName("示例接口返回统一成功响应与追踪编号")
    void shouldServeUnifiedSuccessResponse() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/admin/v1/example/hello"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Trace-Id")).isPresent();
        assertThat(response.body()).contains("\"code\":0");
        assertThat(response.body()).contains("\"application\":\"orange-forge\"");
        assertThat(response.body()).contains("\"serverTime\":\"");
        assertThat(response.body()).doesNotContain("timestamp");
    }

    /**
     * 验证 Redis 连接可用，并能写入、读取与删除带过期时间的键。
     */
    @Test
    @DisplayName("Redis 可写入并读取带过期时间的键")
    void shouldReadAndWriteRedisKey() {
        String key = "forge:m1:probe:" + UUID.randomUUID();
        String value = "dependency-probe";

        stringRedisTemplate.opsForValue().set(key, value, Duration.ofMinutes(1));
        String stored = stringRedisTemplate.opsForValue().get(key);
        Long expireSeconds = stringRedisTemplate.getExpire(key);

        assertThat(stored).isEqualTo(value);
        assertThat(expireSeconds).isNotNull().isGreaterThan(0L);

        stringRedisTemplate.delete(key);
        assertThat(stringRedisTemplate.hasKey(key)).isFalse();
    }
}
