package cn.orangenode.forge.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.framework.storage.StorageProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 本地文件系统存储适配。
 *
 * <p>写入范围被限制在“环境根目录 + 配置里的相对目录”之内，防穿越分三层：
 * 相对目录先按规则规范化（拒绝绝对路径、盘符、{@code ..} 段与保留字符），
 * 再逐段创建并核对每段目录的真实路径仍在根目录内，最后对完整路径做真实路径核对。
 * 目录段被替换成指向外部的符号链接或 Windows junction 时，真实路径核对会失败，
 * 不会把对象写到根目录之外。</p>
 *
 * <p>对象键只由随机值组成，不包含原始文件名，因此中文文件名不影响存储位置。
 * 异常信息不包含本地绝对路径，日志只记录对象键。</p>
 */
@Slf4j
public class LocalStorageProvider implements StorageProvider {

    /**
     * 存储类型代码。
     */
    private static final String PROVIDER_CODE = "local";

    /**
     * 目标目录不合法时对外的统一提示。
     */
    private static final String INVALID_DIRECTORY_MESSAGE = "本地存储相对目录不合法，只能是根目录内的相对路径";

    /**
     * 目标目录不可用时对外的统一提示。
     */
    private static final String UNAVAILABLE_DIRECTORY_MESSAGE = "本地存储目录不可用，请检查根目录配置与访问权限";

    /**
     * 规范化后的目标目录真实路径，保证位于根目录之内。
     */
    private final Path baseDirectory;

    /**
     * 构造本地存储适配。
     *
     * <p>构造时创建根目录与目标目录并解析真实路径：目录不可创建或不可访问时立即失败，
     * 避免把问题推迟到第一次上传。相对目录为空表示直接使用根目录。</p>
     *
     * @param rootDirectory     环境配置的本地存储根目录
     * @param relativeDirectory 后台配置的相对目录，允许为空
     * @throws BusinessException 相对目录越界时抛出 400，目录不可用时抛出 503
     */
    public LocalStorageProvider(Path rootDirectory, String relativeDirectory) {
        Path root = prepareRootDirectory(rootDirectory);
        this.baseDirectory = prepareBaseDirectory(root, relativeDirectory);
    }

