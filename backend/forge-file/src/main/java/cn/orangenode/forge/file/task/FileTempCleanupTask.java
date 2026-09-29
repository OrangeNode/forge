package cn.orangenode.forge.file.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.config.ForgeFileProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 临时文件清理任务。
 *
 * <p>按 {@code forge.file.temp-retention-hours} 删除临时目录中超过保留期限的文件：
 * 上传与下载都是流式处理，临时目录用于存放跨步骤的中间文件，异常中断可能留下残留，
 * 这里按最后修改时间清理，避免长期堆积。</p>
 *
 * <p>清理范围只包含临时目录第一层的普通文件：目录与符号链接一律跳过，
 * 避免把链接指向的外部内容误删。执行间隔来自 {@code forge.file.temp-cleanup-interval-hours}，
 * 保留期限来自配置，代码里不写死时间与目录；开关关闭时组件不装配，方法内再判断一次配置值。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "forge.file", name = "temp-cleanup-enabled", havingValue = "true",
        matchIfMissing = true)
public class FileTempCleanupTask {

    /**
     * 文件与存储配置，提供临时目录、保留期限与清理开关。
     */
    private final ForgeFileProperties properties;

    /**
     * 构造临时文件清理任务。
     *
     * @param properties 文件与存储配置
     */
    public FileTempCleanupTask(ForgeFileProperties properties) {
        this.properties = properties;
    }

    /**
     * 清理超过保留期限的临时文件。
     *
     * <p>按配置的间隔周期性执行：临时目录不存在、配置关闭或目录不可读时直接返回，
     * 只把结果（删除、保留、失败数量）写入日志，便于核对清理效果。</p>
     */
    @Scheduled(fixedDelayString = "${forge.file.temp-cleanup-interval-hours:6}", timeUnit = TimeUnit.HOURS)
    public void cleanupExpiredTempFiles() {
        if (!properties.isTempCleanupEnabled()) {
            log.debug("临时文件清理已关闭，跳过本次清理");
            return;
        }
        Path tempDirectory = resolveTempDirectory();
        if (tempDirectory == null || !Files.isDirectory(tempDirectory)) {
            log.debug("临时目录不存在，跳过本次清理");
            return;
        }
        LocalDateTime boundary = LocalDateTime.now(ZoneOffset.UTC).minusHours(properties.getTempRetentionHours());
        CleanupResult result = removeExpiredFiles(tempDirectory, boundary);
        log.info("临时文件清理完成，删除 {} 个，保留 {} 个，失败 {} 个", result.deleted(), result.retained(),
                result.failed());
    }

    /**
     * 解析临时目录路径。
     *
     * @return 绝对规范化的临时目录，配置不是合法路径时返回 {@code null}
     */
    private Path resolveTempDirectory() {
        String tempDir = properties.getTempDir();
        if (tempDir == null || tempDir.isBlank()) {
            return null;
        }
        try {
            return Path.of(tempDir.strip()).toAbsolutePath().normalize();
        } catch (InvalidPathException failure) {
            log.error("临时目录配置不是合法路径，跳过本次清理");
            return null;
        }
    }

    /**
     * 删除临时目录第一层中超过保留期限的普通文件。
     *
     * @param tempDirectory 临时目录
     * @param boundary      保留期限下界（UTC），最后修改时间早于该值的文件被删除
     * @return 删除、保留与失败数量
     */
    private CleanupResult removeExpiredFiles(Path tempDirectory, LocalDateTime boundary) {
        int deleted = 0;
        int retained = 0;
        int failed = 0;
        try (Stream<Path> entries = Files.list(tempDirectory)) {
            List<Path> candidates = entries.toList();
            for (Path entry : candidates) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || !isExpired(entry, boundary)) {
                    retained++;
                    continue;
                }
                try {
                    Files.deleteIfExists(entry);
                    deleted++;
                } catch (IOException failure) {
                    failed++;
                    log.warn("临时文件删除失败，fileName={}", entry.getFileName());
                }
            }
        } catch (IOException failure) {
            log.error("临时目录读取失败，跳过本次清理");
        }
        return new CleanupResult(deleted, retained, failed);
    }

    /**
     * 判断临时文件是否超过保留期限。
     *
     * @param entry    临时文件
     * @param boundary 保留期限下界（UTC）
     * @return 最后修改时间早于下界时返回 {@code true}，时间读取失败时保守地返回 {@code false}
     */
    private boolean isExpired(Path entry, LocalDateTime boundary) {
        try {
            LocalDateTime lastModified = LocalDateTime.ofInstant(Files.getLastModifiedTime(entry).toInstant(),
                    ZoneOffset.UTC);
            return lastModified.isBefore(boundary);
        } catch (IOException failure) {
            log.warn("临时文件时间读取失败，fileName={}", entry.getFileName());
            return false;
        }
    }

    /**
     * 一次清理的数量结果。
     *
     * @param deleted  已删除数量
     * @param retained 保留数量，包含未到期与非普通文件
     * @param failed   删除失败数量
     */
    private record CleanupResult(int deleted, int retained, int failed) {
    }
}
