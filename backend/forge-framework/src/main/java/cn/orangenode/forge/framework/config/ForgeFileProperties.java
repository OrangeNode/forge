package cn.orangenode.forge.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件与存储配置。
 *
 * <p>对应配置前缀 {@code forge.file}：本地根目录、临时目录与凭据加密主密钥都来自运行环境，
 * 后台只能配置根目录下的相对目录，因此这里给出的是目录边界而不是存储目标本身。</p>
 *
 * <p>主密钥缺失时不在启动阶段失败，而是在保存 S3 凭据时明确报错：本地存储不依赖主密钥，
 * 缺少主密钥不应该让只使用本地存储的实例无法启动。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.file")
public class ForgeFileProperties {

    /**
     * 本地存储根目录，来自环境变量 {@code FORGE_FILE_LOCAL_ROOT}；后台配置的相对目录必须位于其下。
     */
    private String localRoot = "./data/forge-files";

    /**
     * 下载与上传过程使用的临时目录，来自环境变量 {@code FORGE_FILE_TEMP_DIR}。
     */
    private String tempDir = "./data/forge-tmp";

    /**
     * 临时文件保留小时数，超过该时间未被清理的临时文件由维护任务删除。
     */
    @Min(value = 1, message = "临时文件保留小时数必须大于 0")
    private long tempRetentionHours = 24;

    /**
     * 凭据加密主密钥，Base64 编码的 32 字节密钥，来自环境变量 {@code FORGE_FILE_ENCRYPTION_KEY}。
     */
    private String encryptionKey;

    /**
     * 应用上传硬上限，读取 {@code spring.servlet.multipart.max-file-size}。
     *
     * <p>管理员配置的单文件上限不得高于该值，避免绕过应用级保护。</p>
     */
    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize hardMaxFileSize = DataSize.ofMegabytes(50);

    /**
     * 未指定存储方案时使用的默认方案代码。
     */
    private String defaultConfigCode = "default";

    /**
     * 是否启用临时文件清理任务。
     */
    private boolean tempCleanupEnabled = true;

    /**
     * 临时文件清理任务的执行间隔小时数。
     */
    @Min(value = 1, message = "临时文件清理间隔必须大于 0 小时")
    private long tempCleanupIntervalHours = 6;
}
