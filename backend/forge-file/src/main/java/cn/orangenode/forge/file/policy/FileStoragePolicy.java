package cn.orangenode.forge.file.policy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件与存储配置的统一规则。
 *
 * <p>规则集中在此类：方案代码的规范化和格式、存储类型代码、本地相对目录的合法性、
 * 对象存储访问地址的合法性、允许扩展名的解析，以及上传文件名与扩展名的清洗。
 * Validation 注解、业务用例与存储适配引用同一组常量与方法，避免同一约束出现多个来源。</p>
 *
 * <p>本类只做纯字符串判断，不访问数据库、文件系统与网络，也不抛出异常：
 * 调用方根据返回值决定对外返回哪种 {@code body.code}，规则本身不绑定接口语义。</p>
 */
public final class FileStoragePolicy {

    /**
     * 本地文件系统存储类型代码。
     */
    public static final String PROVIDER_LOCAL = "local";

    /**
     * S3 兼容对象存储类型代码。
     */
    public static final String PROVIDER_S3 = "s3";

    /**
     * 存储类型取值校验表达式，大小写不敏感，取值在业务层统一转小写。
     */
    public static final String PROVIDER_PATTERN = "(?i)^(local|s3)$";

    /**
     * 存储方案代码校验表达式：字母开头，只允许字母、数字、下划线与连字符。
     */
    public static final String CODE_PATTERN = "^[A-Za-z][A-Za-z0-9_-]*$";

    /**
     * 存储方案代码最大长度，与 {@code code} 列长度一致。
     */
    public static final int CODE_MAX_LENGTH = 64;

    /**
     * 方案名称最大长度，与 {@code name} 列长度一致。
     */
    public static final int NAME_MAX_LENGTH = 64;

    /**
     * 本地相对目录最大长度，与 {@code base_dir} 列长度一致。
     */
    public static final int DIRECTORY_MAX_LENGTH = 255;

    /**
     * 对象存储访问地址最大长度，与 {@code endpoint} 列长度一致。
     */
    public static final int ENDPOINT_MAX_LENGTH = 255;

    /**
     * 对象存储区域最大长度，与 {@code region} 列长度一致。
     */
    public static final int REGION_MAX_LENGTH = 64;

    /**
     * 桶名称最大长度，与 {@code bucket} 列长度一致。
     */
    public static final int BUCKET_MAX_LENGTH = 128;

    /**
     * 访问凭据明文最大长度，密文保存在 {@code access_key} 列（长度 512）。
     */
    public static final int ACCESS_KEY_MAX_LENGTH = 512;

    /**
     * 访问密钥明文最大长度，密文保存在 {@code secret_key} 列（长度 1024）。
     */
    public static final int SECRET_KEY_MAX_LENGTH = 1024;

    /**
     * 原始文件名最大长度，与 {@code original_name} 列长度一致。
     */
    public static final int ORIGINAL_NAME_MAX_LENGTH = 255;

    /**
     * 内容类型最大长度，与 {@code content_type} 列长度一致。
     */
    public static final int CONTENT_TYPE_MAX_LENGTH = 128;

    /**
     * 单个扩展名最大长度，超出该长度的后缀按“无扩展名”处理。
     */
    public static final int EXTENSION_MAX_LENGTH = 16;

    /**
     * 允许扩展名的数量上限，避免配置过长而超出 {@code allowed_extensions} 列长度。
     */
    public static final int EXTENSION_COUNT_MAX = 50;

    /**
     * 允许扩展名配置的最大长度，与 {@code allowed_extensions} 列长度一致。
     */
    public static final int ALLOWED_EXTENSIONS_MAX_LENGTH = 512;

    /**
     * 单个扩展名的格式表达式：只允许小写字母与数字。
     */
    private static final String EXTENSION_PATTERN = "[a-z0-9]{1," + EXTENSION_MAX_LENGTH + "}";

    /**
     * 相对目录段中禁止出现的字符：Windows 保留字符与路径分隔符以外的危险字符。
     */
    private static final String FORBIDDEN_DIRECTORY_CHARACTERS = "<>:\"|?*";

