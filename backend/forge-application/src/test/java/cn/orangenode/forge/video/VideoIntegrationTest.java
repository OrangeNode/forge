package cn.orangenode.forge.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;
import cn.orangenode.forge.video.collector.CollectedEpisode;
import cn.orangenode.forge.video.collector.CollectedLine;
import cn.orangenode.forge.video.collector.CollectedPage;
import cn.orangenode.forge.video.collector.CollectedVideo;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.entity.SettingsEntity;
import cn.orangenode.forge.video.mapper.SettingsMapper;
import cn.orangenode.forge.video.request.CategoryRequest;
import cn.orangenode.forge.video.request.CollectRequest;
import cn.orangenode.forge.video.request.LineEditRequest;
import cn.orangenode.forge.video.request.PendingResolveRequest;
import cn.orangenode.forge.video.request.TaskRequest;
import cn.orangenode.forge.video.request.VideoEditRequest;
import cn.orangenode.forge.video.response.PendingResponse;
import cn.orangenode.forge.video.response.VideoDetailResponse;
import cn.orangenode.forge.video.service.CatalogService;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.service.IngestService;
import cn.orangenode.forge.video.support.VideoValues;

/**
 * 视频采集真实事务、断点和匿名协议验证；网络使用确定性数据，数据库隔离于日常环境。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_video;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false", "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false", "forge.video.worker-enabled=false",
    "forge.video.app-rate-limit=3", "forge.video.app-rate-window-seconds=3600"
})
class VideoIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CatalogService catalog;
    @Autowired
    private CollectionService collections;
    @Autowired
    private IngestService ingest;
    @Autowired
    private SettingsMapper settingsMapper;
    @Autowired
    private VideoProperties properties;
    @MockitoBean
    private StringRedisTemplate redis;
    @LocalServerPort
    private int port;
    @Autowired
    private cn.orangenode.forge.video.service.SourceService sources;
    @Autowired
    private cn.orangenode.forge.video.service.SettingsService settings;
    @Autowired
    private cn.orangenode.forge.video.service.CollectionBatchService batches;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    private Long category;
    private RestTestClient http;

    /**
     * 从实际迁移派生内存表，保留约束和字段，仅移除 MySQL 引擎及索引前缀语法。
     */
    @BeforeEach
    void prepare() throws Exception {
        new RedisTestDouble(redis);
        RuntimeSchema.recreateSystemAndAuditTables(jdbc);
        String sql = new ClassPathResource("db/migration/video/V20260930200000__video_collection_tables.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?m)^--.*$", "")
                .replaceAll("ENGINE=InnoDB[^;]+", "")
                .replace("match_name(128)", "match_name");
        for (String table : List.of("category", "source", "source_category", "video", "origin", "play_line",
                "episode", "settings", "task", "run", "pending")) {
            jdbc.execute("DROP TABLE IF EXISTS video_" + table);
        }
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                jdbc.execute(command);
            }
        }
        jdbc.execute("ALTER TABLE video_settings ADD COLUMN missing_policies TEXT NULL");
        SettingsEntity rules = new SettingsEntity();
        rules.setId(1L);
        rules.setMatchFields("name,year,category");
        rules.setUpdateFields(String.join(",", properties.getDefaultUpdateFields()));
        rules.setAutoPublish(true);
        rules.setCreatedAt(VideoValues.now());
        rules.setUpdatedAt(VideoValues.now());
        settingsMapper.insert(rules);
        category = Long.valueOf(catalog.saveCategory(null, new CategoryRequest(null, "电影", 0, true)).id());
        for (long source : List.of(1L, 2L)) {
            jdbc.update("INSERT INTO video_source (id,name,api_url,source_zone,enabled,deleted,created_at,updated_at)"
                    + " VALUES (?,?,?,'Asia/Shanghai',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    source, "测试来源" + source, "https://example.com/api.php");
            jdbc.update("INSERT INTO video_source_category (source_id,upstream_id,name,category_id,created_at,updated_at)"
                    + " VALUES (?,'6','动作片',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", source, category);
        }
        http = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /**
     * 同站重采与跨站严格匹配保持一部影片，季数和年份不会被抹掉。
     */
    @Test
    void deduplicatesConservatively() {
        collect(1, movie("1", "测试 第一季", 2026, "原始资料"), 1, 1);
        collect(1, movie("1", "测试 第一季", 2026, "更新资料"), 1, 1);
        collect(2, movie("9", "测试 第一季", 2026, "其他来源"), 1, 1);
        assertThat(count("video")).isEqualTo(1);
        assertThat(count("origin")).isEqualTo(2);
        assertThat(count("play_line")).isEqualTo(2);
        assertThat(catalog.detail(videoId()).video().description()).isEqualTo("更新资料");
        collect(2, movie("10", "测试 第一季", 2025, "往年"), 1, 1);
        collect(2, movie("11", "测试 第二季", 2026, "续季"), 1, 1);
        assertThat(count("video")).isEqualTo(3);
    }

    /**
     * 手工锁定、下架和停用线路持续生效；空地址不会清除旧分集。
     */
    @Test
    void respectsManualStateAndPreservesOldUrls() {
        collect(1, movie("1", "测试", 2026, "原始资料"), 1, 1);
        Long id = videoId();
        VideoDetailResponse detail = catalog.detail(id);
        Long line = Long.valueOf(detail.playLines().getFirst().id());
        catalog.edit(id, new VideoEditRequest("人工片名", category.toString(), null, null, null, null, null,
                null, "人工资料", null, null, null, null));
        catalog.publish(id, false);
        catalog.editLine(line, new LineEditRequest("停用线路", 9, false));
        CollectedVideo empty = new CollectedVideo("1", "6", "来源片名", 2026, "中国", "中文", "导演", "演员",
                null, "源站新资料", null, null, null, null, List.of());
        collect(1, empty, 1, 1);
        detail = catalog.detail(id);
        assertThat(detail.video().name()).isEqualTo("人工片名");
        assertThat(detail.video().year()).isNull();
        assertThat(detail.video().description()).isEqualTo("人工资料");
        assertThat(detail.video().published()).isFalse();
        assertThat(detail.playLines().getFirst().enabled()).isFalse();
        assertThat(detail.playLines().getFirst().episodes().getFirst().url())
                .isEqualTo("https://example.com/a.m3u8?token=A%2FB&expires=1");
        assertThat(catalog.appVideos(1, 20, null, null, null, null, null).records()).isEmpty();
        assertThatThrownBy(() -> catalog.appDetail(id)).isInstanceOf(BusinessException.class);
        catalog.deleteVideo(id);
        collect(1, movie("1", "测试", 2026, "不能恢复"), 1, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM video_video WHERE deleted=0", Long.class)).isZero();
    }

    /**
     * 新缺失字段进入待确认，人工绑定后再次采集走稳定来源关联。
     */
    @Test
    void resolvesMissingDataAndKeepsBinding() {
        collect(1, movie("1", "测试", 2026, "主来源"), 1, 1);
        collect(2, movie("9", "测试", null, "缺失年份"), 1, 1);
        PendingResponse pending = collections.pending(1, 20, "PENDING", null).records().getFirst();
        assertThat(pending.incoming().year()).isNull();
        ingest.resolve(Long.valueOf(pending.id()), new PendingResolveRequest("BIND", videoId().toString(), null));
        collect(2, movie("9", "测试", null, "仍缺年份"), 1, 1);
        assertThat(count("video")).isEqualTo(1);
        assertThat(count("origin")).isEqualTo(2);
        assertThat(collections.pending(1, 20, "PENDING", null).records()).isEmpty();
    }

    /**
     * 忽略是持续业务决定，补齐字段和清理运行日志都不会自动恢复。
     */
    @Test
    void ignoreSurvivesCompleteDataAndLogCleanup() {
        Long run = collect(1, movie("ignored", "忽略影片", null, "缺年"), 1, 1);
        PendingResponse pending = collections.pending(1, 20, "PENDING", null).records().getFirst();
        ingest.resolve(Long.valueOf(pending.id()), new PendingResolveRequest("IGNORE", null, null));
        jdbc.update("UPDATE video_run SET updated_at=? WHERE id=?", VideoValues.now().minusDays(90), run);
        collections.cleanup();
        assertThat(count("pending")).isEqualTo(1);
        assertThat(count("run")).isZero();
        collect(1, movie("ignored", "忽略影片", 2026, "已补齐"), 1, 1);
        assertThat(count("video")).isZero();
    }

    /**
     * 未映射分类与多个候选均不得自动合并，支持管理员明确新建。
     */
    @Test
    void queuesUnmappedAndAmbiguousRecords() {
        collect(1, movie("1", "同名", 2026, "原版"), 1, 1);
        collect(2, movie("unmapped", "同名", null, "待创建"), 1, 1);
        PendingResponse pending = collections.pending(1, 20, "PENDING", null).records().getFirst();
        ingest.resolve(Long.valueOf(pending.id()), new PendingResolveRequest("CREATE", null, category.toString()));
        collect(2, movie("new", "同名", 2026, "第三来源编号"), 1, 1);
        assertThat(collections.pending(1, 20, "PENDING", null).records().getFirst().candidates()).hasSize(2);
        jdbc.update("DELETE FROM video_source_category WHERE source_id=2");
        collect(2, movie("missing-class", "其他", 2026, "未映射"), 1, 1);
        assertThat(collections.pending(1, 20, "PENDING", null).records())
                .anyMatch(p -> p.reason().contains("本地分类") && "missing-class".equals(p.upstreamId()));
    }

    /**
     * 整页事务失败回滚影片、分集和统计，旧地址及页面断点均保留。
     */
    @Test
    void rollsBackWholePageOnStorageFailure() {
        collect(1, movie("1", "测试", 2026, "原始"), 1, 1);
        Long run = start(1);
        CollectedVideo valid = movie("1", "测试", 2026, "不该提交");
        CollectedVideo oversized = movie("2", "超长".repeat(200), 2026, "无效资料");
        assertThatThrownBy(() -> ingest.page(run, new CollectedPage(1, 1, List.of(valid, oversized))))
                .isInstanceOf(RuntimeException.class);
        assertThat(catalog.detail(videoId()).video().description()).isEqualTo("原始");
        assertThat(collections.run(run).nextPage()).isEqualTo(1);
        assertThat(collections.run(run).processed()).isZero();
        assertThat(count("video")).isEqualTo(1);
    }

    /**
     * 页面重放幂等；停止、重启恢复及继续保留累计进度且拒绝重叠采集。
     */
    @Test
    void keepsCheckpointsAcrossStopAndRestart() {
        Long run = start(1);
        CollectedPage page = new CollectedPage(1, 2, List.of(movie("1", "测试", 2026, "原始")));
        ingest.page(run, page);
        ingest.page(run, page);
        assertThat(collections.run(run).processed()).isEqualTo(1);
        assertThatThrownBy(() -> collections.collect(new CollectRequest("1", "DAY", null)))
                .isInstanceOf(BusinessException.class);
        collections.stop(run);
        assertThat(collections.run(run).status()).isEqualTo("STOP_REQUESTED");
        collections.recover();
        assertThat(collections.run(run).status()).isEqualTo("INTERRUPTED");
        collections.resume(run);
        assertThat(collections.claim()).isEqualTo(run);
        ingest.page(run, new CollectedPage(2, 2, List.of(movie("2", "另一部", 2026, "资料"))));
        collections.finish(run, "COMPLETED", null);
        assertThat(collections.run(run).processed()).isEqualTo(2);
        assertThat(collections.run(run).nextPage()).isEqualTo(3);
        assertThat(count("video")).isEqualTo(2);
    }

    /**
     * 新任务默认停用，到期补触发只排队一次并给出窗口丢失提示。
     */
    @Test
    void dispatchesOneMissedSchedule() {
        var request = new TaskRequest("日采集", "1", "DAY", "6", "DAILY", null, null, "02:00", "Asia/Shanghai", true);
        var task = collections.saveTask(null, request);
        assertThat(task.enabled()).isFalse();
        collections.saveTask(Long.valueOf(task.id()), request);
        jdbc.update("UPDATE video_task SET next_run_at=? WHERE id=?", VideoValues.now().minusDays(3), Long.valueOf(task.id()));
        collections.dispatchDue();
        collections.dispatchDue();
        assertThat(count("run")).isEqualTo(1);
        var run = collections.runs(1, 20, null).records().getFirst();
        assertThat(run.status()).isEqualTo("QUEUED");
        assertThat(run.warning()).contains("超过采集窗口");
    }

    /**
     * 分类祖先停用后，公开分类、详情和地址都不可读。
     */
    @Test
    void hidesDisabledCategoryAncestors() {
        var parent = catalog.saveCategory(null, new CategoryRequest(null, "上级", 0, true));
        catalog.saveCategory(category, new CategoryRequest(parent.id(), "电影", 0, true));
        collect(1, movie("1", "测试", 2026, "原始"), 1, 1);
        assertThat(catalog.lines(videoId(), true)).hasSize(1);
        catalog.saveCategory(Long.valueOf(parent.id()), new CategoryRequest(null, "上级", 0, false));
        assertThat(catalog.categories(true)).isEmpty();
        assertThatThrownBy(() -> catalog.lines(videoId(), true)).isInstanceOf(BusinessException.class);
    }

    /**
     * 只放行明确的公开 GET，写入、未知路径和管理接口仍须认证，限流使用统一响应。
     */
    @Test
    void limitsAnonymousGetAndProtectsEveryWrite() {
        String base = "/api/app/v1/video";
        String body = http.get().uri(base + "/categories").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body).contains("\"code\":0", "\"traceId\":");
        http.get().uri(base + "/videos").exchange().expectStatus().isOk();
        http.get().uri(base + "/videos").exchange().expectStatus().isOk();
        body = http.get().uri(base + "/videos").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body).contains("\"code\":429");
        for (String path : List.of(base + "/unknown", "/api/admin/v1/video/sources")) {
            body = http.get().uri(path).exchange().expectStatus().isOk()
                    .expectBody(String.class).returnResult().getResponseBody();
            assertThat(body).contains("\"code\":401");
        }
        body = http.post().uri(base + "/videos").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body).contains("\"code\":401");
    }

    /**
     * 允许更新字段独立生效，更换主要来源后才采用该站资料。
     */
    @Test
    void appliesUpdateWhitelistAndPrimarySource() {
        collect(1, movie("1", "测试", 2026, "初始说明"), 1, 1);
        settings.save(new cn.orangenode.forge.video.request.SettingsRequest(List.of("name", "year", "category"),
                List.of("remarks"), true));
        collect(1, movie("1", "来源改名", 2026, "禁止覆盖"), 1, 1);
        assertThat(catalog.detail(videoId()).video().name()).isEqualTo("测试");
        assertThat(catalog.detail(videoId()).video().description()).isEqualTo("初始说明");
        collect(2, movie("9", "测试", 2026, "次要说明"), 1, 1);
        var second = catalog.detail(videoId()).origins().stream().filter(o -> "2".equals(o.sourceId())).findFirst().orElseThrow();
        catalog.primary(videoId(), Long.valueOf(second.id()));
        settings.save(new cn.orangenode.forge.video.request.SettingsRequest(List.of("name", "year", "category"),
                properties.getDefaultUpdateFields(), true));
        collect(2, movie("9", "测试", 2026, "现在是主来源"), 1, 1);
        assertThat(catalog.detail(videoId()).video().description()).isEqualTo("现在是主来源");
    }

    /**
     * 解除映射是实际写操作；删除仍被来源映射引用的分类时自动解绑，不留下悬空引用。
     */
    @Test
    void unmapsCategoriesWithoutDanglingReferences() {
        sources.map(2L, new cn.orangenode.forge.video.request.MappingRequest("6", null));
        assertThat(sources.categories(2L).getFirst().categoryId()).isNull();
        assertThat(sources.categories(1L).getFirst().categoryId()).isEqualTo(category.toString());
        catalog.deleteCategory(category);
        assertThat(sources.categories(1L).getFirst().categoryId()).isNull();
        assertThat(catalog.categories(false)).isEmpty();
    }

    /**
     * 批量删除先删子分类再删父分类，被子分类阻止或不存在的条目逐条返回原因，映射自动解绑。
     */
    @Test
    void batchDeletesCategoriesChildrenFirst() {
        Long parent = Long.valueOf(catalog.saveCategory(null, new CategoryRequest(null, "父级", 0, true)).id());
        Long child = Long.valueOf(catalog.saveCategory(null, new CategoryRequest(parent.toString(), "子级", 0, true)).id());
        Long grandchild = Long.valueOf(catalog.saveCategory(null,
                new CategoryRequest(child.toString(), "孙级", 0, true)).id());
        Long blocked = Long.valueOf(catalog.saveCategory(null, new CategoryRequest(null, "保留", 0, true)).id());
        catalog.saveCategory(null, new CategoryRequest(blocked.toString(), "保留子级", 0, true));
        var result = catalog.deleteCategories(List.of(parent, blocked, grandchild, category, child, parent, 999999L));
        assertThat(result.total()).isEqualTo(6);
        assertThat(result.succeeded()).isEqualTo(4);
        assertThat(result.failures()).extracting(f -> f.id())
                .containsExactlyInAnyOrder(blocked.toString(), "999999");
        assertThat(result.failures()).filteredOn(f -> f.id().equals(blocked.toString()))
                .extracting(f -> f.message()).containsExactly("分类下还有子分类，请先删除或移动子分类");
        assertThat(catalog.categories(false)).extracting(c -> c.name()).containsExactly("保留");
        assertThat(sources.categories(1L).getFirst().categoryId()).isNull();
        String body = http.post().uri("/api/admin/v1/video/categories/batch-delete")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("{\"ids\":[\"" + blocked + "\"]}").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body).contains("\"code\":401");
    }

    /**
     * 批量删除有影片的分类时只跳过该分类，影片与分类均保留。
     */
    @Test
    void batchDeleteKeepsCategoriesWithVideos() {
        collect(1, movie("1", "占用分类", 2026, "资料"), 1, 1);
        Long empty = Long.valueOf(catalog.saveCategory(null, new CategoryRequest(null, "空分类", 0, true)).id());
        var result = catalog.deleteCategories(List.of(category, empty));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failures()).extracting(f -> f.message()).containsExactly("分类下还有影片，请先移动或删除影片");
        assertThat(count("video")).isEqualTo(1);
        assertThat(catalog.categories(false)).extracting(c -> c.id()).containsExactly(category.toString());
    }

    /**
     * 存疑记录补齐后仍须人工确认，不能绕过管理员的历史决定。
     */
    @Test
    void keepsPreviouslyPendingDataUnderReview() {
        collect(1, movie("1", "待确认", null, "缺少资料"), 1, 1);
        collect(1, movie("1", "待确认", 2026, "最新资料"), 1, 1);
        assertThat(count("video")).isZero();
        PendingResponse pending = collections.pending(1, 20, "PENDING", null).records().getFirst();
        assertThat(pending.incoming().year()).isEqualTo(2026);
        ingest.resolve(Long.valueOf(pending.id()), new PendingResolveRequest("CREATE", null, category.toString()));
        collect(1, movie("1", "待确认", 2026, "再次采集"), 1, 1);
        assertThat(count("video")).isEqualTo(1);
        assertThat(count("origin")).isEqualTo(1);
    }

    /**
     * 人工事务先持有影片锁时，采集等待提交后重新读取锁定状态，不能覆盖人工资料。
     */
    @Test
    void serializesManualEditsAndCollection() throws Exception {
        collect(1, movie("1", "测试", 2026, "原始资料"), 1, 1);
        Long id = videoId();
        Long run = start(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> processing =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM video_video WHERE id=? FOR UPDATE", Long.class, id);
                catalog.edit(id, new VideoEditRequest("人工资料", category.toString(), 2026, null, null, null, null,
                        null, "锁定说明", null, null, null, true));
                processing.set(executor.submit(() -> {
                    started.countDown();
                    ingest.page(run, new CollectedPage(1, 1, List.of(movie("1", "源站改名", 2026, "禁止覆盖"))));
                }));
                try {
                    assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                assertThatThrownBy(() -> processing.get().get(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            });
            processing.get().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(catalog.detail(id).video().name()).isEqualTo("人工资料");
            assertThat(catalog.detail(id).video().description()).isEqualTo("锁定说明");
            assertThat(catalog.detail(id).video().metadataLocked()).isTrue();
            assertThat(collections.run(run).processed()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 创建确定性的采集数据，签名和编码保持原样。
     */
    private CollectedVideo movie(String id, String name, Integer year, String description) {
        return new CollectedVideo(id, "6", name, year, "中国", "中文", "导演", "演员", null,
                description, "更新说明", 1, null, null, List.of(new CollectedLine("m3u8",
                List.of(new CollectedEpisode("第1集", "https://example.com/a.m3u8?token=A%2FB&expires=1", "M3U8")))));
    }

    /**
     * 持久化排队后由服务领取，测试直接提交页以隔离外部网络波动。
     */
    private Long start(long source) {
        Long id = Long.valueOf(collections.collect(new CollectRequest(Long.toString(source), "DAY", null)).id());
        assertThat(collections.claim()).isEqualTo(id);
        return id;
    }

    /**
     * 单页采集闭环，返回运行编号供后续状态验证。
     */
    private Long collect(long source, CollectedVideo movie, int page, int count) {
        Long id = start(source);
        ingest.page(id, new CollectedPage(page, count, List.of(movie)));
        collections.finish(id, "COMPLETED", null);
        return id;
    }

    /**
     * 一键创建父子分类并复用已有分类，重复执行不产生新分类或覆盖人工绑定。
     */
    @Test
    void autoCreatesAndBindsCategoriesIdempotently() {
        for (long source : List.of(1L, 2L)) {
            jdbc.update("INSERT INTO video_source_category (source_id,upstream_id,upstream_parent_id,name,created_at,updated_at)"
                    + " VALUES (?,'11','10','国产剧',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", source);
            jdbc.update("INSERT INTO video_source_category (source_id,upstream_id,name,created_at,updated_at)"
                    + " VALUES (?,'10','电视剧',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", source);
        }
        var first = sources.autoMap(1L, true);
        assertThat(first.created()).isEqualTo(2);
        assertThat(first.bound()).isEqualTo(2);
        assertThat(first.preserved()).isEqualTo(1);
        assertThat(first.unresolved()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT parent_id FROM video_category WHERE name='国产剧'", Long.class))
                .isEqualTo(jdbc.queryForObject("SELECT id FROM video_category WHERE name='电视剧'", Long.class));
        assertThat(sources.autoMap(1L, true).created()).isZero();
        assertThat(sources.autoMap(2L, false).bound()).isEqualTo(2);
        assertThat(count("category")).isEqualTo(3);
        assertThat(catalog.categories(false)).hasSize(2);
        assertThat(catalog.categories(false).stream().filter(c -> "电视剧".equals(c.name()))
                .findFirst().orElseThrow().children()).extracting(c -> c.name()).containsExactly("国产剧");
        assertThat(sources.categories(1L).getFirst().categoryId()).isEqualTo(category.toString());
    }

    /**
     * 常见后缀可识别，歧义与停用分类不会被误绑或重复创建。
     */
    @Test
    void leavesAmbiguousAndDisabledCategoriesForConfirmation() {
        var action = catalog.saveCategory(null, new CategoryRequest(null, "动作", 0, true));
        catalog.saveCategory(null, new CategoryRequest(null, "重复", 0, true));
        catalog.saveCategory(null, new CategoryRequest(null, "重复", 0, true));
        catalog.saveCategory(null, new CategoryRequest(null, "停用分类", 0, false));
        for (String name : List.of("动作片", "重复", "停用分类")) {
            jdbc.update("INSERT INTO video_source_category (source_id,upstream_id,name,created_at,updated_at)"
                    + " VALUES (1,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", name, name);
        }
        var result = sources.autoMap(1L, true);
        assertThat(result.bound()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.unresolved()).containsExactlyInAnyOrder("重复", "停用分类");
        assertThat(sources.categories(1L).stream().filter(c -> "动作片".equals(c.upstreamId())).findFirst().orElseThrow().categoryId())
                .isEqualTo(action.id());
    }

    /**
     * 允许缺失年份时独立创建影片，不用不完整条件自动合并其他来源。
     */
    @Test
    void allowsIncompleteInformationWithoutUnsafeMerging() {
        collect(1, movie("1", "同名影片", 2026, "原始"), 1, 1);
        settings.save(new cn.orangenode.forge.video.request.SettingsRequest(List.of("name", "year", "category"),
                properties.getDefaultUpdateFields(), true, java.util.Map.of("year", "ALLOW")));
        collect(2, movie("2", "同名影片", null, "未知年份"), 1, 1);
        assertThat(count("video")).isEqualTo(2);
        assertThat(count("pending")).isZero();
        collect(2, movie("2", "同名影片", 2026, "后续补齐"), 1, 1);
        assertThat(count("video")).isEqualTo(2);
    }

    /**
     * 缺失策略按运行快照生效，多字段缺失时跳过优先，必需信息不允许为空。
     */
    @Test
    void snapshotsMissingPoliciesAndValidatesRequiredInformation() {
        settings.save(new cn.orangenode.forge.video.request.SettingsRequest(List.of("name", "year", "category"),
                properties.getDefaultUpdateFields(), true, java.util.Map.of("posterUrl", "SKIP", "year", "PENDING")));
        Long run = start(1);
        settings.save(new cn.orangenode.forge.video.request.SettingsRequest(List.of("name", "year", "category"),
                properties.getDefaultUpdateFields(), true, java.util.Map.of("posterUrl", "ALLOW", "year", "ALLOW")));
        ingest.page(run, new CollectedPage(1, 1, List.of(movie("1", "缺资料", null, null))));
        collections.finish(run, "COMPLETED", null);
        assertThat(collections.run(run).skipped()).isEqualTo(1);
        assertThat(count("video")).isZero();
        assertThat(count("pending")).isZero();
        assertThatThrownBy(() -> settings.save(new cn.orangenode.forge.video.request.SettingsRequest(
                List.of("name"), List.of(), true, java.util.Map.of("playUrls", "ALLOW"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> settings.save(new cn.orangenode.forge.video.request.SettingsRequest(
                List.of("name"), List.of(), true, java.util.Map.of("year", "INVALID"))))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 无名称和播放地址按具体策略进入待确认或跳过，不因单条缺失导致整页失败。
     */
    @Test
    void appliesPoliciesToMissingNameAndPlayback() {
        CollectedVideo noName = movie("1", null, 2026, "资料");
        collect(1, noName, 1, 1);
        assertThat(collections.pending(1, 20, "PENDING", null).records().getFirst().reason()).contains("名称");
        settings.save(new cn.orangenode.forge.video.request.SettingsRequest(List.of("name", "year", "category"),
                properties.getDefaultUpdateFields(), true, java.util.Map.of("name", "SKIP", "playUrls", "SKIP")));
        Long skipped = collect(1, noName, 1, 1);
        assertThat(collections.run(skipped).skipped()).isEqualTo(1);
        CollectedVideo empty = new CollectedVideo("2", "6", "无地址", 2026, null, null, null, null,
                null, null, null, null, null, null, List.of());
        skipped = collect(1, empty, 1, 1);
        assertThat(collections.run(skipped).skipped()).isEqualTo(1);
        assertThat(count("video")).isZero();
    }

    /**
     * 全部操作覆盖所有分页，最新规则可批量采集旧的待确认快照。
     */
    @Test
    void collectsAllPendingRecordsBeyondCurrentPage() {
        Long run = start(1);
        List<CollectedVideo> incoming = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> movie(Integer.toString(i), "影片" + i, null, "资料")).toList();
        ingest.page(run, new CollectedPage(1, 1, incoming));
        collections.finish(run, "COMPLETED", null);
        assertThat(collections.pending(1, 20, "PENDING", null).records()).hasSize(20);
        settings.save(new cn.orangenode.forge.video.request.SettingsRequest(List.of("name", "year", "category"),
                properties.getDefaultUpdateFields(), true, java.util.Map.of("year", "ALLOW")));
        var result = batches.pending(new cn.orangenode.forge.video.request.CollectionBatchRequest(
                "ALL", null, "PENDING", null, "COLLECT"));
        assertThat(result.total()).isEqualTo(25);
        assertThat(result.succeeded()).isEqualTo(25);
        assertThat(result.failures()).isEmpty();
        assertThat(count("video")).isEqualTo(25);
        assertThat(collections.pending(1, 20, "PENDING", null).total()).isZero();
    }

    /**
     * 部分记录的失败独立返回，批量删除隐藏记录并保留重采忽略身份。
     */
    @Test
    void reportsBatchFailuresAndPreservesDeletedIdentity() {
        collect(1, movie("1", "缺年份", null, "资料"), 1, 1);
        String pendingId = collections.pending(1, 20, "PENDING", null).records().getFirst().id();
        var result = batches.pending(new cn.orangenode.forge.video.request.CollectionBatchRequest(
                "SELECTED", List.of(pendingId, "999999"), null, null, "COLLECT"));
        assertThat(result.succeeded()).isZero();
        assertThat(result.failures()).hasSize(2);
        result = batches.pending(new cn.orangenode.forge.video.request.CollectionBatchRequest(
                "SELECTED", List.of(pendingId), null, null, "DELETE"));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(collections.pending(1, 20, null, null).total()).isZero();
        collect(1, movie("1", "缺年份", 2026, "资料补齐"), 1, 1);
        assertThat(count("video")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM video_pending WHERE id=?", String.class, Long.valueOf(pendingId)))
                .isEqualTo("DELETED");
        assertThatThrownBy(() -> batches.runs(new cn.orangenode.forge.video.request.CollectionBatchRequest(
                "SELECTED", List.of(), null, null, "DELETE"))).isInstanceOf(BusinessException.class);
    }

    /**
     * 删除历史只解除待确认的运行引用，活动运行拒删且其他成功项仍提交。
     */
    @Test
    void deletesFinishedRunsAndProtectsActiveRuns() {
        Long finished = collect(1, movie("1", "待确认", null, "资料"), 1, 1);
        var active = collections.collect(new CollectRequest("2", "DAY", null));
        var result = batches.runs(new cn.orangenode.forge.video.request.CollectionBatchRequest(
                "SELECTED", List.of(finished.toString(), active.id()), null, null, "DELETE"));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(count("run")).isEqualTo(1);
        assertThat(collections.pending(1, 20, "PENDING", null).records().getFirst().runId()).isNull();
        result = batches.runs(new cn.orangenode.forge.video.request.CollectionBatchRequest(
                "ALL", null, "QUEUED", null, "STOP"));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(collections.run(Long.valueOf(active.id())).status()).isEqualTo("STOPPED");
    }

    /**
     * 重新采集创建新运行并使用最新规则，同站重叠逐条报错而非重复排队。
     */
    @Test
    void requeuesFinishedRunsWithoutOverlappingSource() {
        Long first = collect(1, movie("1", "影片", 2026, "资料"), 1, 1);
        Long second = collect(1, movie("1", "影片", 2026, "更新"), 1, 1);
        var result = batches.runs(new cn.orangenode.forge.video.request.CollectionBatchRequest(
                "SELECTED", List.of(first.toString(), second.toString()), null, null, "COLLECT"));
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(collections.runs(1, 20, "QUEUED").total()).isEqualTo(1);
        assertThat(collections.run(first).status()).isEqualTo("COMPLETED");
    }

    /**
     * 读取首部影片编号，避免假定自增序号。
     */
    private Long videoId() {
        return jdbc.queryForObject("SELECT MIN(id) FROM video_video WHERE deleted=0", Long.class);
    }

    /**
     * 查询夹具表记录数，表名仅来自测试中的固定参数。
     */
    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM video_" + table, Long.class);
    }
}
