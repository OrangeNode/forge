package cn.orangenode.forge.framework.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记操作对象 ID 参数。
 *
 * <p>与 {@link AuditOperation} 配合使用：被标注的参数会作为审计记录中的对象 ID。
 * 只标注一个参数，且应当是对外字符串形式的业务 ID。</p>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditResourceId {
}
