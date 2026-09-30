package cn.orangenode.forge.m4;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriUtils;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.m3.M3FixtureService;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

/**
 * 本地存储全链路端到端验证。
 *
 * <p>覆盖方案创建与默认切换、中文文件名上传、列表、按 ID 下载与删除，以及相对目录越界、
 * 扩展名白名单、方案大小上限、配置版本化与引用保护、元数据写入失败的补偿清理、
 * 只授予部分权限时的下载拒绝。所有接口都断言 HTTP 200 与 {@code body.code}。</p>
 *
 * <p>本地存储根目录指向 JUnit 的临时目录，仓库内不留下任何上传对象。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m4_file_local;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false",
    "forge.file.encryption-key=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
})
class FileLocalStorageIntegrationTest {

    /**
     * 本地存储根目录，由 JUnit 在临时目录下创建并自动清理。
     */
    @TempDir
    static Path storageRoot;

    /**
     * 内存版 Redis 替身使用的字符串模板。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 随机分配的测试端口。
     */
    @LocalServerPort
    private int port;

    /**
     * 建表与断言数据库内容使用的 JDBC 模板。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试数据夹具。
     */
    @Autowired
    private M3FixtureService fixture;

    /**
     * 认证与权限配置，提供超级管理员角色代码。
     */
    @Autowired
    private ForgeSecurityProperties securityProperties;

    /**
     * 按端口构建的测试客户端。
     */
    private RestTestClient client;

    /**
     * 内存版 Redis 替身。
     */
    private RedisTestDouble redisDouble;

    /**
     * 测试管理员的访问令牌。
     */
    private String token;

    /**
     * 把本地存储根目录与临时目录指向 JUnit 提供的临时目录。
     *
     * @param registry 动态属性注册表
     */
    @DynamicPropertySource
    static void registerFileDirectories(DynamicPropertyRegistry registry) {
        registry.add("forge.file.local-root", () -> storageRoot.toString());
        registry.add("forge.file.temp-dir", () -> storageRoot.resolve("tmp").toString());
    }

    /**
     * 每个用例前重建表、清空 Redis 替身，并以超级管理员身份登录。
     */
    @BeforeEach
    void prepareData() {
        client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        RuntimeSchema.recreateSystemAndAuditTables(jdbcTemplate);
        RuntimeSchema.recreateFileTables(jdbcTemplate);
        redisDouble = new RedisTestDouble(stringRedisTemplate);
        redisDouble.clear();
        M4TestSupport.seedPermissions(fixture, M4TestSupport.filePermissionCodes());
        M4TestSupport.createSuperAdmin(fixture, securityProperties.getSuperRoleCode());
        token = M4TestSupport.requireAccessToken(
                M4TestSupport.login(client, M4TestSupport.ADMIN_USERNAME, M4TestSupport.ADMIN_PASSWORD));
    }

    /**
     * 验证创建本地方案、切换默认、上传中文文件名、列表、下载与删除的完整链路。
     */
    @Test
    @DisplayName("本地存储上传后可列表、按 ID 下载并能删除")
    void shouldUploadListDownloadAndDeleteLocalFile() {
        String fileName = "测试文档.txt";
        byte[] content = "中文文件内容-forge-m4".getBytes(StandardCharsets.UTF_8);

        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH,
                M4TestSupport.localConfigBody("local-files", "本地文件方案", "files", 1048576L, "txt"), token);
        assertThat(created.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(created.body())).isZero();
        assertThat(created.body()).contains("\"version\":1");
        String configId = M4TestSupport.firstIdOf(created.body());

        M4TestSupport.ResponseSnapshot switched = M4TestSupport.exchange(client, HttpMethod.PUT,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + configId + "/default", null, token);
        assertThat(M4TestSupport.codeOf(switched.body())).isZero();

        M4TestSupport.ResponseSnapshot uploaded = M4TestSupport.upload(port, token, fileName, "text/plain", content);
        assertThat(uploaded.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(uploaded.body())).isZero();
        assertThat(uploaded.body()).contains("\"originalName\":\"" + fileName + "\"");
        String fileId = M4TestSupport.firstIdOf(uploaded.body());

        M4TestSupport.ResponseSnapshot listed = M4TestSupport.get(client, M4TestSupport.FILES_PATH, token);
        assertThat(M4TestSupport.codeOf(listed.body())).isZero();
        assertThat(M4TestSupport.totalOf(listed.body())).isEqualTo(1L);
        assertThat(listed.body()).contains("\"originalName\":\"" + fileName + "\"");
        assertThat(listed.body()).contains("\"storageConfigId\":\"" + configId + "\"");

        M4TestSupport.ResponseSnapshot downloaded = M4TestSupport.get(client,
                M4TestSupport.FILES_PATH + "/" + fileId + "/download", token);
        assertThat(downloaded.status()).isEqualTo(HttpStatus.OK);
        assertThat(downloaded.headers().getFirst(HttpHeaders.CONTENT_TYPE)).isNotNull().startsWith("text/plain");
        assertThat(downloaded.binary()).isEqualTo(content);
        String disposition = downloaded.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).as("下载响应必须声明附件的安全文件名").isNotNull();
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains("filename*=UTF-8''" + UriUtils.encode(fileName, StandardCharsets.UTF_8));

        M4TestSupport.ResponseSnapshot deleted = M4TestSupport.exchange(client, HttpMethod.DELETE,
                M4TestSupport.FILES_PATH + "/" + fileId, null, token);
        assertThat(M4TestSupport.codeOf(deleted.body())).isZero();

        M4TestSupport.ResponseSnapshot afterDelete = M4TestSupport.get(client, M4TestSupport.FILES_PATH, token);
        assertThat(M4TestSupport.codeOf(afterDelete.body())).isZero();
        assertThat(M4TestSupport.totalOf(afterDelete.body())).isZero();
        assertThat(afterDelete.body()).doesNotContain(fileName);
        assertThat(M4TestSupport.regularFiles(storageRoot.resolve("files")))
                .as("删除后本地不应残留对象文件").isEmpty();
    }

    /**
     * 验证相对目录越界（上级目录与盘符绝对路径）都被参数错误拒绝，且不写入任何配置。
     */
    @Test
    @DisplayName("越界相对目录被拒绝为 400")
    void shouldRejectRelativeDirectoryOutsideRoot() {
        M4TestSupport.ResponseSnapshot parentEscape = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH,
                M4TestSupport.localConfigBody("escape-parent", "上级目录方案", "../outside", 1048576L, null), token);
        assertThat(parentEscape.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(parentEscape.body())).isEqualTo(400);

        M4TestSupport.ResponseSnapshot absoluteEscape = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH,
                M4TestSupport.localConfigBody("escape-absolute", "绝对路径方案", "C:\\temp", 1048576L, null), token);
        assertThat(absoluteEscape.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(absoluteEscape.body())).isEqualTo(400);

        Integer rows = jdbcTemplate.queryForObject("select count(*) from file_storage_config", Integer.class);
        assertThat(rows).isZero();
    }

    /**
     * 验证扩展名不在白名单与超过方案大小上限的上传都被拒绝，且不写入记录、不落地对象。
     */
    @Test
    @DisplayName("扩展名与大小超限的上传被拒绝且不残留对象")
    void shouldRejectDisallowedExtensionAndOversizeUpload() {
        createLocalConfig("restricted-plan", "restricted", 16L, "txt");

        M4TestSupport.ResponseSnapshot executable = M4TestSupport.upload(port, token, "恶意程序.exe",
                "application/octet-stream", "MZ".getBytes(StandardCharsets.UTF_8));
        assertThat(executable.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(executable.body())).isEqualTo(400);

        M4TestSupport.ResponseSnapshot oversize = M4TestSupport.upload(port, token, "大文件.txt", "text/plain",
                new byte[64]);
        assertThat(oversize.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(oversize.body())).isEqualTo(413);

        M4TestSupport.ResponseSnapshot listed = M4TestSupport.get(client, M4TestSupport.FILES_PATH, token);
        assertThat(M4TestSupport.totalOf(listed.body())).isZero();
        assertThat(M4TestSupport.regularFiles(storageRoot.resolve("restricted")))
                .as("校验失败的上传不应写出对象").isEmpty();
    }

    /**
     * 验证同代码再次创建得到新版本、被引用版本不可修改或删除、切换默认只影响新上传。
     */
    @Test
    @DisplayName("配置版本化、引用保护与默认切换只影响新上传")
    void shouldVersionConfigProtectReferenceAndSwitchDefaultForNewUploadsOnly() {
        String firstConfigId = createLocalConfig("documents", "v1", 1048576L, "txt");
        M4TestSupport.ResponseSnapshot second = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH,
                M4TestSupport.localConfigBody("documents", "文档方案", "v2", 1048576L, "txt"), token);
        assertThat(M4TestSupport.codeOf(second.body())).isZero();
        assertThat(second.body()).contains("\"version\":2");
        String secondConfigId = M4TestSupport.firstIdOf(second.body());
        assertThat(secondConfigId).isNotEqualTo(firstConfigId);

        assertThat(M4TestSupport.codeOf(M4TestSupport.exchange(client, HttpMethod.PUT,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + firstConfigId + "/default", null, token).body())).isZero();

        String historicalFileId = uploadTextFile("历史文件.txt", "历史内容");
        assertThat(storageConfigIdOf(historicalFileId)).isEqualTo(Long.valueOf(firstConfigId));

        M4TestSupport.ResponseSnapshot updated = M4TestSupport.exchange(client, HttpMethod.PUT,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + firstConfigId,
                M4TestSupport.localUpdateBody("改名方案", "v1-renamed", 1048576L), token);
        assertThat(updated.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(updated.body())).isEqualTo(409);

        M4TestSupport.ResponseSnapshot deleted = M4TestSupport.exchange(client, HttpMethod.DELETE,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + firstConfigId, null, token);
        assertThat(M4TestSupport.codeOf(deleted.body())).isEqualTo(409);

        assertThat(M4TestSupport.codeOf(M4TestSupport.exchange(client, HttpMethod.PUT,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + secondConfigId + "/default", null, token).body())).isZero();

        M4TestSupport.ResponseSnapshot deleteAfterSwitch = M4TestSupport.exchange(client, HttpMethod.DELETE,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + firstConfigId, null, token);
        assertThat(M4TestSupport.codeOf(deleteAfterSwitch.body())).isEqualTo(409);

        String newFileId = uploadTextFile("新文件.txt", "新内容");
        assertThat(storageConfigIdOf(newFileId)).isEqualTo(Long.valueOf(secondConfigId));

        M4TestSupport.ResponseSnapshot historicalDownload = M4TestSupport.get(client,
                M4TestSupport.FILES_PATH + "/" + historicalFileId + "/download", token);
        assertThat(historicalDownload.binary())
                .as("历史文件继续按记录中固定的配置版本读取").isEqualTo("历史内容".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验证元数据写入失败时上传返回 500，且刚写入的对象被补偿删除、存储目录不残留文件。
     */
    @Test
    @DisplayName("元数据写入失败时补偿删除对象")
    void shouldCompensateStoredObjectWhenMetadataInsertFails() {
        M4TestSupport.deleteRegularFiles(storageRoot);
        createLocalConfig("compensation-plan", "compensation", 4096L, "txt");
        jdbcTemplate.execute("drop table file_record");

        M4TestSupport.ResponseSnapshot uploaded = M4TestSupport.upload(port, token, "补偿.txt", "text/plain",
                "compensate-content".getBytes(StandardCharsets.UTF_8));

        assertThat(uploaded.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(uploaded.body())).isEqualTo(500);
        assertThat(uploaded.body()).doesNotContain("compensate-content");
        assertThat(M4TestSupport.regularFiles(storageRoot)).as("失败补偿后不应残留对象文件").isEmpty();
    }

    /**
     * 验证只授予 {@code file:record:view} 的管理员可以列表但下载被拒绝为 403。
     */
    @Test
    @DisplayName("缺少下载权限的下载请求返回 403")
    void shouldRejectDownloadWithoutDownloadPermission() {
        createLocalConfig("shared-plan", "shared", 1048576L, "txt");
        String fileId = uploadTextFile("共享文件.txt", "共享内容");

        M4TestSupport.createLimitedAdmin(fixture, M4TestSupport.LIMITED_ROLE_CODE,
                M4TestSupport.LIMITED_USERNAME, M4TestSupport.PERM_RECORD_VIEW);
        String limitedToken = M4TestSupport.requireAccessToken(M4TestSupport.login(client,
                M4TestSupport.LIMITED_USERNAME, M4TestSupport.LIMITED_PASSWORD));

        M4TestSupport.ResponseSnapshot listed = M4TestSupport.get(client, M4TestSupport.FILES_PATH, limitedToken);
        assertThat(M4TestSupport.codeOf(listed.body())).isZero();

        M4TestSupport.ResponseSnapshot denied = M4TestSupport.get(client,
                M4TestSupport.FILES_PATH + "/" + fileId + "/download", limitedToken);
        assertThat(denied.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(denied.body())).isEqualTo(403);
    }

    /**
     * 创建一个本地存储方案并切换为默认方案。
     *
     * @param code        方案代码
     * @param baseDir     相对目录
     * @param maxFileSize 单文件大小上限（字节）
     * @param extensions  允许的扩展名
     * @return 新建配置版本的 ID
     */
    private String createLocalConfig(String code, String baseDir, long maxFileSize, String extensions) {
        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH,
                M4TestSupport.localConfigBody(code, "M4 方案 " + code, baseDir, maxFileSize, extensions), token);
        assertThat(M4TestSupport.codeOf(created.body())).as("方案 %s 应创建成功", code).isZero();
        String configId = M4TestSupport.firstIdOf(created.body());
        M4TestSupport.ResponseSnapshot switched = M4TestSupport.exchange(client, HttpMethod.PUT,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + configId + "/default", null, token);
        assertThat(M4TestSupport.codeOf(switched.body())).isZero();
        return configId;
    }

    /**
     * 以当前默认方案上传一个文本文件。
     *
     * @param fileName 原始文件名
     * @param content  文件内容
     * @return 文件 ID
     */
    private String uploadTextFile(String fileName, String content) {
        M4TestSupport.ResponseSnapshot uploaded = M4TestSupport.upload(port, token, fileName, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        assertThat(M4TestSupport.codeOf(uploaded.body())).as("上传 %s 应成功", fileName).isZero();
        return M4TestSupport.firstIdOf(uploaded.body());
    }

    /**
     * 查询文件记录固定的存储配置版本 ID。
     *
     * @param fileId 文件 ID
     * @return 存储配置版本 ID
     */
    private Long storageConfigIdOf(String fileId) {
        return jdbcTemplate.queryForObject("select storage_config_id from file_record where id = ?", Long.class,
                Long.valueOf(fileId));
    }
}