    /**
     * 返回存储类型代码。
     *
     * @return 固定为 {@code local}
     */
    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    /**
     * 写入对象。
     *
     * <p>父目录逐段创建并核对真实路径后才写入；写入字节数与声明长度不一致时删除残留文件并失败，
     * 不留下内容不完整的对象。</p>
     *
     * @param objectKey     随机生成的对象键
     * @param content       对象内容，由调用者负责关闭
     * @param contentLength 内容长度（字节）
     * @param contentType   内容类型，本地存储不保存该值
     * @throws BusinessException 对象键越界时抛出 400，写入失败时抛出 503
     */
    @Override
    public void store(String objectKey, InputStream content, long contentLength, String contentType) {
        Path target = resolveWritablePath(objectKey);
        try {
            long copied = Files.copy(content, target);
            if (contentLength >= 0 && copied != contentLength) {
                Files.deleteIfExists(target);
                log.error("本地存储写入字节数与声明长度不一致，objectKey={}", objectKey);
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "本地存储写入失败，文件内容不完整");
            }
        } catch (FileAlreadyExistsException failure) {
            log.error("本地存储对象键冲突，objectKey={}", objectKey);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "对象键冲突，请重试");
        } catch (IOException failure) {
            log.error("本地存储写入失败，objectKey={}", objectKey);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_DIRECTORY_MESSAGE);
        }
    }

    /**
     * 按对象键读取对象。
     *
     * @param objectKey 随机生成的对象键
     * @return 对象内容，由调用者负责关闭
     * @throws BusinessException 对象键越界时抛出 400，对象不存在时抛出 404，读取失败时抛出 503
     */
    @Override
    public InputStream read(String objectKey) {
        Path target = resolveExistingPath(objectKey);
        try {
            return Files.newInputStream(target);
        } catch (IOException failure) {
            log.error("本地存储读取失败，objectKey={}", objectKey);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_DIRECTORY_MESSAGE);
        }
    }

    /**
     * 按对象键删除对象。
     *
     * <p>对象不存在视为删除成功；符号链接与 junction 指向目录之外时拒绝删除，
     * 避免通过存储对象误删根目录之外的文件。</p>
     *
     * @param objectKey 随机生成的对象键
     * @throws BusinessException 对象键越界时抛出 400，删除失败时抛出 503
     */
    @Override
    public void delete(String objectKey) {
        Path target = toPath(objectKey);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Path real = target.toRealPath();
            if (!real.startsWith(baseDirectory)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键指向了存储目录之外的位置");
            }
            Files.deleteIfExists(real);
        } catch (IOException failure) {
            log.error("本地存储删除失败，objectKey={}", objectKey);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_DIRECTORY_MESSAGE);
        }
    }

    /**
     * 检测本地存储目标是否可创建与可写。
     *
     * <p>创建目标目录后写入并删除一个探针文件：只判断目录存在无法发现“只读挂载”或“无写权限”，
     * 探针写入能真实反映上传时的结果。</p>
     *
     * @throws BusinessException 目录不可创建或不可写时抛出 503
     */
    @Override
    public void verify() {
        try {
            Files.createDirectories(baseDirectory);
            Path probe = Files.createTempFile(baseDirectory, "forge-write-probe-", ".tmp");
            try {
                Files.write(probe, new byte[] {0});
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException failure) {
            log.error("本地存储目标目录不可写");
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_DIRECTORY_MESSAGE);
        }
    }

    /**
     * 准备并规范化环境根目录。
     *
     * @param rootDirectory 环境配置的根目录
     * @return 已创建并解析真实路径的根目录
     * @throws BusinessException 未配置根目录或目录不可用时抛出 503
     */
    private static Path prepareRootDirectory(Path rootDirectory) {
        if (rootDirectory == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "未配置本地存储根目录，无法使用本地存储");
        }
        try {
            Path root = rootDirectory.toAbsolutePath().normalize();
            Files.createDirectories(root);
            return root.toRealPath();
        } catch (IOException failure) {
            log.error("本地存储根目录不可用");
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "本地存储根目录不可用，请检查根目录配置与访问权限");
        }
    }

    /**
     * 准备并规范化目标目录。
     *
     * @param root              已解析真实路径的根目录
     * @param relativeDirectory 后台配置的相对目录，允许为空表示使用根目录
     * @return 位于根目录之内、已解析真实路径的目标目录
     * @throws BusinessException 相对目录越界时抛出 400，目录不可用时抛出 503
     */
    private static Path prepareBaseDirectory(Path root, String relativeDirectory) {
        boolean configured = relativeDirectory != null && !relativeDirectory.isBlank();
        String normalized = FileStoragePolicy.normalizeRelativeDirectory(relativeDirectory);
        if (configured && normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_DIRECTORY_MESSAGE);
        }
        Path candidate = normalized == null ? root : root.resolve(normalized).normalize();
        if (!candidate.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_DIRECTORY_MESSAGE);
        }
        try {
            Files.createDirectories(candidate);
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_DIRECTORY_MESSAGE);
            }
            return real;
        } catch (IOException failure) {
            log.error("本地存储目标目录不可用");
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_DIRECTORY_MESSAGE);
        }
    }

    /**
     * 解析可写入的对象路径，逐段创建父目录并核对真实路径。
     *
     * <p>先判断目录段是否已存在再决定创建：已存在的段如果是符号链接或 junction，
     * 真实路径核对会发现它指向根目录之外并拒绝写入，不会跟着链接在外部建立目录。</p>
     *
     * @param objectKey 对象键
     * @return 允许写入的目标文件路径
     * @throws BusinessException 对象键越界时抛出 400，目录创建失败时抛出 503
     */
    private Path resolveWritablePath(String objectKey) {
        List<String> segments = toSegments(objectKey);
        Path directory = baseDirectory;
        for (int index = 0; index < segments.size() - 1; index++) {
            directory = ensureSafeDirectory(directory.resolve(segments.get(index)));
        }
        return directory.resolve(segments.get(segments.size() - 1));
    }

    /**
     * 确保目录存在且其真实路径仍位于目标目录之内。
     *
     * @param directory 待检查的目录
     * @return 目录的真实路径，供后续拼接使用
     * @throws BusinessException 目录指向目标目录之外时抛出 400，创建失败时抛出 503
     */
    private Path ensureSafeDirectory(Path directory) {
        try {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(directory);
            }
            Path real = directory.toRealPath();
            if (!real.startsWith(baseDirectory)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键指向了存储目录之外的位置");
            }
            return real;
        } catch (IOException failure) {
            log.error("本地存储目录创建失败");
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, UNAVAILABLE_DIRECTORY_MESSAGE);
        }
    }

    /**
     * 解析已存在对象的路径。
     *
     * <p>对完整路径调用 {@code toRealPath()} 会解析路径上所有符号链接与 junction，
     * 再核对是否仍在目标目录之内，因此通过链接读取外部文件会被拒绝。</p>
     *
     * @param objectKey 对象键
     * @return 对象的真实路径
     * @throws BusinessException 对象键越界时抛出 400，对象不存在时抛出 404
     */
    private Path resolveExistingPath(String objectKey) {
        Path target = toPath(objectKey);
        try {
            Path real = target.toRealPath();
            if (!real.startsWith(baseDirectory)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键指向了存储目录之外的位置");
            }
            if (!Files.isRegularFile(real)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理");
            }
            return real;
        } catch (IOException failure) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理");
        }
    }

    /**
     * 把对象键转换为目标目录下的路径。
     *
     * @param objectKey 对象键
     * @return 规范化后的目标路径
     * @throws BusinessException 对象键不合法时抛出 400
     */
    private Path toPath(String objectKey) {
        Path target = baseDirectory;
        for (String segment : toSegments(objectKey)) {
            target = target.resolve(segment);
        }
        Path normalized = target.normalize();
        if (!normalized.startsWith(baseDirectory)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键指向了存储目录之外的位置");
        }
        return normalized;
    }

    /**
     * 校验并拆分对象键。
     *
     * <p>对象键必须是相对路径：拒绝空值、绝对路径、盘符、{@code .}、{@code ..} 段与控制字符。
     * 这是防穿越的第一层，之后还会做真实路径核对。</p>
     *
     * @param objectKey 对象键
     * @return 拆分的路径段，至少一个
     * @throws BusinessException 对象键不合法时抛出 400
     */
    private static List<String> toSegments(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键不能为空");
        }
        String normalized = objectKey.strip().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键必须是相对路径");
        }
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键不合法，不允许包含上级目录");
            }
            if (containsControlCharacter(segment)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "对象键不合法，不允许包含控制字符");
            }
            segments.add(segment);
        }
        return segments;
    }

    /**
     * 判断文本是否包含控制字符。
     *
     * @param text 待检查文本
     * @return 含控制字符时返回 {@code true}
     */
    private static boolean containsControlCharacter(String text) {
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current < 0x20 || current == 0x7f) {
                return true;
            }
        }
        return false;
    }
}
