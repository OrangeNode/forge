package cn.orangenode.forge.m4;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import cn.orangenode.forge.m3.M3FixtureService;

/**
 * M4 文件与审计端到端验证共用的测试约定与请求封装。
 *
 * <p>账号口令、权限码、接口路径与 JSON 拼装集中在此处：权限种子由生产夹具写入账号相关表，
 * 再通过超级管理员角色让测试管理员获得全部有效权限，因此用例走的是真实认证与授权路径，
 * 不是绕过安全层的直连调用。</p>
 *
 * <p>普通 JSON 请求使用 {@link RestTestClient}；multipart 上传由 JDK 的 {@link HttpClient}
 * 手工拼装请求体，因为测试客户端不提供 multipart 支持。响应统一按 UTF-8 解码，
 * 断言同时覆盖 HTTP 状态与 {@code body.code}。</p>
 */
public final class M4TestSupport {

    /**
     * 测试管理员用户名，超级管理员角色持有者。
     */
    public static final String ADMIN_USERNAME = "m4-admin";

    /**
     * 测试管理员密码，只是测试固定值，不是任何环境的真实口令。
     */
    public static final String ADMIN_PASSWORD = "m4-test-pass";

    /**
     * 只被授予部分权限的角色代码。
     */
    public static final String LIMITED_ROLE_CODE = "m4_viewer";

    /**
     * 部分权限管理员的用户名。
     */
    public static final String LIMITED_USERNAME = "m4-limited";

    /**
     * 部分权限管理员的密码。
     */
    public static final String LIMITED_PASSWORD = "m4-test-pass";

    /**
     * 初始即为停用状态的管理员用户名，用于验证停用路径的登录日志。
     */
    public static final String BLOCKED_USERNAME = "m4-blocked";

    /**
     * 停用账号的密码。
     */
    public static final String BLOCKED_PASSWORD = "m4-test-pass";

    /**
     * 存储配置管理接口路径。
     */
    public static final String STORAGE_CONFIGS_PATH = "/api/admin/v1/storage-configs";

    /**
     * 文件管理接口路径。
     */
    public static final String FILES_PATH = "/api/admin/v1/files";

    /**
     * 登录日志查询接口路径。
     */
    public static final String LOGIN_LOGS_PATH = "/api/admin/v1/audit/login-logs";

    /**
     * 操作日志查询接口路径。
     */
    public static final String OPERATION_LOGS_PATH = "/api/admin/v1/audit/operation-logs";

    /**
     * 权限管理接口路径。
     */
    public static final String PERMISSIONS_PATH = "/api/admin/v1/system/permissions";

    /**
     * 查询存储配置权限码。
     */
    public static final String PERM_STORAGE_VIEW = "file:storage:view";

    /**
     * 新增存储配置权限码。
     */
    public static final String PERM_STORAGE_CREATE = "file:storage:create";

    /**
     * 修改存储配置权限码。
     */
    public static final String PERM_STORAGE_UPDATE = "file:storage:update";

    /**
     * 删除存储配置权限码。
     */
    public static final String PERM_STORAGE_DELETE = "file:storage:delete";

    /**
     * 存储连接检测权限码。
     */
    public static final String PERM_STORAGE_TEST = "file:storage:test";

    /**
     * 切换默认存储方案权限码。
     */
    public static final String PERM_STORAGE_DEFAULT = "file:storage:default";

    /**
     * 查询文件权限码。
     */
    public static final String PERM_RECORD_VIEW = "file:record:view";

    /**
     * 上传文件权限码。
     */
    public static final String PERM_RECORD_UPLOAD = "file:record:upload";

    /**
     * 下载文件权限码。
     */
    public static final String PERM_RECORD_DOWNLOAD = "file:record:download";

    /**
     * 删除文件权限码。
     */
    public static final String PERM_RECORD_DELETE = "file:record:delete";

    /**
     * 新增权限的权限码，用于验证写接口的操作审计。
     */
    public static final String PERM_PERMISSION_CREATE = "system:permission:create";

    /**
     * 查询登录日志权限码。
     */
    public static final String PERM_AUDIT_LOGIN_VIEW = "audit:login:view";

    /**
     * 查询操作日志权限码。
     */
    public static final String PERM_AUDIT_OPERATION_VIEW = "audit:operation:view";

    /**
     * 从响应体中提取 {@code body.code} 的正则。
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\":(\\d+)");

    /**
     * 从分页响应体中提取 {@code data.total} 的正则。
     */
    private static final Pattern TOTAL_PATTERN = Pattern.compile("\"total\":(\\d+)");

