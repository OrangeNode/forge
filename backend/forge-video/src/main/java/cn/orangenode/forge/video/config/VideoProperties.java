package cn.orangenode.forge.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 视频采集可调参数，唯一默认来源为 application.yaml。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.video")
public class VideoProperties {
    @Min(1)
    private int connectTimeoutSeconds;
    @Min(1)
    private int requestTimeoutSeconds;
    @Min(0)
    private long requestIntervalMillis;
    @Min(0)
    private int retryCount;
    @Min(1024)
    private int maxResponseBytes;
    @Min(1024)
    private int maxPosterBytes;
    @Min(1)
    private int scanIntervalSeconds;
    /**
     * 后台“最大并发采集数”允许设置的上限，防止压垮数据库与本机网络。
     */
    @Min(1)
    private int maxConcurrencyLimit;
    @Min(1)
    private int logRetentionDays;
    @Min(1)
    private int appRateLimit;
    @Min(1)
    private int appRateWindowSeconds;
    @NotBlank
    private String defaultZone;
    /**
     * 可选 HTTP 代理地址，由部署配置提供，禁止在后台接口中修改。
     */
    private String proxyUrl;
    @NotBlank
    private String userAgent;
    private boolean workerEnabled;
    private boolean autoPublish;
    private java.util.List<String> defaultMatchFields;
    private java.util.List<String> defaultUpdateFields;
    private java.util.List<SeedSource> sources;

    /**
     * 首次安装的资源站配置，后续由后台维护。
     */
    @Getter
    @Setter
    public static class SeedSource {
        private String name;
        private String apiUrl;
    }
}
