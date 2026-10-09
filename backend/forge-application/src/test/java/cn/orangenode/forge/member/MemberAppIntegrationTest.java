package cn.orangenode.forge.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
import cn.orangenode.forge.app.request.AppReleaseMinVersionRequest;
import cn.orangenode.forge.app.request.AppReleasePublishRequest;
import cn.orangenode.forge.app.request.AppSplashRequest;
import cn.orangenode.forge.app.service.AppContentService;
import cn.orangenode.forge.app.service.AppReleaseService;
import cn.orangenode.forge.app.service.AppSettingsService;
import cn.orangenode.forge.framework.storage.StoredFileContent;
import cn.orangenode.forge.framework.storage.StoredFileReader;
import cn.orangenode.forge.member.request.MemberActivationBatchRequest;
import cn.orangenode.forge.member.request.MemberActivationCreateRequest;
import cn.orangenode.forge.member.request.MemberVipExtendRequest;
import cn.orangenode.forge.member.service.MemberActivationService;
import cn.orangenode.forge.member.service.MemberManagementService;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 普通用户与 App 管理的端到端验证：用户名注册、VIP 观看授权、同时登录台数、令牌隔离与首页配置。
 *
 * <p>表结构由真实迁移脚本派生（只移除 MySQL 引擎与函数差异，H2 以 IGNORECASE 模拟 MySQL 不区分大小写的排序规则），Redis 使用内存替身，不真实发信。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_member;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR;IGNORECASE=TRUE",
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
    private MemberActivationService activations;
    @Autowired
    private AppContentService content;
    @Autowired
    private AppSettingsService settings;
    @Autowired
    private AppReleaseService releases;
    @MockitoBean
    private StringRedisTemplate redis;
    @MockitoBean
    private StoredFileReader files;
    @LocalServerPort
    private int port;
    private RestTestClient http;
    private long videoId;
    private long categoryId;

    /**
     * 重建表、种入一部含 M3U8 与 OTHER 线路的影片，并安装替身发送器与文件读取。
     */
    @BeforeEach
    void prepare() throws Exception {
        new RedisTestDouble(redis);
        RuntimeSchema.recreateSystemAndAuditTables(jdbc);
        for (String table : List.of("member_activation_code", "member_vip_log", "member_device", "member_user", "app_splash", "app_banner",
                "app_section", "app_settings", "app_release")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        for (String table : List.of("category", "source", "source_category", "video", "origin", "play_line",
                "episode", "settings", "task", "run", "pending")) {
            jdbc.execute("DROP TABLE IF EXISTS video_" + table);
        }
        migrate("db/migration/video/V20260930200000__video_collection_tables.sql");
        jdbc.execute("ALTER TABLE video_video ADD COLUMN name_initials VARCHAR(255) NULL");
        migrate("db/migration/member/V20261007120000__member_tables.sql");
        migrate("db/migration/member/V20261008150000__member_username.sql");
        migrate("db/migration/member/V20261009090000__member_activation_code.sql");
        migrate("db/migration/member/V20261009100000__member_activation_vip_days.sql");
        migrate("db/migration/app/V20261007121000__app_tables.sql");
        migrate("db/migration/app/V20261008170000__app_ads_and_release.sql");
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
        when(files.exists(anyLong())).thenReturn(true);
        when(files.open(anyLong())).thenAnswer(invocation -> new StoredFileContent(
                new ByteArrayInputStream("png-bytes".getBytes(StandardCharsets.UTF_8)), "image/png", 9));
        http = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /**
     * 用户名注册成功并直接登录；非法用户名 400，用户名不区分大小写唯一 409，关闭注册 403，同一来源每日注册数受限 429。
     */
    @Test
    void registersWithUsernameOnly() {
        assertThat(register("ab", "device-a0").path("code").asInt()).isEqualTo(400);
        assertThat(register("bad-name!", "device-a0").path("code").asInt()).isEqualTo(400);
        assertThat(register("a_very_long_username_21", "device-a0").path("code").asInt()).isEqualTo(400);
        JsonNode ok = register("User_01", "device-a1");
        assertThat(ok.path("code").asInt()).isZero();
        String token = ok.path("data").path("accessToken").asText();
        assertThat(ok.path("data").path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(ok.path("data").path("expiresIn").asLong()).isEqualTo(2592000L);
        assertThat(ok.path("data").path("profile").path("vipActive").asBoolean()).isFalse();
        JsonNode profile = get("/api/app/v1/member/profile", token);
        assertThat(profile.path("data").path("username").asText()).isEqualTo("User_01");
        assertThat(profile.path("data").path("nickname").asText()).isEqualTo("User_01");
        assertThat(register("user_01", "device-a2").path("code").asInt()).isEqualTo(409);
        assertThat(loginBody("USER_01", "secret1", "device-a3").path("code").asInt()).isZero();
        assertThat(loginBody("User_01", "wrong-pass", "device-a3").path("message").asText()).isEqualTo("用户名或密码错误");
        assertThat(post("/api/app/v1/member/auth/email-code", null, Map.of("email", "x@example.com",
                "purpose", "REGISTER")).path("code").asInt()).isEqualTo(401);
        for (int i = 2; i <= 10; i++) {
            assertThat(register("user_ip_" + i, "device-r" + i).path("code").asInt()).isZero();
        }
        assertThat(register("user_ip_11", "device-r11").path("code").asInt()).isEqualTo(429);
        settings.updateFeatures(new AppFeatureRequest(true, false, 2, null, null));
        assertThat(register("other_user", "device-a4").path("code").asInt()).isEqualTo(403);
    }

    /**
     * 注册必须使用激活码：忽略大小写与短横线；一码只能注册一次；注册成功从现在起开通该码的会员天数；不正确、已作废、已过期都拒绝；
     * 列表可看到使用者，按状态筛选；作废只对未使用的码生效；单次生成数量受限。
     */
    @Test
    void registersOnlyWithValidActivationCode() {
        var generated = activations.create(new MemberActivationCreateRequest(3, 30, "第一批"));
        assertThat(generated).hasSize(3);
        String code = generated.get(0).code();
        assertThat(code).matches("^[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$");
        assertThat(post("/api/app/v1/member/auth/register", null, Map.of("username", "no_code_user",
                "password", "secret1", "deviceCode", "device-k0", "deviceName", "测试电视")).path("code").asInt())
                .isEqualTo(400);
        assertThat(registerWithCode("wrong_code", "device-k0", "AAAA-BBBB-CCCC").path("message").asText())
                .isEqualTo("激活码不正确");
        JsonNode ok = registerWithCode("code_user", "device-k1", " " + code.toLowerCase().replace("-", "") + " ");
        assertThat(ok.path("code").asInt()).isZero();
        assertThat(ok.path("data").path("profile").path("vipActive").asBoolean()).isTrue();
        java.time.Instant vipUntil = java.time.Instant.parse(ok.path("data").path("profile").path("vipExpireAt").asText());
        assertThat(vipUntil).isBetween(java.time.Instant.now().plus(java.time.Duration.ofDays(30)).minusSeconds(120),
                java.time.Instant.now().plus(java.time.Duration.ofDays(30)));
        assertThat(registerWithCode("code_user2", "device-k2", code).path("message").asText()).isEqualTo("激活码已被使用");
        var used = activations.page(1, 10, "code_user", "USED", null);
        assertThat(used.total()).isEqualTo(1);
        assertThat(used.records().get(0).code()).isEqualTo(code);
        assertThat(used.records().get(0).usedUsername()).isEqualTo("code_user");
        assertThat(used.records().get(0).remark()).isEqualTo("第一批");
        assertThat(used.records().get(0).vipDays()).isEqualTo(30);
        assertThat(activations.page(1, 10, null, "UNUSED", "第一").total()).isEqualTo(2);
        Long usedId = Long.valueOf(used.records().get(0).id());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> activations.revoke(usedId))
                .hasMessageContaining("已被使用");
        String revoked = generated.get(1).code();
        Long revokedId = Long.valueOf(generated.get(1).id());
        assertThat(activations.revoke(revokedId).status()).isEqualTo("REVOKED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> activations.revoke(revokedId))
                .hasMessageContaining("已作废");
        assertThat(registerWithCode("revoked_user", "device-k3", revoked).path("message").asText()).isEqualTo("激活码已作废");
        jdbc.update("UPDATE member_activation_code SET expire_at=DATEADD('DAY', -1, CURRENT_TIMESTAMP) WHERE id=?",
                Long.valueOf(generated.get(2).id()));
        assertThat(activations.page(1, 10, null, "EXPIRED", null).total()).isEqualTo(1);
        assertThat(registerWithCode("expired_user", "device-k4", generated.get(2).code()).path("message").asText())
                .isEqualTo("激活码已过期");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_user", Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> activations.create(
                new MemberActivationCreateRequest(501, 30, null))).hasMessageContaining("一次最多生成 500 个");
    }

    /**
     * 批量作废只处理未使用的码；批量删除跳过已使用的码；导出与列表筛选一致；超过单次上限 400。
     */
    @Test
    void revokesDeletesAndExportsInBatch() {
        var generated = activations.create(new MemberActivationCreateRequest(4, 90, "批量"));
        List<String> ids = generated.stream().map(item -> item.id()).toList();
        assertThat(registerWithCode("batch_user", "device-b1", generated.get(0).code()).path("code").asInt()).isZero();
        var revoked = activations.revokeBatch(new MemberActivationBatchRequest(List.of(ids.get(0), ids.get(1), ids.get(2))));
        assertThat(revoked.succeeded()).isEqualTo(2);
        assertThat(revoked.skipped()).isEqualTo(1);
        assertThat(activations.export(null, "REVOKED", "批量")).hasSize(2);
        assertThat(activations.export(null, null, "批量")).extracting(item -> item.vipDays()).containsOnly(90);
        var deleted = activations.deleteBatch(new MemberActivationBatchRequest(ids));
        assertThat(deleted.succeeded()).isEqualTo(3);
        assertThat(deleted.skipped()).isEqualTo(1);
        var left = activations.export(null, null, "批量");
        assertThat(left).hasSize(1);
        assertThat(left.get(0).usedUsername()).isEqualTo("batch_user");
        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, 501).mapToObj(String::valueOf).toList();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> activations.deleteBatch(
                new MemberActivationBatchRequest(tooMany))).hasMessageContaining("一次最多处理 500 个");
    }

    /**
     * 同一来源当天输错激活码达到上限后，即使输入正确的码也返回 429。
     */
    @Test
    void limitsWrongActivationCodesPerAddress() {
        for (int i = 0; i < 20; i++) {
            assertThat(registerWithCode("guess_user", "device-g0", "ZZZZ-ZZZZ-ZZ" + (char) ('A' + i % 20))
                    .path("code").asInt()).isEqualTo(400);
        }
        JsonNode blocked = register("guess_user", "device-g0");
        assertThat(blocked.path("code").asInt()).isEqualTo(429);
        assertThat(blocked.path("message").asText()).isEqualTo("激活码输错次数过多，请明天再试");
    }

    /**
     * 关闭公开注册后由后台新建账号：可同时开通 VIP 并直接登录；用户名不区分大小写重复时拒绝。
     */
    @Test
    void createsMembersFromAdmin() {
        settings.updateFeatures(new AppFeatureRequest(true, false, 2, null, null));
        assertThat(register("self_signup", "device-c0").path("code").asInt()).isEqualTo(403);
        var created = members.create(new cn.orangenode.forge.member.request.MemberCreateRequest("Admin_Made",
                "secret1", null, 30));
        assertThat(created.username()).isEqualTo("Admin_Made");
        assertThat(created.vipActive()).isTrue();
        JsonNode body = loginBody("admin_made", "secret1", "device-c1");
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("profile").path("nickname").asText()).isEqualTo("Admin_Made");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> members.create(
                new cn.orangenode.forge.member.request.MemberCreateRequest("ADMIN_MADE", "secret1", null, null)))
                .hasMessageContaining("已被使用");
    }

    /**
     * 非 VIP 请求播放线路被拒；后台开通后可取到且只含 M3U8/MP4；关闭 VIP 开关后登录即可观看。
     */
    @Test
    void requiresVipForPlayLinesAndFiltersUnplayableUrls() {
        String token = newMember("vip_user", "device-v1");
        String lines = "/api/app/v1/video/videos/" + videoId + "/play-lines";
        assertThat(get("/api/app/v1/video/videos", token).path("code").asInt()).isZero();
        assertThat(get(lines, null).path("code").asInt()).isEqualTo(401);
        JsonNode denied = get(lines, token);
        assertThat(denied.path("code").asInt()).isEqualTo(403);
        assertThat(denied.path("data").isNull()).isTrue();
        Long memberId = memberId("vip_user");
        members.extendVip(memberId, new MemberVipExtendRequest(30, "测试开通"));
        JsonNode allowed = get(lines, token);
        assertThat(allowed.path("code").asInt()).isZero();
        assertThat(allowed.path("data")).hasSize(1);
        JsonNode episodes = allowed.path("data").get(0).path("episodes");
        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).path("urlType").asText()).isEqualTo("M3U8");
        assertThat(members.vipLogs(memberId)).hasSize(1);
        String plain = newMember("plain_user", "device-p1");
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
        newMember("multi_user", "device-m0");
        String first = login("multi_user", "device-m1");
        String second = login("multi_user", "device-m2");
        assertThat(get("/api/app/v1/member/profile", first).path("code").asInt()).isZero();
        String third = login("multi_user", "device-m3");
        assertThat(get("/api/app/v1/member/profile", first).path("code").asInt()).isEqualTo(401);
        assertThat(get("/api/app/v1/member/profile", second).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/profile", third).path("code").asInt()).isZero();
        String again = login("multi_user", "device-m3");
        assertThat(get("/api/app/v1/member/profile", third).path("code").asInt()).isEqualTo(401);
        assertThat(get("/api/app/v1/member/profile", again).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/profile", second).path("code").asInt()).isZero();
        Long memberId = memberId("multi_user");
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
     * 普通用户令牌不能访问管理接口；退出与后台重置密码后旧令牌失效；停用账号令牌立即失效。
     */
    @Test
    void separatesTokensAndRevokesSessions() {
        String token = newMember("safe_user", "device-s1");
        assertThat(get("/api/admin/v1/auth/me", token).path("code").asInt()).isEqualTo(401);
        assertThat(post("/api/app/v1/member/auth/logout", token, Map.of()).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/profile", token).path("code").asInt()).isEqualTo(401);
        String second = login("safe_user", "device-s2");
        members.resetPassword(memberId("safe_user"),
                new cn.orangenode.forge.member.request.MemberAdminPasswordRequest("newpass1"));
        assertThat(get("/api/app/v1/member/profile", second).path("code").asInt()).isEqualTo(401);
        assertThat(loginBody("safe_user", "secret1", "device-s3").path("code").asInt()).isEqualTo(401);
        String third = loginBody("safe_user", "newpass1", "device-s3").path("data").path("accessToken").asText();
        members.changeStatus(memberId("safe_user"),
                new cn.orangenode.forge.member.request.MemberStatusRequest("DISABLED"));
        assertThat(get("/api/app/v1/member/profile", third).path("code").asInt()).isEqualTo(401);
        assertThat(loginBody("safe_user", "newpass1", "device-s3").path("code").asInt()).isEqualTo(403);
    }

    /**
     * 后台配置开屏、轮播、推荐位与热搜后，App 启动配置匿名可读，首页与热搜需要登录并返回配置内容。
     */
    @Test
    void servesConfiguredHomeContent() {
        content.saveSplash(null, new AppSplashRequest("国庆", "501", 5, true, null, null, true, 0, "URL",
                "https://ad.example.com/landing"));
        content.saveSplash(null, new AppSplashRequest("已过期", "502", 3, false, null,
                java.time.Instant.parse("2020-01-01T00:00:00Z"), true, 0, null, null));
        content.saveBanner(null, new AppBannerRequest("热映", "503", "VIDEO", String.valueOf(videoId), null, null, true, 1,
                null));
        content.saveBanner(null, new AppBannerRequest("广告", "504", "URL", null, null, null, true, 2,
                "https://ad.example.com/banner"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> content.saveBanner(null, new AppBannerRequest("坏链接",
                "505", "URL", null, null, null, true, 3, "javascript:alert(1)"))).hasMessageContaining("http");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> content.saveSplash(null, new AppSplashRequest("坏影片",
                "506", 3, true, null, null, true, 1, "VIDEO", "99999"))).hasMessageContaining("影片不存在");
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
        assertThat(launch.path("data").path("splash").path("targetType").asText()).isEqualTo("URL");
        assertThat(launch.path("data").path("splash").path("targetValue").asText())
                .isEqualTo("https://ad.example.com/landing");
        assertThat(launch.path("data").path("release").isNull()).isTrue();
        assertThat(get("/api/app/v1/app/home", null).path("code").asInt()).isEqualTo(401);
        String token = newMember("home_user", "device-h1");
        JsonNode home = get("/api/app/v1/app/home", token).path("data");
        assertThat(home.path("banners")).hasSize(2);
        assertThat(home.path("banners").get(0).path("targetId").asText()).isEqualTo(String.valueOf(videoId));
        assertThat(home.path("banners").get(1).path("targetType").asText()).isEqualTo("URL");
        assertThat(home.path("banners").get(1).path("targetUrl").asText()).isEqualTo("https://ad.example.com/banner");
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
     * 上传的 APK 自动解析包名与版本号：发布后启动配置带出版本与外链；同版本重复发布、非 APK 文件被拒绝；
     * 可调整最低可用版本，撤销后启动配置不再带版本。
     */
    @Test
    void publishesReleaseFromUploadedApk() throws Exception {
        byte[] apk = new ClassPathResource("apk/sample-manifest-only.apk").getContentAsByteArray();
        when(files.open(900L)).thenAnswer(invocation -> new StoredFileContent(new ByteArrayInputStream(apk),
                "application/vnd.android.package-archive", apk.length));
        when(files.publicPath(900L)).thenReturn("/api/public/v1/files/token900/orange.apk");
        var published = releases.publish(new AppReleasePublishRequest("900", "首个版本", true), 1L);
        assertThat(published.packageName()).isEqualTo("cn.orangenode.orange_app");
        assertThat(published.versionCode()).isEqualTo(1);
        assertThat(published.versionName()).isEqualTo("1.0.0");
        assertThat(published.minVersionCode()).isEqualTo(1);
        assertThat(published.sizeBytes()).isEqualTo(apk.length);
        assertThat(published.sha256()).isEqualTo(java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(apk)));
        JsonNode release = get("/api/app/v1/app/launch", null).path("data").path("release");
        assertThat(release.path("versionCode").asInt()).isEqualTo(1);
        assertThat(release.path("minVersionCode").asInt()).isEqualTo(1);
        assertThat(release.path("apkUrl").asText()).isEqualTo("/api/public/v1/files/token900/orange.apk");
        assertThat(release.path("sha256").asText()).isEqualTo(published.sha256());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> releases.publish(
                new AppReleasePublishRequest("900", null, false), 1L)).hasMessageContaining("不高于");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> releases.publish(
                new AppReleasePublishRequest("901", null, false), 1L)).hasMessageContaining("APK");
        assertThat(releases.updateMinVersion(new AppReleaseMinVersionRequest(null), 1L).minVersionCode()).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> releases.updateMinVersion(
                new AppReleaseMinVersionRequest(2), 1L)).hasMessageContaining("不能高于");
        releases.withdraw(1L);
        assertThat(get("/api/app/v1/app/launch", null).path("data").path("release").isNull()).isTrue();
    }

    /**
     * 执行迁移脚本，只移除 H2 不支持的 MySQL 引擎声明与函数。
     */
    private void migrate(String resource) throws Exception {
        String sql = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?m)^--.*$", "")
                .replaceAll("ENGINE=InnoDB[^;]+", "")
                .replace("match_name(128)", "match_name")
                .replace("UTC_TIMESTAMP(3)", "CURRENT_TIMESTAMP")
                // 数据回填语句只作用于已有数据，空库测试中跳过（H2 不支持 MySQL 多表 UPDATE 与窗口函数写法）。
                .replaceAll("(?s)UPDATE [^;]*;", "")
                .replace("DROP INDEX uk_member_user_email", "DROP CONSTRAINT uk_member_user_email");
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                jdbc.execute(command);
            }
        }
    }

    /**
     * 注册新用户并返回令牌。
     */
    private String newMember(String username, String deviceCode) {
        JsonNode body = register(username, deviceCode);
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("accessToken").asText();
    }

    /**
     * 提交注册请求。
     */
    private JsonNode register(String username, String deviceCode) {
        return registerWithCode(username, deviceCode, freshCode());
    }

    /**
     * 使用指定激活码提交注册请求。
     */
    private JsonNode registerWithCode(String username, String deviceCode, String activationCode) {
        return post("/api/app/v1/member/auth/register", null, Map.of("username", username,
                "password", "secret1", "activationCode", activationCode, "deviceCode", deviceCode,
                "deviceName", "测试电视"));
    }

    /**
     * 生成一个不带会员时长的激活码（注册后不开通 VIP，便于验证 VIP 相关规则）。
     */
    private String freshCode() {
        return activations.create(new MemberActivationCreateRequest(1, null, null)).get(0).code();
    }

    /**
     * 使用默认密码登录并返回令牌。
     */
    private String login(String username, String deviceCode) {
        JsonNode body = loginBody(username, "secret1", deviceCode);
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("accessToken").asText();
    }

    /**
     * 提交登录请求。
     */
    private JsonNode loginBody(String username, String password, String deviceCode) {
        return post("/api/app/v1/member/auth/login", null, Map.of("username", username, "password", password,
                "deviceCode", deviceCode, "deviceName", "测试电视"));
    }

    /**
     * 按用户名查询用户 ID。
     */
    private Long memberId(String username) {
        return jdbc.queryForObject("SELECT id FROM member_user WHERE username=?", Long.class, username);
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
