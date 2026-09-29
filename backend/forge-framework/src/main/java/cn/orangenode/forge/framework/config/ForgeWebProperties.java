package cn.orangenode.forge.framework.config;

import java.time.ZoneId;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

/**
 * 基础 Web 与追踪配置。
 *
 * <p>对应配置前缀 {@code forge.web}：既包含展示时区，也包含追踪编号的开关与响应头名称，
 * 这些取值都放在配置文件中，便于部署时检查和调整。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.web")
public class ForgeWebProperties {

    /**
     * 当前部署使用的展示时区。
     *
     * <p>数据库统一存储 UTC，本值只用于服务端需要按业务时区计算日期边界等场景，
     * 不改变接口使用带时区 ISO 8601 字符串的约定。</p>
     */
    private ZoneId displayZone = ZoneId.of("UTC");

    /**
     * 是否启用追踪编号过滤器。
     */
    private boolean traceEnabled = true;

    /**
     * 追踪编号响应头名称。
     */
    @NotBlank(message = "追踪编号响应头名称不能为空")
    private String traceHeaderName = "X-Trace-Id";
}
