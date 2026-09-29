package cn.orangenode.forge.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 维护任务调度装配。
 *
 * <p>只启用 Spring 自带的调度能力，供临时文件清理与审计日志清理等必要维护任务使用；
 * 不引入通用调度平台、任务编排或分布式调度组件。</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
