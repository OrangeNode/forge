package cn.orangenode.forge.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户认证配置。
 *
 * <p>对应前缀 {@code forge.member.auth}：普通用户令牌与管理员令牌使用不同的有效期、Redis 键与接口前缀，
 * 两种令牌互不通用。App 功能开关模块未装配时，观看授权与注册使用这里的默认值。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.member.auth")
public class ForgeMemberAuthProperties {

    /**
     * 令牌有效期秒数，每次续期后重新计时（滑动有效期）。
     */
    @Min(value = 300, message = "普通用户令牌有效期不能少于 300 秒")
    private long tokenTtlSeconds = 2592000;

    /**
     * 两次续期之间的最短间隔秒数，避免每个请求都改写 Redis 与设备活跃时间。
     */
    @Min(value = 1, message = "普通用户令牌续期间隔必须大于 0 秒")
    private long renewIntervalSeconds = 3600;

    /**
     * 普通用户接口路径前缀；只在该前缀下识别普通用户令牌，管理员令牌在该前缀下无效。
     */
    @NotBlank(message = "普通用户接口前缀不能为空")
    @Pattern(regexp = "^/.*/$", message = "普通用户接口前缀必须以 / 开头并以 / 结尾")
    private String pathPrefix = "/api/app/";

    /**
     * App 功能开关模块未装配时：是否只有 VIP 能观看。
     */
    private boolean defaultVipRequired = true;

    /**
     * App 功能开关模块未装配时：是否开放注册。
     */
    private boolean defaultRegistrationOpen = true;

    /**
     * App 功能开关模块未装配时：同一账号同时登录的设备上限。
     */
    @Min(value = 1, message = "同时登录设备上限必须大于 0")
    private int defaultMaxDevices = 2;
}
