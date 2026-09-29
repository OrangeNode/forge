package cn.orangenode.forge.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Redis 键与缓存配置。
 *
 * <p>对应配置前缀 {@code forge.redis}。Redis 键必须包含项目、环境与模块前缀，
 * 避免同一 Redis 实例中不同项目或不同环境的键互相覆盖；
 * 缓存必须给出明确 TTL，禁止写入不过期的业务缓存。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.redis")
public class ForgeRedisProperties {

    /**
     * Redis 键前缀，建议形如 {@code forge:local}，包含项目名与运行环境。
     */
    @NotBlank(message = "Redis 键前缀不能为空")
    private String keyPrefix = "forge:local";

    /**
     * 缓存默认存活秒数，未显式指定 TTL 的缓存写入使用该值。
     */
    @Min(value = 1, message = "Redis 缓存默认存活秒数必须大于 0")
    private long defaultTtlSeconds = 600;
}
