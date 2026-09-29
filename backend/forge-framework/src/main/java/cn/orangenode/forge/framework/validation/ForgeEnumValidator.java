package cn.orangenode.forge.framework.validation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ForgeEnum} 的校验实现。
 *
 * <p>按枚举常量名匹配，忽略大小写以外的差异；被校验值为 {@code null} 或空白时视为合法，
 * 是否必填由其他约束注解决定，避免重复表达同一规则。</p>
 */
public class ForgeEnumValidator implements ConstraintValidator<ForgeEnum, String> {

    /**
     * 允许的枚举常量名集合。
     */
    private Set<String> allowedValues;

    /**
     * 读取注解上声明的枚举类型并缓存可选值。
     *
     * @param annotation 枚举校验注解
     */
    @Override
    public void initialize(ForgeEnum annotation) {
        this.allowedValues = Arrays.stream(annotation.value().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 判断取值是否为允许的枚举代码。
     *
     * @param value   被校验字符串
     * @param context 校验上下文，本实现不追加自定义消息
     * @return 空值或匹配的枚举常量名时返回 {@code true}
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return allowedValues.contains(value);
    }
}
