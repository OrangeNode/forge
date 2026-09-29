package cn.orangenode.forge.m2.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 多数据源验证。
 *
 * <p>使用两个内存数据库验证：默认走主库、{@code @DS} 按声明选库、两库数据互不可见、
 * 调用结束后数据源上下文清空、单库事务失败时回滚，以及未注册的数据源名称明确报错。</p>
 *
 * <p>第二数据源通过配置启用，验证第二库是可选能力：未配置时不注册，配置后按名称路由。</p>
 */
@SpringBootTest(properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_primary;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.secondary-datasource.enabled=true",
    "forge.secondary-datasource.url=jdbc:h2:mem:forge_secondary;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "forge.secondary-datasource.username=sa",
    "forge.secondary-datasource.password=",
    "forge.secondary-datasource.driver-class-name=org.h2.Driver",
    "forge.mybatis-plus.db-type=H2"
})
class MultiDataSourceIntegrationTest {

    /**
     * Redis 替身，避免该用例依赖真实 Redis。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 多数据源路由验证用例入口。
     */
    @Autowired
    private DataSourceProbeService probeService;

    /**
     * 验证未注册数据源名称的调用入口，用于确认严格匹配不回退主库。
     */
    @Autowired
    private UnknownDataSourceProbe unknownDataSourceProbe;

    /**
     * 每个用例前清空两个库的探针表并重新写入标记，避免用例之间相互影响。
     */
    @BeforeEach
    void prepareData() {
        DynamicDataSourceContextHolder.clear();
        probeService.createPrimaryTable();
        probeService.createSecondaryTable();
        probeService.clearPrimary();
        probeService.clearSecondary();
        probeService.insertPrimary("primary-only");
        probeService.insertSecondary("secondary-only");
    }

    /**
     * 验证不同 {@code @DS} 方法写入的数据分别落在各自的库中。
     */
    @Test
    @DisplayName("@DS 按声明路由到主库与第二库")
    void shouldRouteToDeclaredDataSource() {
        assertThat(probeService.selectPrimary()).containsExactly("primary-only");
        assertThat(probeService.selectSecondary()).containsExactly("secondary-only");
    }

    /**
     * 验证调用结束后数据源上下文被清空，不会污染同一线程的后续请求。
     */
    @Test
    @DisplayName("调用结束后数据源上下文清空")
    void shouldClearDataSourceContext() {
        probeService.countSecondary();

        assertThat(DynamicDataSourceContextHolder.peek()).isNull();
    }

    /**
     * 验证单库事务内抛出异常时数据全部回滚。
     */
    @Test
    @DisplayName("单库事务失败时全部回滚")
    void shouldRollbackSingleDatabaseTransaction() {
        assertThatThrownBy(() -> probeService.insertThenFail("rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback-probe");

        assertThat(probeService.selectPrimary()).containsExactly("primary-only");
    }

    /**
     * 验证使用未注册的数据源名称时明确失败，不回退主库继续执行。
     */
    @Test
    @DisplayName("未注册的数据源名称明确失败且不回退主库")
    void shouldFailForUnknownDataSourceName() {
        assertThatThrownBy(() -> unknownDataSourceProbe.selectFromUnknown())
                .isInstanceOf(RuntimeException.class);

        assertThat(probeService.selectPrimary()).containsExactly("primary-only");
    }
}
