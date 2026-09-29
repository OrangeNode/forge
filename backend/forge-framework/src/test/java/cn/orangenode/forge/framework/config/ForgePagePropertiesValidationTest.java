package cn.orangenode.forge.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * 验证配置类上的约束注解确实会拒绝越界取值。
 *
 * <p>分页上限属于可配置项，因此约束写在配置类字段上，由容器在装配配置后执行。
 * 本用例用与运行期相同的校验器直接断言边界行为，证明注解不是摆设；
 * 真实启动路径下越界值会命中同一校验器并导致启动失败。</p>
 */
class ForgePagePropertiesValidationTest {

    /**
     * 校验器，与 Spring Boot 绑定配置时使用的校验能力一致。
     */
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 构造指定取值的分页配置。
     *
     * @param defaultPageNum  默认页码
     * @param defaultPageSize 默认每页条数
     * @param maxPageSize     每页条数上限
     * @return 分页配置
     */
    private ForgePageProperties properties(int defaultPageNum, int defaultPageSize, int maxPageSize) {
        ForgePageProperties properties = new ForgePageProperties();
        properties.setDefaultPageNum(defaultPageNum);
        properties.setDefaultPageSize(defaultPageSize);
        properties.setMaxPageSize(maxPageSize);
        return properties;
    }

    /**
     * 验证上限在允许范围内时没有违规。
     */
    @Test
    @DisplayName("上限在允许范围内时无违规")
    void shouldAcceptValidMaxPageSize() {
        ForgePageProperties properties = properties(1, 20, 100);

        assertThat(properties.getMaxPageSize()).isEqualTo(100);
        assertThat(validator.validate(properties)).isEmpty();
    }

    /**
     * 验证上限越界时校验器报出违规，并指出字段名。
     */
    @Test
    @DisplayName("上限越界时校验器报出违规")
    void shouldRejectOutOfRangeMaxPageSize() {
        ForgePageProperties properties = properties(1, 20, 5000);

        Set<ConstraintViolation<ForgePageProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("maxPageSize");
    }

    /**
     * 验证上限小于 1 时同样被拒绝。
     */
    @Test
    @DisplayName("上限小于 1 时校验器报出违规")
    void shouldRejectMaxPageSizeBelowOne() {
        assertThat(validator.validate(properties(1, 20, 0))).isNotEmpty();
    }

    /**
     * 验证默认页码越界时被拒绝。
     */
    @Test
    @DisplayName("默认页码越界时校验器报出违规")
    void shouldRejectInvalidDefaultPageNum() {
        assertThat(validator.validate(properties(0, 20, 100))).isNotEmpty();
    }

    /**
     * 验证默认每页条数越界时被拒绝。
     */
    @Test
    @DisplayName("默认每页条数越界时校验器报出违规")
    void shouldRejectInvalidDefaultPageSize() {
        assertThat(validator.validate(properties(1, 0, 100))).isNotEmpty();
    }
}
