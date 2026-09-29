package cn.orangenode.forge.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

/**
 * 初始管理员引导配置。
 *
 * <p>对应配置前缀 {@code forge.system.bootstrap}。凭据只从环境变量或未提交的环境文件读取：
 * 密码不写入种子 SQL，也不进入版本库；引导只在账号表为空时创建管理员，不覆盖已有账号。</p>
 *
 * <p>账号表为空且未提供凭据时启动直接失败，避免部署出一个无法登录的实例；
 * 已有管理员时本配置不生效。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.system.bootstrap")
public class ForgeSystemBootstrapProperties {

    /**
     * 是否启用初始管理员引导。
     */
    private boolean enabled = true;

    /**
     * 初始管理员登录用户名，来自环境变量 {@code FORGE_INIT_ADMIN_USERNAME}。
     */
    private String username;

    /**
     * 初始管理员登录密码，来自环境变量 {@code FORGE_INIT_ADMIN_PASSWORD}，日志与响应中都不出现。
     */
    private String password;

    /**
     * 初始管理员显示名称。
     */
    private String displayName = "初始管理员";
}
