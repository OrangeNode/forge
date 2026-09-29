package cn.orangenode.forge.framework.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。
 *
 * <p>标注在写操作的 Controller 方法上，由 framework 的切面记录操作者、动作、对象、结果 code 与追踪编号，
 * 业务代码不写审计逻辑。查询与只读方法不加该注解，避免制造无意义的审计噪声。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {

    /**
     * 动作代码，建议与权限代码一致，例如 {@code system:admin:create}。
     *
     * @return 动作代码
     */
    String action();

    /**
     * 对象类型，例如 {@code admin}、{@code storage-config}。
     *
     * @return 对象类型，未声明时为空
     */
    String resourceType() default "";
}
