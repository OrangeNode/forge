package cn.orangenode.forge.app.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * App 关闭公告时上报已看过的版本。
 *
 * @param revision 弹出时公告的内容版本
 */
public record AppAnnouncementReadRequest(
        @NotNull(message = "请提供公告版本") @Min(value = 1, message = "公告版本至少为 1") Integer revision) {
}
