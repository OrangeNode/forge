package cn.orangenode.forge.m3;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.support.RedisTestDouble;

/**
 * 管理员登录图形验证码与来源地址限流集成测试。
 *
 * <p>测试类路径下的补充配置默认关闭验证码，本类在类级属性中重新开启，
 * 并把来源地址失败上限调低、账号失败上限调高，以便单独验证来源地址限流。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m3_captcha;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false",
    "forge.security.captcha.enabled=true",
    "forge.security.login-max-attempts=10",
    "forge.security.login-ip-max-attempts=3"
})
class AdminCaptchaIntegrationTest {

    /**
     * 验证码编号提取规则。
     */
    private static final Pattern CAPTCHA_ID = Pattern.compile("\"captchaId\":\"([0-9a-f]{32})\"");

    /**
     * 验证码图片提取规则。
     */
    private static final Pattern IMAGE = Pattern.compile("\"image\":\"data:image/png;base64,([^\"]+)\"");

    /**
     * PNG 文件头。
     */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    /**
     * 被替身接管的 Redis 模板。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 随机端口。
     */
    @LocalServerPort
    private int port;

    /**
     * 测试数据库访问。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试数据构造。
     */
    @Autowired
    private M3FixtureService fixture;

    /**
     * 用于拼接完整 Redis 键名。
     */
    @Autowired
    private ForgeRedisTemplate forgeRedisTemplate;

    /**
     * 测试客户端。
     */
    private RestTestClient client;

    /**
     * 内存版 Redis 替身。
     */
    private RedisTestDouble redisDouble;

    /**
     * 每个用例前重建表结构、清空 Redis 替身并创建一个启用的管理员。
     */
    @BeforeEach
    void prepareData() {
        client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        M3TestSupport.recreateSystemTables(jdbcTemplate);
        redisDouble = new RedisTestDouble(stringRedisTemplate);
        redisDouble.clear();
        fixture.createAdmin(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD, "enabled");
    }

    /**
     * 验证验证码接口返回 PNG 图片，答案只写入 Redis 并带有效期。
     */
    @Test
    @DisplayName("验证码接口返回图片，答案只存 Redis")
    void shouldIssueCaptchaImage() {
        String body = exchange(HttpMethod.GET, "/api/admin/v1/auth/captcha", null);

        assertThat(body).contains("\"code\":0").contains("\"enabled\":true").contains("\"expiresIn\":120");
        String captchaId = captchaIdOf(body);
        Matcher image = IMAGE.matcher(body);
        assertThat(image.find()).isTrue();
        byte[] png = Base64.getDecoder().decode(image.group(1));
        assertThat(png).startsWith(PNG_SIGNATURE);
        String answer = answerOf(captchaId);
        assertThat(answer).matches("[2-9A-HJKMNP-Z]{4}");
        assertThat(body).doesNotContain("\"" + answer + "\"");
        assertThat(redisDouble.ttl(fullKey(captchaId))).isEqualTo(Duration.ofSeconds(120));
    }

    /**
     * 验证缺少或填错验证码时拒绝登录，且验证码用过一次即作废。
     */
    @Test
    @DisplayName("验证码缺失或错误时拒绝登录，用过即作废")
    void shouldRejectMissingOrWrongCaptcha() {
        assertThat(login(null, null, M3TestSupport.ADMIN_PASSWORD)).contains("\"code\":400");

        String captchaId = captchaIdOf(exchange(HttpMethod.GET, "/api/admin/v1/auth/captcha", null));
        String answer = answerOf(captchaId);
        String wrong = answer.equals("2222") ? "3333" : "2222";
        assertThat(login(captchaId, wrong, M3TestSupport.ADMIN_PASSWORD)).contains("\"code\":400");
        assertThat(redisDouble.value(fullKey(captchaId))).isNull();
        assertThat(login(captchaId, answer, M3TestSupport.ADMIN_PASSWORD)).contains("\"code\":400");
    }

    /**
     * 验证正确验证码（不区分大小写）加正确密码可以登录。
     */
    @Test
    @DisplayName("验证码正确且不区分大小写时登录成功")
    void shouldLoginWithCorrectCaptcha() {
        String captchaId = captchaIdOf(exchange(HttpMethod.GET, "/api/admin/v1/auth/captcha", null));
        String answer = answerOf(captchaId).toLowerCase();

        String body = login(captchaId, answer, M3TestSupport.ADMIN_PASSWORD);

        assertThat(body).contains("\"code\":0").contains("\"accessToken\"");
        assertThat(redisDouble.value(fullKey(captchaId))).isNull();
    }

    /**
     * 验证同一来源地址失败次数达到上限后，即使凭据正确也返回 429。
     */
    @Test
    @DisplayName("来源地址失败次数达到上限后拒绝登录")
    void shouldLimitFailuresBySourceAddress() {
        for (int attempt = 0; attempt < 3; attempt++) {
            String captchaId = captchaIdOf(exchange(HttpMethod.GET, "/api/admin/v1/auth/captcha", null));
            assertThat(login(captchaId, answerOf(captchaId), "wrong-password-" + attempt)).contains("\"code\":401");
        }
        String captchaId = captchaIdOf(exchange(HttpMethod.GET, "/api/admin/v1/auth/captcha", null));

        String body = login(captchaId, answerOf(captchaId), M3TestSupport.ADMIN_PASSWORD);

        assertThat(body).contains("\"code\":429").doesNotContain("accessToken");
    }

    /**
     * 发送带验证码的登录请求。
     *
     * @param captchaId   验证码编号，允许为 {@code null}
     * @param captchaCode 验证码输入，允许为 {@code null}
     * @param password    密码
     * @return 响应体
     */
    private String login(String captchaId, String captchaCode, String password) {
        StringBuilder body = new StringBuilder("{\"username\":\"").append(M3TestSupport.ADMIN_USERNAME)
                .append("\",\"password\":\"").append(password).append('"');
        if (captchaId != null) {
            body.append(",\"captchaId\":\"").append(captchaId).append('"');
        }
        if (captchaCode != null) {
            body.append(",\"captchaCode\":\"").append(captchaCode).append('"');
        }
        return exchange(HttpMethod.POST, "/api/admin/v1/auth/login", body.append('}').toString());
    }

    /**
     * 提取验证码编号，取不到时让用例失败。
     *
     * @param body 验证码接口响应体
     * @return 验证码编号
     */
    private String captchaIdOf(String body) {
        Matcher matcher = CAPTCHA_ID.matcher(body);
        assertThat(matcher.find()).as("响应应包含验证码编号：%s", body).isTrue();
        return matcher.group(1);
    }

    /**
     * 从 Redis 替身读取验证码答案。
     *
     * @param captchaId 验证码编号
     * @return 答案
     */
    private String answerOf(String captchaId) {
        String answer = redisDouble.value(fullKey(captchaId));
        assertThat(answer).as("Redis 中应保存验证码答案").isNotNull();
        return answer;
    }

    /**
     * 拼接验证码答案的完整 Redis 键名。
     *
     * @param captchaId 验证码编号
     * @return 完整键名
     */
    private String fullKey(String captchaId) {
        return forgeRedisTemplate.key("auth:captcha:" + captchaId);
    }

    /**
     * 发送请求并返回 UTF-8 响应体。
     *
     * @param method 请求方法
     * @param uri    请求路径
     * @param body   JSON 请求体，允许为 {@code null}
     * @return 响应体
     */
    private String exchange(HttpMethod method, String uri, String body) {
        RestTestClient.RequestBodySpec request = client.method(method).uri(uri);
        RestTestClient.RequestHeadersSpec<?> spec = body == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).body(body);
        EntityExchangeResult<byte[]> result = spec.exchange().returnResult(byte[].class);
        byte[] payload = result.getResponseBody();
        return payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
    }
}
