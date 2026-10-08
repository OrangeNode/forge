package cn.orangenode.forge.app.request;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 开屏新增与修改入参。
 *
 * @param name            名称，仅后台展示
 * @param imageFileId     封面图文件 ID（先通过文件管理上传）
 * @param durationSeconds 停留秒数
 * @param skippable       是否可跳过
 * @param startAt         生效开始时间，可为空
 * @param endAt           生效结束时间，可为空
 * @param enabled         是否启用
 * @param sortNo          优先顺序，越小越优先
 * @param targetType      点击跳转类型 NONE / VIDEO / URL，为空按 NONE
 * @param targetValue     跳转影片 ID 或外部链接（http/https）
 */
public record AppSplashRequest(
        @NotBlank(message = "请输入名称") @Size(max = 64, message = "名称不能超过 64 个字符") String name,
        @NotBlank(message = "请上传封面图") String imageFileId,
        @NotNull(message = "请输入停留秒数") @Min(value = 1, message = "停留秒数至少 1 秒")
        @Max(value = 30, message = "停留秒数不能超过 30 秒") Integer durationSeconds,
        @NotNull(message = "请选择是否可跳过") Boolean skippable,
        Instant startAt,
        Instant endAt,
        @NotNull(message = "请选择是否启用") Boolean enabled,
        @NotNull(message = "请输入顺序") @Min(value = 0, message = "顺序不能为负数") Integer sortNo,
        @Pattern(regexp = "NONE|VIDEO|URL", message = "跳转类型只能是 NONE、VIDEO 或 URL") String targetType,
        @Size(max = 512, message = "跳转目标不能超过 512 个字符") String targetValue) {
}
