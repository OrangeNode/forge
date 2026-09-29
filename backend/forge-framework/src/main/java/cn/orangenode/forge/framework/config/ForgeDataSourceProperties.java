package cn.orangenode.forge.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 数据源相关配置。
 *
 * <p>对应配置前缀 {@code forge.datasource}。连接信息本身仍由 dynamic-datasource 的
 * {@code spring.datasource.dynamic} 配置提供；本类只表达“哪个名称是主库”这类项目级约定，
 * 避免主库名称散落在 Java 代码里。</p>
 *
 * <p>默认值与 {@code spring.datasource.dynamic.primary} 保持一致，两处不一致时以本项为准，
 * 因为它决定 Flyway 迁移等主库相关行为的落点。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "forge.datasource")
public class ForgeDataSourceProperties {

    /**
     * 主库在 dynamic-datasource 中注册的数据源名称。
     */
    private String primaryName = "master";
}
