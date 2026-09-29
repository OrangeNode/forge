package cn.orangenode.forge.framework.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.orangenode.forge.framework.config.ForgeWebProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 追踪编号过滤器装配。
 *
 * <p>是否启用追踪与响应头名称来自 {@code forge.web} 配置，便于按环境关闭或改名；
 * 关闭时请求属性与响应头都不写入，统一响应体仍可自行生成追踪编号。</p>
 */
@Slf4j
@Configuration
public class WebTraceConfig {

    /**
     * 按配置装配追踪编号过滤器。
     *
     * @param properties Web 与追踪配置
     * @return 追踪编号过滤器
     */
    @Bean
    @ConditionalOnProperty(prefix = "forge.web", name = "trace-enabled", havingValue = "true", matchIfMissing = true)
    public TraceIdFilter traceIdFilter(ForgeWebProperties properties) {
        log.info("追踪编号过滤器已启用，响应头名称为 [{}]", properties.getTraceHeaderName());
        return new TraceIdFilter(properties.getTraceHeaderName());
    }
}
