package cn.orangenode.forge.app.request;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 公告新增与修改入参。
 *
 * @param title       标题
 * @param contentType 内容格式 TEXT 纯文本 / HTML
 * @param content     内容；HTML 保存前按白名单过滤
 * @param actionType  “去了解”按钮 NONE 不显示 / VIDEO 影片 / URL 外部链接，为空按 NONE
 * @param actionValue 跳转影片 ID 或外部链接（http/https）
 * @param startAt     生效开始时间，可为空
 * @param endAt       生效结束时间，可为空
 * @param enabled     是否启用
 * @param sortNo      优先级，越小越优先
 */
public record AppAnnouncementRequest(
        @NotBlank(message = "请输入标题") @Size(max = 64, message = "标题不能超过 64 个字符") String title,
        @NotNull(message = "请选择内容格式") @Pattern(regexp = "TEXT|HTML", message = "内容格式只能是 TEXT 或 HTML")
        String contentType,
        @NotBlank(message = "请输入内容") @Size(max = 20000, message = "内容不能超过 20000 个字符") String content,
        @Pattern(regexp = "NONE|VIDEO|URL", message = "按钮类型只能是 NONE、VIDEO 或 URL") String actionType,
        @Size(max = 512, message = "跳转目标不能超过 512 个字符") String actionValue,
        Instant startAt,
        Instant endAt,
        @NotNull(message = "请选择是否启用") Boolean enabled,
        @NotNull(message = "请输入优先级") @Min(value = 0, message = "优先级不能为负数") Integer sortNo) {
}
