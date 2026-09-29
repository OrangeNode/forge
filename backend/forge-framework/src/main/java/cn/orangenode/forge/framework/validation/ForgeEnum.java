package cn.orangenode.forge.framework.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 校验字符串取值是否为指定枚举的稳定代码。
 *
 * <p>接口规范要求枚举使用明确、稳定的代码，不返回依赖排序位置的序号。
 * 参数需要表达“枚举代码”时使用本注解，避免各业务模块各写一套字符串判断，
 * 也避免依赖 {@code valueOf} 抛出 {@code IllegalArgumentException} 后由异常处理器兜底。</p>
 *
 * <p>校验只接受枚举常量名，空值视为合法，必填由 {@code @NotBlank} 等注解表达。</p>
 */
@Documented
@Constraint(validatedBy = ForgeEnumValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ForgeEnum {

    /**
     * 校验失败时的中文提示。
     *
     * @return 默认提示
     */
    String message() default "取值不在允许的范围内";

    /**
     * 校验分组，按用例复用时使用。
     *
     * @return 分组类型
     */
    Class<?>[] groups() default {};

    /**
     * 校验负载，供框架扩展使用。
     *
     * @return 负载类型
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 允许取值的枚举类型。
     *
     * @return 枚举类型
     */
    Class<? extends Enum<?>> value();
}
