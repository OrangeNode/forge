package cn.orangenode.forge.framework.member;

import java.time.Instant;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 观看授权：普通用户登录后，VIP 开关开启时还要求 VIP 未过期。
 *
 * <p>VIP 状态来自本次请求认证时实时读取的账号，后台开通或延长后下一次请求即生效。</p>
 */
@Component
public class PlaybackAccessGuard {

    /**
     * 功能开关读取入口。
     */
    private final AppFeatureResolver features;

    /**
     * 构造观看授权组件。
     *
     * @param features 功能开关读取入口
     */
    public PlaybackAccessGuard(AppFeatureResolver features) {
        this.features = features;
    }

    /**
     * 校验当前普通用户可以获取播放地址，未登录返回 401，非 VIP 返回 403。
     */
    public void assertCanPlay() {
        AuthenticatedMember member = CurrentMember.require();
        if (features.current().vipRequired() && !member.vipActiveAt(Instant.now())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "开通 VIP 后才能观看");
        }
    }
}
