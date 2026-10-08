package cn.orangenode.forge.framework.member;

/**
 * App 功能开关快照。
 *
 * @param vipRequired      是否只有 VIP 能观看；关闭时登录即可观看
 * @param registrationOpen 是否开放注册
 * @param maxDevices       同一账号同时登录的设备上限
 */
public record AppFeatureSnapshot(boolean vipRequired, boolean registrationOpen, int maxDevices) {
}
