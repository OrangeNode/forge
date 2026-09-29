package cn.orangenode.forge.framework.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * 认证与权限配置。
 *
 * <p>对应配置前缀 {@code forge.security}：令牌有效期、登录失败限流参数、超级管理员角色代码
 * 与匿名放行路径都来自配置文件，避免在安全过滤链里写死数值与路径。</p>
 *
 * <p>匿名放行路径是安全边界的唯一来源：过滤链按本列表逐条放行，其余路径一律要求认证。
 * 生产环境必须关闭 OpenAPI 文档（{@code springdoc.*}），需要保留文档时把文档路径从本列表移除，
 * 改由令牌访问，避免暴露内部接口结构。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.security")
public class ForgeSecurityProperties {

    /**
     * 令牌有效期秒数。
     *
     * <p>默认 7200 秒（两小时），首版不提供刷新令牌，到期后必须重新登录。
     * 该值同时用于会话版本键的存活时间，保证版本键不会早于其失效的令牌过期。</p>
     */
    @Min(value = 60, message = "令牌有效期不能少于 60 秒")
    private long tokenTtlSeconds = 7200;

    /**
     * 同一用户名在统计窗口内允许的登录失败次数上限，达到上限后返回 429。
     */
    @Min(value = 1, message = "登录失败次数上限必须大于 0")
    private int loginMaxAttempts = 5;

    /**
     * 登录失败计数的统计窗口秒数，每次失败都会重新计时。
     */
    @Min(value = 1, message = "登录失败统计窗口必须大于 0 秒")
    private long loginAttemptWindowSeconds = 300;

    /**
     * 超级管理员角色代码。
     *
     * <p>拥有该角色的管理员在解析权限时获得全部有效权限，无需逐条建立角色与权限关系；
     * 这样空库安装后由初始化引导创建的管理员才能管理工作台自身。</p>
     */
    @NotBlank(message = "超级管理员角色代码不能为空")
    private String superRoleCode = "super_admin";

    /**
     * 权限缓存存活秒数。
     *
     * <p>权限解析结果按管理员缓存该时长，缓存键包含全局权限版本：任何角色、权限或管理员角色关系变更
     * 都会递增版本，使所有缓存立即失效，不需要通配扫描删除旧键。</p>
     */
    @Min(value = 1, message = "权限缓存存活时间必须大于 0 秒")
    private long permissionCacheTtlSeconds = 300;

    /**
     * 匿名放行的路径模式，支持 Spring 的路径匹配写法。
     *
     * <p>至少保留登录接口；CORS 预检请求由过滤链单独放行，不需要写在这里。</p>
     */
    @NotEmpty(message = "匿名放行路径不能为空，至少保留登录接口")
    private List<String> permitAllPaths = new ArrayList<>(List.of(
            "/api/admin/v1/auth/login",
            "/error"));
}
