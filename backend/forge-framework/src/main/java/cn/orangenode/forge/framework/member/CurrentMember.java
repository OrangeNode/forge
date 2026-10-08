package cn.orangenode.forge.framework.member;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 当前普通用户读取入口。
 *
 * <p>当前用户只来自安全上下文，不接受请求参数指定；管理员主体不会被当成普通用户。</p>
 */
public final class CurrentMember {

    /**
     * 工具类不允许实例化。
     */
    private CurrentMember() {
    }

    /**
     * 读取当前普通用户。
     *
     * @return 当前普通用户，未登录时为空
     */
    public static Optional<AuthenticatedMember> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedMember member) {
            return Optional.of(member);
        }
        return Optional.empty();
    }

    /**
     * 读取当前普通用户，未登录时返回 body.code=401。
     *
     * @return 当前普通用户
     */
    public static AuthenticatedMember require() {
        return find().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"));
    }

    /**
     * 读取当前请求携带的令牌明文。
     *
     * @return 令牌，未登录时为空
     */
    public static Optional<String> token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedMember
                && authentication.getCredentials() instanceof String token) {
            return Optional.of(token);
        }
        return Optional.empty();
    }
}
