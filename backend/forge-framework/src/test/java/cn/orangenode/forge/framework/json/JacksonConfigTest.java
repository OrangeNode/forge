package cn.orangenode.forge.framework.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * 验证接口时间的 JSON 输出格式。
 *
 * <p>数据库与业务层统一使用 UTC，接口必须输出带时区的 ISO 8601 字符串；
 * 该行为依赖 Jackson 3 的默认设置，因此用测试固定结论，避免后续升级静默改变协议。</p>
 */
class JacksonConfigTest {

    /**
     * 被测 JSON 映射器，与 {@link JacksonConfig} 的装配方式保持一致。
     */
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 验证 Instant 序列化为以 Z 结尾的 ISO 8601 字符串。
     *
     * @throws Exception 序列化失败时由测试框架报告
     */
    @Test
    @DisplayName("Instant 输出为 UTC 的 ISO 8601 字符串")
    void shouldSerializeInstantAsIso8601Utc() throws Exception {
        Instant value = Instant.parse("2026-09-14T08:30:00Z");

        String json = jsonMapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"2026-09-14T08:30:00Z\"");
    }
}
