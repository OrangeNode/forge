package cn.orangenode.forge.system.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.security.AuthenticatedAdmin;

/**
 * 当前操作者解析。
 *
 * <p>审计字段（创建者、更新者）与关系表操作者只从安全上下文取当前管理员 ID，
 * 不接受请求体或查询参数指定身份：后台传来的字段不能替代认证上下文。</p>
 *
 * <p>没有认证主体时返回 {@code null}，由数据库把操作者列写成空值；
 * 初始化引导等非请求流程也走同一取值方式，不假造管理员身份。</p>
 */
@Component
public class SystemCurrentAdmin {

    /**
     * 读取当前认证管理员的 ID。
     *
     * @return 当前管理员 ID，匿名或无认证主体时返回 {@code null}
     */
    public Long adminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin) {
            return admin.id();
        }
        return null;
    }
}
