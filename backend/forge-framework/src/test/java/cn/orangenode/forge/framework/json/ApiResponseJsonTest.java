package cn.orangenode.forge.framework.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.orangenode.forge.core.response.ApiResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证统一响应体的对外 JSON 结构。
 *
 * <p>接口协议只允许 code、message、data、traceId 四个顶层字段；
 * 任何服务端便利方法都不能意外出现在响应体中。</p>
 */
class ApiResponseJsonTest {

    /**
     * 被测 JSON 映射器，与 {@link JacksonConfig} 的装配方式保持一致。
     */
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 验证成功响应只输出协议约定的四个字段。
     */
    @Test
    @DisplayName("成功响应只包含协议约定的四个字段")
    void shouldSerializeOnlyProtocolFields() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        JsonNode node = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("code", "message", "data", "traceId");
        assertThat(node.get("code").asInt()).isZero();
        assertThat(node.get("data").asString()).isEqualTo("payload");
        assertThat(node.get("traceId").asString()).hasSize(32);
    }

    /**
     * 验证失败响应不携带业务数据，且错误码保持数字语义。
     */
    @Test
    @DisplayName("失败响应 data 为 null 且 code 为非零数字")
    void shouldSerializeFailureWithoutData() {
        ApiResponse<Void> response = ApiResponse.failure(400, "参数错误");

        JsonNode node = jsonMapper.readTree(jsonMapper.writeValueAsString(response));

        assertThat(node.get("code").asInt()).isEqualTo(400);
        assertThat(node.get("data").isNull()).isTrue();
        assertThat(node.get("message").asString()).isEqualTo("参数错误");
        assertThat(node.propertyNames()).containsExactlyInAnyOrder("code", "message", "data", "traceId");
    }
}
