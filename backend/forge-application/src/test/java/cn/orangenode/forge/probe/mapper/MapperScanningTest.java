package cn.orangenode.forge.probe.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 数据源装配与 Mapper 扫描的自动化验证。
 *
 * <p>使用内存数据库装配真实的 dynamic-datasource 路由数据源，证明 {@code @MapperScan} 能扫到
 * 各模块 {@code mapper} 包下的接口并复用同一数据源执行 SQL；Redis 用替身替换，
 * 使该用例不依赖任何外部中间件。</p>
 *
 * <p>本用例是修复“Mapper 扫描未命中”后的回归保护：当时只用临时探针验证后删除，
 * 没有留下自动检查。注意映射器必须是被扫描包下的顶层接口，嵌套接口不会被注册。</p>
 */
@SpringBootTest(properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_mapper;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    // M3 起初始管理员引导默认开启；本用例不建系统表，显式关闭引导。
    "forge.system.bootstrap.enabled=false"
})
class MapperScanningTest {

    /**
     * Redis 替身，避免该用例依赖真实 Redis；本用例不验证 Redis 行为。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 被测映射器，由应用启动时的 MapperScan 注册。
     */
    @Autowired
    private ProbeQueryMapper probeQueryMapper;

    /**
     * 验证带 {@code @Mapper} 注解的接口被注册为可注入的 Bean。
     */
    @Test
    @DisplayName("mapper 包下的 @Mapper 接口被扫描并注册")
    void shouldRegisterMapperAnnotatedInterface() {
        assertThat(probeQueryMapper).isNotNull();
    }

    /**
     * 验证映射器可通过 dynamic-datasource 的主库连接执行 SQL。
     */
    @Test
    @DisplayName("映射器可通过主库连接执行查询")
    void shouldExecuteQueryThroughPrimaryDataSource() {
        assertThat(probeQueryMapper.selectOne()).isEqualTo(1);
    }
}
