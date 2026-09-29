package cn.orangenode.forge.framework.json;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 序列化配置。
 *
 * <p>本项目的接口时间统一使用带时区的 ISO 8601 字符串，数据库时间统一存储 UTC。
 * Spring Boot 4 使用 Jackson 3（{@code tools.jackson}），日期时间默认即为 ISO 8601 字符串，
 * 因此此处不再注册 Jackson 2 时代的自定义序列化器，只提供集中定制的入口。</p>
 *
 * <p>后续阶段如需调整命名策略、未知字段处理或模块注册，统一在本类内补充，
 * 避免在各模块散落 {@code @JsonFormat} 之外的全局设置。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 构建与 Spring Boot 自动配置一致的 JSON 映射器。
     *
     * <p>使用 {@link JsonMapper#builder()} 装配，便于后续在本方法内集中追加模块与特性开关；
     * 当前保留 Jackson 3 的默认特性，不覆盖日期时间输出格式。</p>
     *
     * @return JSON 映射器
     */
    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }
}