    /**
     * 工具类不允许实例化。
     */
    private FileStoragePolicy() {
    }

    /**
     * 规范化存储方案代码：去首尾空格并转为小写。
     *
     * <p>数据库中唯一键使用 {@code utf8mb4_0900_ai_ci}，大小写不敏感；
     * 保存前统一转小写后，不会出现 {@code Default} 与 {@code default} 两个方案。</p>
     *
     * @param code 原始方案代码，允许为 {@code null}
     * @return 规范化后的代码，入参为空时返回 {@code null}
     */
    public static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 判断存储方案代码是否符合格式要求。
     *
     * @param code 已规范化的方案代码，允许为 {@code null}
     * @return 长度与字符集都符合要求时返回 {@code true}
     */
    public static boolean isValidCode(String code) {
        return code != null && code.length() <= CODE_MAX_LENGTH && code.matches(CODE_PATTERN);
    }

    /**
     * 规范化存储类型代码：去首尾空格并转为小写。
     *
     * @param provider 原始存储类型，允许为 {@code null}
     * @return 规范化后的存储类型，入参为空时返回 {@code null}
     */
    public static String normalizeProvider(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.strip().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 判断存储类型是否为受支持的类型。
     *
     * @param provider 已规范化的存储类型，允许为 {@code null}
     * @return 为 {@code local} 或 {@code s3} 时返回 {@code true}
     */
    public static boolean isSupportedProvider(String provider) {
        return PROVIDER_LOCAL.equals(provider) || PROVIDER_S3.equals(provider);
    }

    /**
     * 规范化本地存储相对目录。
     *
     * <p>只接受位于环境根目录之内的相对路径：拒绝绝对路径、盘符、{@code ..} 段、
     * 控制字符与 Windows 保留字符，并去掉重复斜杠、多余的 {@code ./} 前缀与结尾斜杠。
     * 反斜杠统一转换为正斜杠，避免同一目录在 Windows 与 Linux 上出现两种写法。</p>
     *
     * @param directory 原始相对目录，允许为 {@code null}
     * @return 规范化后的相对目录，不合法时返回 {@code null}
     */
    public static String normalizeRelativeDirectory(String directory) {
        if (directory == null) {
            return null;
        }
        String normalized = directory.strip().replace('\\', '/');
        if (normalized.isEmpty() || normalized.length() > DIRECTORY_MAX_LENGTH) {
            return null;
        }
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            return null;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        for (String segment : normalized.split("/")) {
            if (!isValidDirectorySegment(segment)) {
                return null;
            }
        }
        return normalized;
    }

    /**
     * 判断对象存储访问地址是否合法。
     *
     * <p>只接受带主机名的 {@code http} 或 {@code https} 地址，避免把非法字符串写进配置后
     * 在调用对象存储时才以难以解释的方式失败。</p>
     *
     * @param endpoint 访问地址，允许为 {@code null}
     * @return 合法时返回 {@code true}
     */
    public static boolean isValidEndpoint(String endpoint) {
        if (endpoint == null) {
            return false;
        }
        String trimmed = endpoint.strip();
        if (trimmed.isEmpty() || trimmed.length() > ENDPOINT_MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return supportedScheme && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException failure) {
            return false;
        }
    }

    /**
     * 拆分并规范化允许的扩展名配置。
     *
     * <p>输入是逗号分隔的字符串，允许带前导点与大小写差异；输出按输入顺序去重，
     * 元素为不含点的小写扩展名。空白输入返回空列表，表示不限制扩展名。</p>
     *
     * @param raw 原始配置，允许为 {@code null}
     * @return 规范化后的扩展名列表，可能为空
     */
    public static List<String> splitExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String extension = part.strip().toLowerCase(Locale.ROOT);
            if (extension.startsWith(".")) {
                extension = extension.substring(1);
            }
            if (!extension.isEmpty()) {
                normalized.add(extension);
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * 判断单个扩展名是否符合格式要求。
     *
     * @param extension 不含点的小写扩展名，允许为 {@code null}
     * @return 只含字母数字且长度合规时返回 {@code true}
     */
    public static boolean isValidExtension(String extension) {
        return extension != null && extension.matches(EXTENSION_PATTERN);
    }

    /**
     * 把扩展名列表拼接为数据库保存的字符串。
     *
     * @param extensions 扩展名列表，允许为 {@code null}
     * @return 逗号分隔的字符串，列表为空时返回 {@code null} 表示不限制
     */
    public static String joinExtensions(List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return null;
        }
        return String.join(",", extensions);
    }

    /**
     * 解析数据库中保存的允许扩展名。
     *
     * @param stored 数据库列内容，允许为 {@code null}
     * @return 有序去重的扩展名集合，未配置时返回空集合
     */
    public static Set<String> parseAllowedExtensions(String stored) {
        return new LinkedHashSet<>(splitExtensions(stored));
    }

    /**
     * 从原始文件名提取扩展名。
     *
     * <p>返回不含点的小写扩展名；没有扩展名或后缀不是“字母数字”形式（例如中文后缀）时返回空串，
     * 由调用方按“无扩展名”处理。文件名中的中文不会影响对象键，对象键由随机值生成。</p>
     *
     * @param originalName 原始文件名，允许为 {@code null}
     * @return 小写扩展名，无法识别时返回空串
     */
    public static String resolveExtension(String originalName) {
        String name = sanitizeOriginalName(originalName);
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return isValidExtension(extension) ? extension : "";
    }

    /**
     * 清洗原始文件名，只保留名称部分。
     *
     * <p>部分客户端会把完整路径放进 {@code filename}，这里去掉目录部分；
     * 控制字符会破坏响应头与日志，出现即判定文件名不合法。清洗后的名称只用于元数据与下载响应头，
     * 不参与对象键生成。</p>
     *
     * @param originalName 原始文件名，允许为 {@code null}
     * @return 清洗后的文件名，为空或含控制字符时返回 {@code null}
     */
    public static String sanitizeOriginalName(String originalName) {
        if (originalName == null) {
            return null;
        }
        String name = originalName.strip().replace('\\', '/');
        int separator = name.lastIndexOf('/');
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (current < 0x20 || current == 0x7f) {
                return null;
            }
        }
        return name.isEmpty() ? null : name;
    }