    /**
     * 从响应体中提取第一个对外 ID 的正则。
     */
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\":\"(\\d+)\"");

    /**
     * 从登录响应中提取访问令牌的正则。
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    /**
     * 工具类不允许实例化。
     */
    private M4TestSupport() {
    }

    /**
     * 返回文件模块用例需要的全部权限码。
     *
     * @return 存储配置与文件记录权限码列表
     */
    public static List<String> filePermissionCodes() {
        return List.of(PERM_STORAGE_VIEW, PERM_STORAGE_CREATE, PERM_STORAGE_UPDATE, PERM_STORAGE_DELETE,
                PERM_STORAGE_TEST, PERM_STORAGE_DEFAULT, PERM_RECORD_VIEW, PERM_RECORD_UPLOAD, PERM_RECORD_DOWNLOAD,
                PERM_RECORD_DELETE);
    }

    /**
     * 返回审计用例额外需要的权限码。
     *
     * @return 审计查询与权限新增权限码列表
     */
    public static List<String> auditPermissionCodes() {
        return List.of(PERM_AUDIT_LOGIN_VIEW, PERM_AUDIT_OPERATION_VIEW, PERM_PERMISSION_CREATE);
    }

    /**
     * 写入权限种子数据。
     *
     * <p>超级管理员的权限集合由全部有效权限行推导，因此用例必须先写入权限行，
     * 否则即使是超级管理员也没有任何 {@code @PreAuthorize} 可用的权限码。</p>
     *
     * @param fixture 数据夹具
     * @param codes   需要写入的权限代码
     */
    public static void seedPermissions(M3FixtureService fixture, List<String> codes) {
        for (String code : codes) {
            fixture.createPermission(code, "M4 用例权限 " + code);
        }
    }

    /**
     * 创建超级管理员角色并授予测试管理员。
     *
     * @param fixture       数据夹具
     * @param superRoleCode 超级管理员角色代码，取自 {@code forge.security.super-role-code}
     * @return 测试管理员 ID
     */
    public static Long createSuperAdmin(M3FixtureService fixture, String superRoleCode) {
        Long roleId = fixture.createRole(superRoleCode, "超级管理员");
        Long adminId = fixture.createAdmin(ADMIN_USERNAME, ADMIN_PASSWORD, "enabled");
        fixture.grantRole(adminId, roleId);
        return adminId;
    }

    /**
     * 创建只拥有指定权限的管理员，用于验证越权边界。
     *
     * @param fixture         数据夹具
     * @param jdbcTemplate    用于按权限码反查权限 ID
     * @param roleCode        角色代码
     * @param username        用户名
     * @param permissionCodes 需要授予的权限代码，必须已经作为权限行存在
     * @return 管理员 ID
     */
    public static Long createLimitedAdmin(M3FixtureService fixture, JdbcTemplate jdbcTemplate, String roleCode,
            String username, String... permissionCodes) {
        Long roleId = fixture.createRole(roleCode, "部分权限角色");
        for (String code : permissionCodes) {
            Long permissionId = jdbcTemplate.queryForObject("select id from sys_permission where code = ?",
                    Long.class, code);
            assertThat(permissionId).as("权限 %s 必须已写入种子数据", code).isNotNull();
            fixture.linkRolePermission(roleId, permissionId);
        }
        Long adminId = fixture.createAdmin(username, LIMITED_PASSWORD, "enabled");
        fixture.grantRole(adminId, roleId);
        return adminId;
    }

    /**
     * 创建初始状态即为停用的管理员账号。
     *
     * @param fixture 数据夹具
     * @return 管理员 ID
     */
    public static Long createDisabledAdmin(M3FixtureService fixture) {
        return fixture.createAdmin(BLOCKED_USERNAME, BLOCKED_PASSWORD, "disabled");
    }

    /**
     * 发送登录请求。
     *
     * @param client   测试客户端
     * @param username 用户名
     * @param password 密码
     * @return 响应快照
     */
    public static ResponseSnapshot login(RestTestClient client, String username, String password) {
        String body = "{\"username\":" + jsonText(username) + ",\"password\":" + jsonText(password) + "}";
        return exchange(client, HttpMethod.POST, "/api/admin/v1/auth/login", body, null);
    }

    /**
     * 取出登录响应中的访问令牌，取不到时让用例直接失败。
     *
     * @param snapshot 登录响应快照
     * @return 访问令牌
     */
    public static String requireAccessToken(ResponseSnapshot snapshot) {
        Matcher matcher = TOKEN_PATTERN.matcher(snapshot.body());
        assertThat(matcher.find()).as("登录响应应包含 accessToken，实际响应为 %s", snapshot.body()).isTrue();
        return matcher.group(1);
    }

    /**
     * 发送不带请求体的请求。
     *
     * @param client 测试客户端
     * @param uri    请求路径，可带查询参数
     * @param token  Bearer 令牌，允许为 {@code null}
     * @return 响应快照
     */
    public static ResponseSnapshot get(RestTestClient client, String uri, String token) {
        return exchange(client, HttpMethod.GET, uri, null, token);
    }

    /**
     * 发送请求并返回 UTF-8 解码后的响应。
     *
     * @param client 测试客户端
     * @param method 请求方法
     * @param uri    请求路径
     * @param body   请求体，允许为 {@code null}
     * @param token  Bearer 令牌，允许为 {@code null}
     * @return 响应快照
     */
    public static ResponseSnapshot exchange(RestTestClient client, HttpMethod method, String uri, String body,
            String token) {
        RestTestClient.RequestBodySpec request = client.method(method).uri(uri);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RestTestClient.RequestHeadersSpec<?> spec = body == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).body(body);
        EntityExchangeResult<byte[]> result = spec.exchange().returnResult(byte[].class);
        return snapshot(result.getStatus(), result.getResponseHeaders(), result.getResponseBody());
    }

    /**
     * 以 multipart/form-data 上传文件。
     *
     * <p>请求体按字节拼装，中文文件名以 UTF-8 写入 form-data 的 filename 参数；
     * 上传硬上限与方案校验都由服务端完成，本方法不做任何预判。</p>
     *
     * @param port        随机端口
     * @param token       Bearer 令牌
     * @param fileName    原始文件名，可含中文
     * @param contentType 声明的内容类型
     * @param content     文件内容
     * @return 响应快照
     */
    public static ResponseSnapshot upload(int port, String token, String fileName, String contentType, byte[] content) {
        String boundary = "----forgeM4Boundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        payload.writeBytes(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        payload.writeBytes(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        payload.writeBytes(content);
        payload.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + FILES_PATH))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray()))
                .build();
        try {
            HttpResponse<byte[]> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());
            HttpHeaders headers = new HttpHeaders();
            response.headers().map().forEach(headers::put);
            return snapshot(HttpStatusCode.valueOf(response.statusCode()), headers, response.body());
        } catch (IOException failure) {
            throw new IllegalStateException("multipart 上传请求发送失败", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("multipart 上传请求被中断", failure);
        }
    }

    /**
     * 构造本地存储方案的新增请求体。
     *
     * @param code              方案代码
     * @param name              方案名称
     * @param baseDir           相对目录
     * @param maxFileSize       单文件大小上限（字节）
     * @param allowedExtensions 允许的扩展名，允许为 {@code null}
     * @return JSON 请求体
     */
    public static String localConfigBody(String code, String name, String baseDir, long maxFileSize,
            String allowedExtensions) {
        return "{\"code\":" + jsonText(code) + ",\"name\":" + jsonText(name) + ",\"provider\":\"local\""
                + ",\"baseDir\":" + jsonText(baseDir) + ",\"maxFileSize\":" + maxFileSize
                + ",\"allowedExtensions\":" + nullableText(allowedExtensions) + "}";
    }

    /**
     * 构造本地存储方案的完整修改请求体。
     *
     * @param name        方案名称
     * @param baseDir     相对目录
     * @param maxFileSize 单文件大小上限（字节）
     * @return JSON 请求体
     */
    public static String localUpdateBody(String name, String baseDir, long maxFileSize) {
        return "{\"name\":" + jsonText(name) + ",\"provider\":\"local\",\"baseDir\":" + jsonText(baseDir)
                + ",\"maxFileSize\":" + maxFileSize + ",\"allowedExtensions\":null}";
    }

    /**
     * 构造对象存储方案的新增请求体。
     *
     * @param code       方案代码
     * @param name       方案名称
     * @param endpoint   访问地址
     * @param region     区域，允许为 {@code null}
     * @param bucket     桶名称
     * @param accessKey  访问凭据明文
     * @param secretKey  访问密钥明文
     * @param maxFileSize 单文件大小上限（字节）
     * @return JSON 请求体
     */
    public static String s3ConfigBody(String code, String name, String endpoint, String region, String bucket,
            String accessKey, String secretKey, long maxFileSize) {
        return "{\"code\":" + jsonText(code) + ",\"name\":" + jsonText(name) + ",\"provider\":\"s3\""
                + ",\"endpoint\":" + jsonText(endpoint) + ",\"region\":" + nullableText(region)
                + ",\"bucket\":" + jsonText(bucket) + ",\"pathStyle\":true"
                + ",\"accessKey\":" + jsonText(accessKey) + ",\"secretKey\":" + jsonText(secretKey)
                + ",\"maxFileSize\":" + maxFileSize + "}";
    }

    /**
     * 构造权限新增请求体。
     *
     * @param code 权限代码
     * @param name 权限名称
     * @return JSON 请求体
     */
    public static String permissionBody(String code, String name) {
        return "{\"code\":" + jsonText(code) + ",\"name\":" + jsonText(name) + ",\"description\":null}";
    }

    /**
     * 读取响应体中的 {@code body.code}。
     *
     * @param body 响应体
     * @return 业务结果码
     */
    public static int codeOf(String body) {
        Matcher matcher = CODE_PATTERN.matcher(body);
        assertThat(matcher.find()).as("响应体应包含 code 字段，实际响应为 %s", body).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * 读取分页响应体中的 {@code data.total}。
     *
     * @param body 响应体
     * @return 记录总数
     */
    public static long totalOf(String body) {
        Matcher matcher = TOTAL_PATTERN.matcher(body);
        assertThat(matcher.find()).as("分页响应应包含 total 字段，实际响应为 %s", body).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    /**
     * 读取响应体中的第一个对外 ID。
     *
     * @param body 响应体
     * @return 字符串形式的 ID
     */
    public static String firstIdOf(String body) {
        Matcher matcher = ID_PATTERN.matcher(body);
        assertThat(matcher.find()).as("响应体应包含 id 字段，实际响应为 %s", body).isTrue();
        return matcher.group(1);
    }

    /**
     * 列出目录下递归的全部普通文件。
     *
     * @param root 目录，允许不存在
     * @return 普通文件路径列表，目录不存在时为空列表
     */
    public static List<Path> regularFiles(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException failure) {
            throw new IllegalStateException("遍历存储目录失败", failure);
        }
    }

    /**
     * 删除目录下递归的全部普通文件，保留目录本身。
     *
     * <p>存储根目录在同一个测试类内被多个用例共用，清理动作让“失败补偿后不残留对象”的断言
     * 只观察本次上传的结果。</p>
     *
     * @param root 目录，允许不存在
     */
    public static void deleteRegularFiles(Path root) {
        try {
            for (Path file : regularFiles(root)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("清理存储目录失败", failure);
        }
    }

    /**
     * 生成 JSON 字符串字面量，转义引号、反斜杠与控制字符。
     *
     * @param raw 原始文本
     * @return 带引号的 JSON 字符串
     */
    private static String jsonText(String raw) {
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(current);
            }
        }
        return builder.append('"').toString();
    }

    /**
     * 生成可空的 JSON 字符串字面量。
     *
     * @param raw 原始文本，允许为 {@code null}
     * @return 文本为空时返回 {@code null} 字面量，否则返回带引号的 JSON 字符串
     */
    private static String nullableText(String raw) {
        return raw == null ? "null" : jsonText(raw);
    }

    /**
     * 组装响应快照。
     *
     * @param status  HTTP 状态
     * @param headers 响应头
     * @param binary  响应字节，允许为 {@code null}
     * @return 响应快照
     */
    private static ResponseSnapshot snapshot(HttpStatusCode status, HttpHeaders headers, byte[] binary) {
        byte[] payload = binary == null ? new byte[0] : binary;
        HttpHeaders responseHeaders = headers == null ? new HttpHeaders() : headers;
        return new ResponseSnapshot(status, responseHeaders, new String(payload, StandardCharsets.UTF_8), payload);
    }

    /**
     * 一次响应的 HTTP 状态、响应头、文本体与原始字节。
     *
     * @param status  HTTP 状态
     * @param headers 响应头
     * @param body    UTF-8 解码后的响应文本，二进制响应时该值不可用
     * @param binary  原始响应字节
     */
    public record ResponseSnapshot(HttpStatusCode status, HttpHeaders headers, String body, byte[] binary) {
    }
}
