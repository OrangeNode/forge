package cn.orangenode.forge.framework.member;

/**
 * App 功能开关端口。
 *
 * <p>由拥有 App 设置的业务模块实现，普通用户与视频模块只通过本端口读取开关，
 * 业务模块之间因此不互相依赖。未装配实现时使用 {@code forge.member.auth} 中的默认值。</p>
 */
public interface AppFeatureSwitches {

    /**
     * 读取当前开关快照。
     *
     * @return 开关快照
     */
    AppFeatureSnapshot current();
}
