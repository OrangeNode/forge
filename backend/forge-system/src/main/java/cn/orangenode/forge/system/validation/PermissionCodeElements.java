package cn.orangenode.forge.system.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 权限标识集合校验注解。
 *
 * <p>用于菜单新增与修改入参的权限标识列表：集合本身允许为空（目录节点不声明权限），
 * 但每一个元素都必须是合法的 {@code 模块:资源:动作} 标识。</p>
 *
 * <p>把它放在入参记录上而不是在服务里抛异常，是为了让格式错误与其它字段校验走同一条出口：
 * 响应统一返回 HTTP 200 + body.code=400，并在 {@code data.fieldErrors} 里指出 {@code permCodes}，
 * 前端表单因此能把提示显示在权限编辑区域上。</p>
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PermissionCodeElementsValidator.class)
public @interface PermissionCodeElements {

    /**
     * 校验失败时的默认提示。
     *
     * @return 提示文案
     */
    String message() default "权限标识格式为 模块:资源:动作，只允许小写字母、数字、下划线与连字符";

    /**
     * 校验分组，遵守 Bean Validation 约定。
     *
     * @return 分组类型
     */
    Class<?>[] groups() default {};

    /**
     * 附加负载，遵守 Bean Validation 约定。
     *
     * @return 负载类型
     */
    Class<? extends Payload>[] payload() default {};
}
