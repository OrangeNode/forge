package cn.orangenode.forge.system.validation;

import java.util.List;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import cn.orangenode.forge.system.support.PermissionCodeFormat;

/**
 * 权限标识集合校验器。
 *
 * <p>逐个元素校验格式：空白元素直接视为非法，避免出现“看起来没填但实际提交了空串”的情况。
 * 去重与“是否已被其他菜单占用”属于业务规则，由服务层在处理时判断，不在这里做。</p>
 */
public class PermissionCodeElementsValidator implements ConstraintValidator<PermissionCodeElements, List<String>> {

    /**
     * 判断权限标识集合是否全部合法。
     *
     * @param value   权限标识集合，允许为 {@code null} 表示未提交
     * @param context 校验上下文
     * @return 全部元素合法或集合为空时返回 {@code true}
     */
    @Override
    public boolean isValid(List<String> value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (String code : value) {
            if (!PermissionCodeFormat.isValid(PermissionCodeFormat.normalize(code))) {
                return false;
            }
        }
        return true;
    }
}
