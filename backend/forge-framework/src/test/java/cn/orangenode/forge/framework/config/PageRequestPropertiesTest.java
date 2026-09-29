package cn.orangenode.forge.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.framework.web.TraceIdFilter;

/**
 * 在容器中验证配置项的真实绑定与生效方式。
 *
 * <p>这里刻意使用与 {@code application.yaml} 不同的取值，证明分页默认值、分页上限
 * 与追踪响应头名称都来自配置，而不是写死在 Java 代码里。</p>
 */
@SpringBootTest(
        classes = PageRequestPropertiesTest.TestConfig.class,
        properties = {
            "forge.page.default-page-num=2",
            "forge.page.default-page-size=50",
            "forge.page.max-size=80",
            "forge.web.trace-header-name=X-Forge-Trace"
        })
class PageRequestPropertiesTest {

    /**
     * 被测分页入参，由容器创建以获得配置注入。
     */
    @Autowired
    private PageRequest pageRequest;

    /**
     * 被测追踪过滤器，装配方式与运行期一致。
     */
    @Autowired
    private TraceIdFilter traceIdFilter;

    /**
     * 验证分页默认值与上限来自配置。
     */
    @Test
    @DisplayName("分页默认值与上限来自配置")
    void shouldBindPagePropertiesFromConfiguration() {
        assertThat(pageRequest.getPageNum()).isEqualTo(2);
        assertThat(pageRequest.getPageSize()).isEqualTo(50);
        assertThat(pageRequest.getConfiguredMaxPageSize()).isEqualTo(80);

        pageRequest.setPageSize(80);
        assertThat(pageRequest.validatePageSize()).isTrue();

        pageRequest.setPageSize(81);
        assertThat(pageRequest.validatePageSize()).isFalse();
    }

    /**
     * 验证追踪响应头名称来自配置，且请求属性同时写入。
     */
    @Test
    @DisplayName("追踪响应头名称来自配置")
    void shouldUseConfiguredTraceHeaderName() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader("X-Forge-Trace")).isNotBlank();
        assertThat(request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)).isNotNull();
    }

    /**
     * 测试用最小容器配置：只装配被测配置类与分页、追踪组件。
     */
    @Configuration
    @EnableConfigurationProperties({ForgePageProperties.class, ForgeWebProperties.class,
            ForgeDataSourceProperties.class})
    static class TestConfig {

        /**
         * 提供分页入参 Bean，使配置注入生效。
         *
         * @return 分页入参
         */
        @Bean
        PageRequest pageRequest() {
            return new PageRequest();
        }

        /**
         * 提供追踪过滤器 Bean，装配方式与 {@code WebTraceConfig} 一致。
         *
         * @param properties Web 与追踪配置
         * @return 追踪过滤器
         */
        @Bean
        TraceIdFilter traceIdFilter(ForgeWebProperties properties) {
            return new TraceIdFilter(properties.getTraceHeaderName());
        }
    }
}
