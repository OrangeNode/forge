package cn.orangenode.forge.member;

import static org.assertj.core.api.Assertions.assertThat;

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

import cn.orangenode.forge.app.request.AppFeedbackHandleRequest;
import cn.orangenode.forge.app.service.AppFeedbackService;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T3 个人数据端到端验证：观看历史与断点续播、收藏、我的设备、意见反馈与后台处理。
 *
 * <p>表结构由真实迁移脚本派生（只移除 MySQL 引擎与函数差异），Redis 使用内存替身。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_personal;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR;IGNORECASE=TRUE",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false", "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false", "forge.video.worker-enabled=false",
    "forge.video.app-rate-limit=100000", "forge.app.feedback-daily-limit=2"
})
class MemberPersonalIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private AppFeedbackService feedbacks;
    @MockitoBean
    private StringRedisTemplate redis;
    @LocalServerPort
    private int port;
    private RestTestClient http;

    /**
     * 重建表、种入两部公开影片与一部下架影片，。
     */
    @BeforeEach
    void prepare() throws Exception {
        new RedisTestDouble(redis);
        RuntimeSchema.recreateSystemAndAuditTables(jdbc);
        for (String table : List.of("member_watch_history", "member_favorite", "member_vip_log", "member_device",
                "member_user", "app_feedback", "app_splash", "app_banner", "app_section", "app_settings")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        for (String table : List.of("category", "source", "source_category", "video", "origin", "play_line",
                "episode", "settings", "task", "run", "pending")) {
            jdbc.execute("DROP TABLE IF EXISTS video_" + table);
        }
        migrate("db/migration/video/V20260930200000__video_collection_tables.sql");
        jdbc.execute("ALTER TABLE video_video ADD COLUMN name_initials VARCHAR(255) NULL");
        migrate("db/migration/member/V20261007120000__member_tables.sql");
        migrate("db/migration/member/V20261008120000__member_personal_data.sql");
        migrate("db/migration/member/V20261008150000__member_username.sql");
        migrate("db/migration/app/V20261007121000__app_tables.sql");
        migrate("db/migration/app/V20261008120100__app_feedback.sql");
        migrate("db/migration/app/V20261008150100__app_feedback_username.sql");
        jdbc.update("INSERT INTO video_category (id,parent_id,name,sort_no,enabled,deleted,created_at,updated_at)"
                + " VALUES (1,0,'电影',1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (long id : List.of(10L, 11L, 12L)) {
            jdbc.update("INSERT INTO video_video (id,name,match_name,category_id,published,metadata_locked,deleted,"
                    + "created_at,updated_at) VALUES (?,?,?,1,?,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    id, "影片" + id, "影片" + id, id == 12L ? 0 : 1);
        }
        http = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /**
     * 观看进度只保留每部影片最近一次，按最近观看倒序；下架影片不能上报；删除与清空生效；未登录 401。
     */
    @Test
    void keepsLatestProgressPerVideo() {
        String token = newMember("history_user", "phone-0001");
        assertThat(get("/api/app/v1/member/history", null).path("code").asInt()).isEqualTo(401);
        JsonNode empty = get("/api/app/v1/member/history/10", token).path("data");
        assertThat(empty.isNull() || empty.isMissingNode()).isTrue();
        assertThat(put("/api/app/v1/member/history/10", token, progress(0, "第1集", 120)).path("code").asInt()).isZero();
        assertThat(put("/api/app/v1/member/history/11", token, progress(2, "第3集", 30)).path("code").asInt()).isZero();
        assertThat(put("/api/app/v1/member/history/10", token, progress(1, "第2集", 600)).path("code").asInt()).isZero();
        assertThat(put("/api/app/v1/member/history/12", token, progress(0, "第1集", 5)).path("code").asInt())
                .isEqualTo(404);

        JsonNode page = get("/api/app/v1/member/history", token).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("records").get(0).path("videoId").asText()).isEqualTo("10");
        assertThat(page.path("records").get(0).path("episodeIndex").asInt()).isEqualTo(1);
        assertThat(page.path("records").get(0).path("positionSeconds").asInt()).isEqualTo(600);
        assertThat(page.path("records").get(0).path("videoName").asText()).isEqualTo("影片10");
        JsonNode resume = get("/api/app/v1/member/history/10", token).path("data");
        assertThat(resume.path("episodeName").asText()).isEqualTo("第2集");

        assertThat(delete("/api/app/v1/member/history/11", token).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/history", token).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(delete("/api/app/v1/member/history", token).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/history", token).path("data").path("total").asLong()).isZero();
    }

    /**
     * 收藏幂等、可查询状态与取消；下架影片不能收藏；收藏只属于自己。
     */
    @Test
    void favoritesAreIdempotentAndPrivate() {
        String token = newMember("fav_user", "phone-0002");
        String other = newMember("other_user", "phone-0003");
        assertThat(put("/api/app/v1/member/favorites/10", token, null).path("code").asInt()).isZero();
        assertThat(put("/api/app/v1/member/favorites/10", token, null).path("code").asInt()).isZero();
        assertThat(put("/api/app/v1/member/favorites/12", token, null).path("code").asInt()).isEqualTo(404);
        assertThat(get("/api/app/v1/member/favorites/10", token).path("data").path("favorited").asBoolean()).isTrue();
        assertThat(get("/api/app/v1/member/favorites/10", other).path("data").path("favorited").asBoolean()).isFalse();
        JsonNode page = get("/api/app/v1/member/favorites", token).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(page.path("records").get(0).path("videoName").asText()).isEqualTo("影片10");
        assertThat(get("/api/app/v1/member/favorites", other).path("data").path("total").asLong()).isZero();
        assertThat(delete("/api/app/v1/member/favorites/10", token).path("code").asInt()).isZero();
        assertThat(get("/api/app/v1/member/favorites/10", token).path("data").path("favorited").asBoolean()).isFalse();
    }

    /**
     * 我的设备：手机能看到用电视登录的设备并让其下线，电视令牌随即失效；不能让当前设备或他人设备下线。
     */
    @Test
    void managesOwnDevices() {
        String phone = newMember("devices_user", "phone-0004");
        String stranger = newMember("stranger_user", "phone-0005");
        String tv = post("/api/app/v1/member/auth/login", null, Map.of("username", "devices_user",
                "password", "secret1", "deviceCode", "tv-device-0001", "deviceName", "客厅电视"))
                .path("data").path("accessToken").asText();
        assertThat(get("/api/app/v1/member/profile", tv).path("code").asInt()).isZero();

        JsonNode devices = get("/api/app/v1/member/devices", phone).path("data");
        assertThat(devices).hasSize(2);
        String tvDevice = null;
        for (JsonNode device : devices) {
            if ("客厅电视".equals(device.path("deviceName").asText())) {
                tvDevice = device.path("id").asText();
                assertThat(device.path("current").asBoolean()).isFalse();
            } else {
                assertThat(device.path("current").asBoolean()).isTrue();
                assertThat(post("/api/app/v1/member/devices/" + device.path("id").asText() + "/offline", phone, null)
                        .path("code").asInt()).isEqualTo(400);
            }
        }
        assertThat(post("/api/app/v1/member/devices/" + tvDevice + "/offline", stranger, null).path("code").asInt())
                .isEqualTo(404);
        assertThat(post("/api/app/v1/member/devices/" + tvDevice + "/offline", phone, null).path("code").asInt())
                .isZero();
        assertThat(post("/api/app/v1/member/devices/" + tvDevice + "/offline", phone, null).path("code").asInt())
                .isEqualTo(409);
        assertThat(get("/api/app/v1/member/profile", tv).path("code").asInt()).isEqualTo(401);
        assertThat(get("/api/app/v1/member/profile", phone).path("code").asInt()).isZero();
    }

    /**
     * 反馈：播放报错必须带影片并保存名称快照；每日条数受限；管理接口不接受普通用户令牌；后台标记已处理只能一次。
     */
    @Test
    void collectsFeedbackForAdmin() {
        String token = newMember("feedback_user", "phone-0006");
        assertThat(post("/api/app/v1/feedback", token, Map.of("type", "PLAYBACK", "content", "播不了"))
                .path("code").asInt()).isEqualTo(400);
        assertThat(post("/api/app/v1/feedback", token, Map.of("type", "PLAYBACK", "videoId", "10", "lineName", "高清",
                "episodeName", "第1集", "content", "这集播不了")).path("code").asInt()).isZero();
        assertThat(post("/api/app/v1/feedback", token, Map.of("type", "GENERAL", "content", "希望增加倍速"))
                .path("code").asInt()).isZero();
        assertThat(post("/api/app/v1/feedback", token, Map.of("type", "GENERAL", "content", "第三条"))
                .path("code").asInt()).isEqualTo(429);
        assertThat(get("/api/admin/v1/app/feedbacks", token).path("code").asInt()).isEqualTo(401);

        var page = feedbacks.page(1, 20, "PENDING", "PLAYBACK");
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).videoName()).isEqualTo("影片10");
        assertThat(page.records().get(0).memberUsername()).isEqualTo("feedback_user");
        Long id = Long.valueOf(page.records().get(0).id());
        feedbacks.resolve(id, new AppFeedbackHandleRequest("已换线"));
        assertThat(feedbacks.page(1, 20, "RESOLVED", null).records().get(0).handleRemark()).isEqualTo("已换线");
        try {
            feedbacks.resolve(id, null);
            throw new AssertionError("重复处理应当失败");
        } catch (BusinessException expected) {
            assertThat(expected.getMessage()).contains("已处理");
        }
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
     * 构造进度上报入参。
     */
    private static Map<String, Object> progress(int episodeIndex, String episodeName, int position) {
        return Map.of("episodeIndex", episodeIndex, "episodeName", episodeName, "positionSeconds", position,
                "durationSeconds", 2400);
    }

    /**
     * 注册新用户并返回令牌。
     */
    private String newMember(String username, String deviceCode) {
        JsonNode body = post("/api/app/v1/member/auth/register", null, Map.of("username", username,
                "password", "secret1", "deviceCode", deviceCode, "deviceName", "测试手机"));
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("accessToken").asText();
    }

    /**
     * 发送 GET 请求，断言 HTTP 200 并解析响应体。
     */
    private JsonNode get(String path, String token) {
        return json.readTree(http.get().uri(path).headers(headers -> bearer(headers, token)).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
    }

    /**
     * 发送 DELETE 请求，断言 HTTP 200 并解析响应体。
     */
    private JsonNode delete(String path, String token) {
        return json.readTree(http.delete().uri(path).headers(headers -> bearer(headers, token)).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
    }

    /**
     * 发送 JSON POST 请求，断言 HTTP 200 并解析响应体；入参为空时不带请求体。
     */
    private JsonNode post(String path, String token, Map<String, ?> body) {
        var spec = http.post().uri(path).contentType(MediaType.APPLICATION_JSON).headers(headers -> bearer(headers, token));
        var exchange = body == null ? spec.exchange() : spec.body(body).exchange();
        return json.readTree(exchange.expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
    }

    /**
     * 发送 JSON PUT 请求，断言 HTTP 200 并解析响应体；入参为空时不带请求体。
     */
    private JsonNode put(String path, String token, Map<String, ?> body) {
        var spec = http.put().uri(path).contentType(MediaType.APPLICATION_JSON).headers(headers -> bearer(headers, token));
        var exchange = body == null ? spec.exchange() : spec.body(body).exchange();
        return json.readTree(exchange.expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
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
