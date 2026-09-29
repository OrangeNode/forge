package cn.orangenode.forge.framework.config;

import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * MyBatis-Plus 分页插件配置。
 *
 * <p>对应配置前缀 {@code forge.mybatis-plus}。分页方言按部署数据库选择，
 * 不在代码里写死；首版默认 MySQL，接入其他数据库时只改配置。</p>
 *
 * <p>每页条数上限由分页入参 {@code forge.page.max-size} 控制，
 * 这里不再重复限制，避免同一规则出现两个来源。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "forge.mybatis-plus")
public class ForgeMybatisPlusProperties {

    /**
     * 分页方言对应的数据库类型，取值使用 MyBatis-Plus 的 DbType 枚举名。
     */
    private String dbType = "MYSQL";
}
