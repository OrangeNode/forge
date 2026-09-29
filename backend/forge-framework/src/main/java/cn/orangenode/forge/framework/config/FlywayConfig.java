package cn.orangenode.forge.framework.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Flyway 迁移配置。
 *
 * <p>dynamic-datasource 的自动配置会注册 DataSource Bean，Spring Boot 的 Flyway 自动配置
 * 因此不会按预期生效（实测表现为迁移完全不执行，且不创建迁移历史表）。本类显式构造
 * {@link Flyway}，只使用主库连接，并按装配模块的 classpath 目录迁移。</p>
 *
 * <p>客户外接数据库不在此处初始化或迁移；新增数据源也不会自动纳入迁移范围。</p>
 *
 * <p>M3 实测发现：初版虽然接收了 {@code spring.flyway.enabled}，但装配时没有判断该开关，
 * 导致把它设为 {@code false} 仍然会执行迁移；在只有 MySQL 迁移脚本的内存库测试里直接启动失败。
 * 现在通过 {@link ConditionalOnProperty} 遵循该开关，关闭时不注册 Flyway 组件。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
public class FlywayConfig {

    /**
     * 构建针对主库的 Flyway 迁移组件。
     *
     * <p>从动态数据源中取配置指定的主库对应的真实数据源，避免把路由数据源交给 Flyway。
     * 找不到主库时直接失败，不静默跳过迁移。</p>
     *
     * @param routingDataSource 动态路由数据源
     * @param properties        迁移配置，来源于 spring.flyway
     * @param dataSourceProperties 数据源约定，提供主库名称
     * @return 只针对主库的 Flyway 实例
     */
    @Bean(initMethod = "migrate")
    @ConditionalOnMissingBean(Flyway.class)
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Flyway flyway(DataSource routingDataSource, FlywayProperties properties,
            ForgeDataSourceProperties dataSourceProperties) {
        String primaryName = dataSourceProperties.getPrimaryName();
        DataSource masterDataSource = resolvePrimaryDataSource(routingDataSource, primaryName);
        Flyway flyway = Flyway.configure()
                .dataSource(masterDataSource)
                .locations(properties.getLocations().toArray(new String[0]))
                .baselineOnMigrate(properties.isBaselineOnMigrate())
                .validateOnMigrate(properties.isValidateOnMigrate())
                .load();
        log.info("Flyway 只迁移主库 [{}]，迁移目录为 {}", primaryName, properties.getLocations());
        return flyway;
    }

    /**
     * 从动态路由数据源中取出主库数据源。
     *
     * @param routingDataSource 动态路由数据源
     * @param primaryName       主库名称，来自 {@code forge.datasource.primary-name}
     * @return 主库数据源
     * @throws IllegalStateException 动态数据源类型不符或缺少主库时抛出
     */
    private DataSource resolvePrimaryDataSource(DataSource routingDataSource, String primaryName) {
        if (!(routingDataSource instanceof DynamicRoutingDataSource dynamicDataSource)) {
            throw new IllegalStateException(
                    "Flyway 需要 dynamic-datasource 的路由数据源，实际类型为 "
                            + routingDataSource.getClass().getName());
        }
        DataSource master = dynamicDataSource.getDataSource(primaryName);
        if (master == null) {
            throw new IllegalStateException(
                    "未找到名为 [" + primaryName + "] 的主库数据源，可用数据源为 "
                            + dynamicDataSource.getDataSources().keySet());
        }
        return master;
    }
}
