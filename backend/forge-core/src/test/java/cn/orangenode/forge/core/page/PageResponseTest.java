package cn.orangenode.forge.core.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 验证框架无关的分页结果的规范化行为。
 *
 * <p>空页必须返回空数组与真实总数，不返回 {@code null}，避免调用方对空结果做额外判空分支。</p>
 */
class PageResponseTest {

    /**
     * 验证构造时把 {@code null} 记录列表规范化为不可变空集合。
     */
    @Test
    @DisplayName("空记录规范化为空数组")
    void shouldNormalizeNullRecords() {
        PageResponse<String> response = new PageResponse<>(null, -5L, 1, 20);

        assertThat(response.records()).isEmpty();
        assertThat(response.total()).isZero();
    }

    /**
     * 验证记录列表被复制为不可变集合，避免外部修改影响响应内容。
     */
    @Test
    @DisplayName("记录列表为不可变副本")
    void shouldCopyRecords() {
        List<String> source = new java.util.ArrayList<>(List.of("a"));

        PageResponse<String> response = new PageResponse<>(source, 1L, 1, 20);

        assertThat(response.records()).containsExactly("a");
        assertThat(response.records()).isNotSameAs(source);
    }

    /**
     * 验证按页码与每页条数构造空分页结果。
     */
    @Test
    @DisplayName("构造空分页结果保留分页元数据")
    void shouldBuildEmptyPage() {
        PageResponse<String> response = PageResponse.empty(3, 10);

        assertThat(response.records()).isEmpty();
        assertThat(response.total()).isZero();
        assertThat(response.pageNum()).isEqualTo(3);
        assertThat(response.pageSize()).isEqualTo(10);
    }
}
