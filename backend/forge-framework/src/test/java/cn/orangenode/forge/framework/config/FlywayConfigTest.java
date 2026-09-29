package cn.orangenode.forge.framework.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;

/**
 * 验证 Flyway 只针对配置指定的主库装配，并且缺少主库时明确失败。
 *
 * <p>M1 实测中 Spring Boot 的 Flyway 自动配置在 dynamic-datasource 下不会生效，
 * 迁移完全不执行且不创建历史表；因此这里固定三条关键行为：主库名称来自配置、
 * 面向动态数据源时能成功装配、数据源不符合预期或缺少主库时抛错而不是静默跳过迁移。</p>
 */
class FlywayConfigTest {

    /**
     * 被测配置。
     */
    private final FlywayConfig flywayConfig = new FlywayConfig();

    /**
     * 构造使用默认主库名称的数据源配置。
     *
     * @return 数据源配置
     */
    private ForgeDataSourceProperties defaultDataSourceProperties() {
        ForgeDataSourceProperties properties = new ForgeDataSourceProperties();
        properties.setPrimaryName("master");
        return properties;
    }

    /**
     * 验证能从动态路由数据源取到配置指定的主库并成功装配 Flyway。
     */
    @Test
    @DisplayName("从配置指定的主库装配 Flyway")
    void shouldBuildFlywayFromConfiguredPrimaryDataSource() {
        DataSource master = mock(DataSource.class);
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource("master")).thenReturn(master);

        Flyway flyway = flywayConfig.flyway(routing, new FlywayProperties(), defaultDataSourceProperties());

        assertThat(flyway).isNotNull();
    }

    /**
     * 验证主库名称改为其他配置值时按新名称取数据源。
     */
    @Test
    @DisplayName("主库名称来自配置，可改为其他名称")
    void shouldUseConfiguredPrimaryName() {
        DataSource primary = mock(DataSource.class);
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource("primary-db")).thenReturn(primary);
        ForgeDataSourceProperties dataSourceProperties = new ForgeDataSourceProperties();
        dataSourceProperties.setPrimaryName("primary-db");

        Flyway flyway = flywayConfig.flyway(routing, new FlywayProperties(), dataSourceProperties);

        assertThat(flyway).isNotNull();
    }

    /**
     * 验证数据源不是 dynamic-datasource 的路由数据源时立即失败。
     */
    @Test
    @DisplayName("数据源类型不符时明确失败")
    void shouldFailWhenDataSourceIsNotDynamicRouting() {
        DataSource plain = mock(DataSource.class);

        assertThatThrownBy(() -> flywayConfig.flyway(plain, new FlywayProperties(),
                defaultDataSourceProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dynamic-datasource");
    }

    /**
     * 验证缺少主库时失败，并给出实际可用的数据源名称。
     */
    @Test
    @DisplayName("缺少主库时明确失败")
    void shouldFailWhenPrimaryDataSourceMissing() {
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource("master")).thenReturn(null);
        when(routing.getDataSources()).thenReturn(Map.of("secondary", mock(DataSource.class)));

        assertThatThrownBy(() -> flywayConfig.flyway(routing, new FlywayProperties(),
                defaultDataSourceProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master")
                .hasMessageContaining("secondary");
    }

    /**
     * 验证迁移目录取自 spring.flyway 配置，而不是写死在代码中。
     */
    @Test
    @DisplayName("迁移目录来自配置")
    void shouldUseConfiguredLocations() {
        DataSource master = mock(DataSource.class);
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource("master")).thenReturn(master);
        FlywayProperties properties = new FlywayProperties();
        properties.setLocations(List.of("classpath:db/migration"));

        Flyway flyway = flywayConfig.flyway(routing, properties, defaultDataSourceProperties());

        assertThat(flyway.getConfiguration().getLocations())
                .extracting(location -> location.getDescriptor())
                .containsExactly("classpath:db/migration");
    }
}
