package cn.orangenode.forge.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import cn.orangenode.forge.app.request.AppBannerRequest;
import cn.orangenode.forge.app.request.AppFeatureRequest;
import cn.orangenode.forge.app.request.AppHotSearchRequest;
import cn.orangenode.forge.app.request.AppSectionRequest;
import cn.orangenode.forge.app.request.AppSplashRequest;
import cn.orangenode.forge.app.service.AppContentService;
import cn.orangenode.forge.app.service.AppSettingsService;
import cn.orangenode.forge.framework.storage.StoredFileContent;
import cn.orangenode.forge.framework.storage.StoredFileReader;
import cn.orangenode.forge.member.mail.MemberCodePurpose;
import cn.orangenode.forge.member.mail.MemberMailSender;
import cn.orangenode.forge.member.request.MemberVipExtendRequest;
import cn.orangenode.forge.member.service.MemberManagementService;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 普通用户与 App 管理的端到端验证：邮箱验证码注册（替身发送器）、VIP 观看授权、同时登录台数、令牌隔离与首页配置。
 *
 * <p>表结构由真实迁移脚本派生（只移除 MySQL 引擎与函数差异），Redis 使用内存替身，不真实发信。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_member;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false", "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false", "forge.video.worker-enabled=false",
    "forge.video.app-rate-limit=100000", "forge.member.code.resend-interval-seconds=60"
})
class MemberAppIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private MemberManagementService members;
    @Autowired
    private AppContentService content;
    @Autowired
    private AppSettingsService settings;
    @MockitoBean
    private StringRedisTemplate redis;
    @MockitoBean
    private MemberMailSender mail;
    @MockitoBean
    private StoredFileReader files;
    @LocalServerPort
    private int port;
    private RestTestClient http;
    private final Map<String, String> sentCodes = new HashMap<>();
    private long videoId;
    private long categoryId;

    /**
     * 重建表、种入一部含 M3U8 与 OTHER 线路的影片，并安装替身发送器与文件读取。
     */
    @BeforeEach
    void prepare() throws Exception {
        new RedisTestDouble(redis);
        RuntimeSchema.recreateSystemAndAuditTables(jdbc);
        for (String table : List.of("member_vip_log", "member_device", "member_user", "app_splash", "app_banner",
                "app_section", "app_settings")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        for (String table : List.of("category", "source", "source_category", "video", "origin", "play_line",
                "episode", "settings", "task", "run", "pending")) {
            jdbc.execute("DROP TABLE IF EXISTS video_" + table);
        }
        migrate("db/migration/video/V20260930200000__video_collection_tables.sql");
        migrate("db/migration/member/V20261007120000__member_tables.sql");
        migrate("db/migration/app/V20261007121000__app_tables.sql");
        jdbc.update("INSERT INTO video_category (id,parent_id,name,sort_no,enabled,deleted,created_at,updated_at)"
                + " VALUES (1,0,'电影',1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_category (id,parent_id,name,sort_no,enabled,deleted,created_at,updated_at)"
                + " VALUES (2,1,'动作片',1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_video (id,name,match_name,category_id,published,metadata_locked,deleted,"
                + "created_at,updated_at) VALUES (10,'测试影片','测试影片',2,1,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_origin (id,source_id,upstream_id,video_id,name,collected_at,created_at,updated_at)"
                + " VALUES (20,1,'u1',10,'测试影片',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_play_line (id,origin_id,code,name,sort_no,enabled,created_at,updated_at)"
                + " VALUES (30,20,'m3u8','高清',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_play_line (id,origin_id,code,name,sort_no,enabled,created_at,updated_at)"
                + " VALUES (31,20,'share','分享',2,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_episode (line_id,name,sort_no,url,url_type,created_at,updated_at)"
                + " VALUES (30,'第1集',1,'https://cdn.example.com/1.m3u8','M3U8',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_episode (line_id,name,sort_no,url,url_type,created_at,updated_at)"
                + " VALUES (30,'第2集',2,'https://cdn.example.com/2.html','OTHER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO video_episode (line_id,name,sort_no,url,url_type,created_at,updated_at)"
                + " VALUES (31,'第1集',1,'https://share.example.com/1','OTHER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        videoId = 10L;
        categoryId = 1L;
        sentCodes.clear();
        when(mail.configured()).thenReturn(true);
        doAnswer(invocation -> {
            sentCodes.put(invocation.getArgument(0, String.class), invocation.getArgument(2, String.class));
            return null;
        }).when(mail).sendCode(anyString(), any(MemberCodePurpose.class), anyString());
        when(files.exists(anyLong())).thenReturn(true);
        when(files.open(anyLong())).thenAnswer(invocation -> new StoredFileContent(
                new ByteArrayInputStream("png-bytes".getBytes(StandardCharsets.UTF_8)), "image/png", 9));
        http = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /**
     * 邮箱验证码注册成功并直接登录；错误、复用验证码与频繁发送被拒绝，已注册邮箱 409，关闭注册 403。
     */
    @Test
    void registersWithEmailCodeOnly() {
        assertThat(post("/api/app/v1/member/auth/email-code", null, Map.of("email", "User@Example.com",
                "purpose", "REGISTER")).path("code").asInt()).isZero();
        String code = sentCodes.get("user@example.com");
        assertThat(code).hasSize(6).containsOnlyDigits();
        assertThat(post("/api/app/v1/member/auth/email-code", null, Map.of("email", "user@example.com",
                "purpose", "REGISTER")).path("code").asInt()).isEqualTo(429);
        assertThat(register("user@example.com", "000000".equals(code) ? "111111" : "000000", "device-a1")
                .path("code").asInt()).isEqualTo(400);
        JsonNode ok = register("user@example.com", code, "device-a1");
        assertThat(ok.path("code").asInt()).isZero();
        String token = ok.path("data").path("accessToken").asText();
        assertThat(ok.path("data").path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(ok.path("data").path("expiresIn").asLong()).isEqualTo(2592000L);
        assertThat(ok.path("data").path("profile").path("vipActive").asBoolean()).isFalse();
        JsonNode profile = get("/api/app/v1/member/profile", token);
        assertThat(profile.path("data").path("email").asText()).isEqualTo("user@example.com");
        assertThat(register("user@example.com", code, "device-a2").path("code").asInt()).isEqualTo(409);
        settings.updateFeatures(new AppFeatureRequest(true, false, 2, null, null));
        assertThat(post("/api/app/v1/member/auth/email-code", null, Map.of("email", "other@example.com",
                "purpose", "REGISTER")).path("code").asInt()).isEqualTo(403);
    }

    /**
     * 未配置发信服务时发送验证码返回 503；找回密码对未注册邮箱静默成功且不发信。
     */
    @Test
    void reportsMissingMailServerAndHidesUnknownEmails() {
        assertThat(post("/api/app/v1/member/auth/email-code", null, Map.of("email", "nobody@example.com",
                "purpose", "RESET_PASSWORD")).path("code").asInt()).isZero();
        verify(mail, never()).sendCode(eq("nobody@example.com"), any(), anyString());
        when(mail.configured()).thenReturn(false);
        JsonNode body = post("/api/app/v1/member/auth/email-code", null, Map.of("email", "new@example.com",
                "purpose", "REGISTER"));
        assertThat(body.path("code").asInt()).isEqualTo(503);
        assertThat(body.path("message").asText()).contains("尚未配置");
    }

    /**
     * 非 VIP 请求播放线路被拒；后台开通后可取到且只含 M3U8/MP4；关闭 VIP 开关后登录即可观看。
     */
    @Test
    void requiresVipForPlayLinesAndFiltersUnplayableUrls() {
        String token = newMember("vip@example.com", "device-v1");
        String lines = "/api/app/v1/video/videos/" + videoId + "/play-lines";
        assertThat(get("/api/app/v1/video/videos", token).path("code").asInt()).isZero();
        assertThat(get(lines, null).path("code").asInt()).isEqualTo(401);
        JsonNode denied = get(lines, token);
        assertThat(denied.path("code").asInt()).isEqualTo(403);
        assertThat(denied.path("data").isNull()).isTrue();
        Long memberId = memberId("vip@example.com");
        members.extendVip(memberId, new MemberVipExtendRequest(30, "测试开通"));
        JsonNode allowed = get(lines, token);
        assertThat(allowed.path("code").asInt()).isZero();
        assertThat(allowed.path("data")).hasSize(1);
        JsonNode episodes = allowed.path("data").get(0).path("episodes");
        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).path("urlType").asText()).isEqualTo("M3U8");
        assertThat(members.vipLogs(memberId)).hasSize(1);
        String plain = newMember("plain@example.com", "device-p1");
        assertThat(get(lines, plain).path("code").asInt()).isEqualTo(403);
        settings.updateFeatures(new AppFeatureRequest(false, true, 2, null, null));
        assertThat(get(lines, plain).path("code").asInt()).isZero();
    }

    /**
     * 超过同时登录台数时最早登录的设备被踢出；后台强制下线立即生效；同一设备重登不占名额。
     */
    @Test
    void kicksOldestDeviceBeyondLimit() {
        settings.updateFeatures(new AppFeatureRequest(true, true, 2, null, null));
        newMember("multi@example.com", "device-m0");
        String first = login("multi@example.com", "device-m1");
        String second = login("multi@example.com", "device-m2");
        assertThat(get("/api/app/v1/member/profile", first).path("code").asInt()).isZero();
        String third = login("multi@example.com", "device-m3");
        assertThat(get("/api/app/v1/member/profile", first).path("code").asInt()).isEqualTo(401);
        assertThat(get("/api/app/v1/member/profile", second).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/profile", third).path("code").asInt()).isZero();
        String again = login("multi@example.com", "device-m3");
        assertThat(get("/api/app/v1/member/profile", third).path("code").asInt()).isEqualTo(401);
        assertThat(get("/api/app/v1/member/profile", again).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/profile", second).path("code").asInt()).isZero();
        Long memberId = memberId("multi@example.com");
        var devices = members.devices(1, 20, memberId, null, true).records();
        assertThat(devices).extracting(device -> device.deviceCode()).containsExactlyInAnyOrder("device-m2", "device-m3");
        assertThat(jdbc.queryForList("SELECT offline_reason FROM member_device WHERE member_id=? AND online=0",
                String.class, memberId)).containsOnly("LIMIT").hasSize(2);
        String m3 = devices.stream().filter(device -> "device-m3".equals(device.deviceCode())).findFirst()
                .orElseThrow().id();
        members.forceOffline(Long.valueOf(m3));
        assertThat(get("/api/app/v1/member/profile", again).path("code").asInt()).isEqualTo(401);
    }

    /**
     * 普通用户令牌不能访问管理接口；退出与找回密码后旧令牌失效；停用账号令牌立即失效。
     */
    @Test
    void separatesTokensAndRevokesSessions() {
        String token = newMember("safe@example.com", "device-s1");
        assertThat(get("/api/admin/v1/auth/me", token).path("code").asInt()).isEqualTo(401);
        assertThat(post("/api/app/v1/member/auth/logout", token, Map.of()).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/profile", token).path("code").asInt()).isEqualTo(401);
        String second = login("safe@example.com", "device-s2");
        post("/api/app/v1/member/auth/email-code", null, Map.of("email", "safe@example.com", "purpose", "RESET_PASSWORD"));
        assertThat(post("/api/app/v1/member/auth/password/reset", null, Map.of("email", "safe@example.com",
                "code", sentCodes.get("safe@example.com"), "newPassword", "newpass1")).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/profile", second).path("code").asInt()).isEqualTo(401);
        assertThat(loginBody("safe@example.com", "secret1", "device-s3").path("code").asInt()).isEqualTo(401);
        String third = loginBody("safe@example.com", "newpass1", "device-s3").path("data").path("accessToken").asText();
        members.changeStatus(memberId("safe@example.com"),
                new cn.orangenode.forge.member.request.MemberStatusRequest("DISABLED"));
        assertThat(get("/api/app/v1/member/profile", third).path("code").asInt()).isEqualTo(401);
        assertThat(loginBody("safe@example.com", "newpass1", "device-s3").path("code").asInt()).isEqualTo(403);
    }

    /**
     * 后台配置开屏、轮播、推荐位与热搜后，App 启动配置匿名可读，首页与热搜需要登录并返回配置内容。
     */
    @Test
    void servesConfiguredHomeContent() {
        content.saveSplash(null, new AppSplashRequest("国庆", "501", 5, true, null, null, true, 0));
        content.saveSplash(null, new AppSplashRequest("已过期", "502", 3, false, null,
                java.time.Instant.parse("2020-01-01T00:00:00Z"), true, 0));
        content.saveBanner(null, new AppBannerRequest("热映", "503", "VIDEO", String.valueOf(videoId), null, null, true, 1));
        content.saveSection(null, new AppSectionRequest("热播电影", "CATEGORY", String.valueOf(categoryId), null, 10,
                true, 1));
        content.saveSection(null, new AppSectionRequest("编辑推荐", "MANUAL", null, List.of(String.valueOf(videoId)), 5,
                true, 2));
        settings.updateHotSearch(new AppHotSearchRequest(List.of("测试影片", " 庆余年 ", "测试影片", "违规词剧"),
                List.of("违规")));
        JsonNode launch = get("/api/app/v1/app/launch", null);
        assertThat(launch.path("code").asInt()).isZero();
        assertThat(launch.path("data").path("splash").path("imageUrl").asText()).isEqualTo("/api/app/v1/app/images/501");
        assertThat(launch.path("data").path("splash").path("durationSeconds").asInt()).isEqualTo(5);
        assertThat(get("/api/app/v1/app/home", null).path("code").asInt()).isEqualTo(401);
        String token = newMember("home@example.com", "device-h1");
        JsonNode home = get("/api/app/v1/app/home", token).path("data");
        assertThat(home.path("banners")).hasSize(1);
        assertThat(home.path("banners").get(0).path("targetId").asText()).isEqualTo(String.valueOf(videoId));
        assertThat(home.path("sections")).hasSize(2);
        assertThat(home.path("sections").get(0).path("videos").get(0).path("name").asText()).isEqualTo("测试影片");
        assertThat(get("/api/app/v1/app/hot-search", token).path("data").path("words").toString())
                .isEqualTo("[\"测试影片\",\"庆余年\"]");
        byte[] image = http.get().uri("/api/app/v1/app/images/503").exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG).expectBody(byte[].class).returnResult()
                .getResponseBody();
        assertThat(new String(image, StandardCharsets.UTF_8)).isEqualTo("png-bytes");
        assertThat(get("/api/app/v1/app/images/999", null).path("code").asInt()).isEqualTo(404);
        assertThat(post("/api/admin/v1/app/features", token, Map.of()).path("code").asInt()).isEqualTo(401);
    }

    /**
     * 执行迁移脚本，只移除 H2 不支持的 MySQL 引擎声明与函数。
     */
    private void migrate(String resource) throws Exception {
        String sql = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?m)^--.*$", "")
                .replaceAll("ENGINE=InnoDB[^;]+", "")
                .replace("match_name(128)", "match_name")
                .replace("UTC_TIMESTAMP(3)", "CURRENT_TIMESTAMP");
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                jdbc.execute(command);
            }
        }
    }

    /**
     * 发送验证码并注册新用户，返回令牌。
     */
    private String newMember(String email, String deviceCode) {
        post("/api/app/v1/member/auth/email-code", null, Map.of("email", email, "purpose", "REGISTER"));
        JsonNode body = register(email, sentCodes.get(email), deviceCode);
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("accessToken").asText();
    }

    /**
     * 提交注册请求。
     */
    private JsonNode register(String email, String code, String deviceCode) {
        return post("/api/app/v1/member/auth/register", null, Map.of("email", email, "code", code,
                "password", "secret1", "deviceCode", deviceCode, "deviceName", "测试电视"));
    }

    /**
     * 使用默认密码登录并返回令牌。
     */
    private String login(String email, String deviceCode) {
        JsonNode body = loginBody(email, "secret1", deviceCode);
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("accessToken").asText();
    }

    /**
     * 提交登录请求。
     */
    private JsonNode loginBody(String email, String password, String deviceCode) {
        return post("/api/app/v1/member/auth/login", null, Map.of("email", email, "password", password,
                "deviceCode", deviceCode, "deviceName", "测试电视"));
    }

    /**
     * 按邮箱查询用户 ID。
     */
    private Long memberId(String email) {
        return jdbc.queryForObject("SELECT id FROM member_user WHERE email=?", Long.class, email);
    }

    /**
     * 发送 GET 请求，断言 HTTP 200 并解析响应体。
     */
    private JsonNode get(String path, String token) {
        return json.readTree(http.get().uri(path).headers(headers -> bearer(headers, token)).exchange().expectStatus().isOk().expectBody(String.class).returnResult()
                .getResponseBody());
    }

    /**
     * 发送 JSON POST 请求，断言 HTTP 200 并解析响应体。
     */
    private JsonNode post(String path, String token, Map<String, ?> body) {
        return json.readTree(http.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> bearer(headers, token)).body(body).exchange().expectStatus().isOk().expectBody(String.class)
                .returnResult().getResponseBody());
    }

    /**
     * 有令牌时写入 Bearer 认证头。
     */
    private static void bearer(org.springframework.http.HttpHeaders headers, String token) {
        if (token != null) {
            headers.setBearerAuth(token);
        }
    }
}
