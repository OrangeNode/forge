package cn.orangenode.forge.framework.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * 验证分页入参的默认值、边界与偏移量计算。
 *
 * <p>默认值与上限来自配置 {@code forge.page}；本用例直接设置这些配置值，
 * 覆盖默认取值、硬上限校验与按配置的上限校验。配置绑定本身由
 * {@code PageRequestPropertiesTest} 在容器中验证。</p>
 */
class PageRequestTest {

    /**
     * 校验器，用于断言类型层面的约束注解。
     */
    private final Validator validator;

    /**
     * 初始化基于默认提供者的校验器。
     */
    PageRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    /**
     * 构造使用默认配置值的分页入参。
     *
     * @return 分页入参
     */
    private PageRequest newRequest() {
        PageRequest request = new PageRequest();
        request.setDefaultPageNum(1);
        request.setDefaultPageSize(20);
        request.setConfiguredMaxPageSize(100);
        return request;
    }

    /**
     * 验证未设置分页参数时使用配置的默认页码与默认条数。
     */
    @Test
    @DisplayName("未设置分页参数时使用配置的默认值")
    void shouldUseConfiguredDefaults() {
        PageRequest request = newRequest();

        assertThat(request.getPageNum()).isEqualTo(1);
        assertThat(request.getPageSize()).isEqualTo(20);
        assertThat(request.getOffset()).isZero();
        assertThat(request.validatePageSize()).isTrue();
        assertThat(validator.validate(request)).isEmpty();
    }

    /**
     * 验证默认值改为其他配置时立即生效。
     */
    @Test
    @DisplayName("默认值随配置变化")
    void shouldFollowConfiguredDefaults() {
        PageRequest request = new PageRequest();
        request.setDefaultPageNum(2);
        request.setDefaultPageSize(50);
        request.setConfiguredMaxPageSize(50);

        assertThat(request.getPageNum()).isEqualTo(2);
        assertThat(request.getPageSize()).isEqualTo(50);
        assertThat(request.getOffset()).isEqualTo(50L);
    }

    /**
     * 验证偏移量按页码与每页条数计算。
     */
    @Test
    @DisplayName("偏移量按页码与每页条数计算")
    void shouldCalculateOffset() {
        PageRequest request = newRequest();
        request.setPageNum(3);
        request.setPageSize(20);

        assertThat(request.getOffset()).isEqualTo(40L);
    }

    /**
     * 验证页码较大时偏移量不因整型运算溢出。
     */
    @Test
    @DisplayName("页码较大时偏移量不溢出")
    void shouldNotOverflowOffsetForLargePageNum() {
        PageRequest request = newRequest();
        request.setPageNum(Integer.MAX_VALUE);
        request.setPageSize(100);

        assertThat(request.getOffset()).isEqualTo((long) (Integer.MAX_VALUE - 1) * 100);
    }

    /**
     * 验证页码小于 1 时被约束注解拒绝。
     */
    @Test
    @DisplayName("页码小于 1 时校验失败")
    void shouldRejectPageNumBelowOne() {
        PageRequest request = newRequest();
        request.setPageNum(0);

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("pageNum");
    }

    /**
     * 验证每页条数超过硬上限时被约束注解拒绝。
     */
    @Test
    @DisplayName("每页条数超过硬上限时校验失败")
    void shouldRejectPageSizeAboveAbsoluteLimit() {
        PageRequest request = newRequest();
        request.setPageSize(1001);

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("pageSize");
    }

    /**
     * 验证每页条数超过配置上限但未超过硬上限时，由配置校验拒绝。
     */
    @Test
    @DisplayName("每页条数超过配置上限时按配置拒绝")
    void shouldRejectPageSizeAboveConfiguredLimit() {
        PageRequest request = newRequest();
        request.setConfiguredMaxPageSize(50);
        request.setPageSize(51);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.validatePageSize()).isFalse();
    }

    /**
     * 验证每页条数小于 1 时被约束注解拒绝。
     */
    @Test
    @DisplayName("每页条数小于 1 时校验失败")
    void shouldRejectPageSizeBelowOne() {
        PageRequest request = newRequest();
        request.setPageSize(0);

        Set<ConstraintViolation<PageRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(request.validatePageSize()).isFalse();
    }
}