    /**
     * 判断上传声明的内容类型是否可用。
     *
     * <p>内容类型来自客户端，会写入元数据并用于下载响应头，因此只接受可打印 ASCII 字符：
     * 换行等控制字符可能被用来伪造响应头，直接拒绝。</p>
     *
     * @param contentType 内容类型，允许为 {@code null}
     * @return 长度合规且只含可打印 ASCII 字符时返回 {@code true}
     */
    public static boolean isValidContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || contentType.length() > CONTENT_TYPE_MAX_LENGTH) {
            return false;
        }
        for (int index = 0; index < contentType.length(); index++) {
            char current = contentType.charAt(index);
            if (current < 0x20 || current > 0x7e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断相对目录中的单个路径段是否合法。
     *
     * <p>拒绝空段、{@code .}、{@code ..}、控制字符、Windows 保留字符，
     * 以及以点或空格结尾的段（Windows 会静默去掉结尾的点与空格，导致实际目录与配置不一致）。</p>
     *
     * @param segment 路径段
     * @return 合法时返回 {@code true}
     */
    private static boolean isValidDirectorySegment(String segment) {
        if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
            return false;
        }
        char last = segment.charAt(segment.length() - 1);
        if (last == '.' || last == ' ') {
            return false;
        }
        for (int index = 0; index < segment.length(); index++) {
            char current = segment.charAt(index);
            if (current < 0x20 || current == 0x7f || FORBIDDEN_DIRECTORY_CHARACTERS.indexOf(current) >= 0) {
                return false;
            }
        }
        return true;
    }
}
