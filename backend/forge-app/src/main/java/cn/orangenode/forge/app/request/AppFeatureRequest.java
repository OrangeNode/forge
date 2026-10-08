package cn.orangenode.forge.app.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 功能开关修改入参。
 *
 * @param vipRequired      VIP 开关：开启时只有 VIP 能观看，关闭时登录即可观看
 * @param registrationOpen 是否开放注册
 * @param maxDevices       同一账号同时登录设备上限，下一次登录时生效
 * @param serviceText      客服文字，可为空
 * @param noticeText       公告文字，可为空
 */
public record AppFeatureRequest(
        @NotNull(message = "请选择 VIP 开关") Boolean vipRequired,
        @NotNull(message = "请选择是否开放注册") Boolean registrationOpen,
        @NotNull(message = "请输入同时登录台数") @Min(value = 1, message = "同时登录台数至少为 1")
        @Max(value = 20, message = "同时登录台数不能超过 20") Integer maxDevices,
        @Size(max = 512, message = "客服文字不能超过 512 个字符") String serviceText,
        @Size(max = 1024, message = "公告文字不能超过 1024 个字符") String noticeText) {
}
