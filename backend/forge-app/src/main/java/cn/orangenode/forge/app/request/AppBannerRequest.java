package cn.orangenode.forge.app.request;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 首页轮播新增与修改入参。
 *
 * @param title       标题
 * @param imageFileId 图片文件 ID（先通过文件管理上传）
 * @param targetType  跳转类型 NONE、VIDEO、CATEGORY 或 URL（广告外链）
 * @param targetId    跳转影片或分类 ID，NONE 时为空
 * @param startAt     上线时间，可为空
 * @param endAt       下线时间，可为空
 * @param enabled     是否启用
 * @param sortNo      显示顺序
 * @param targetUrl   跳转类型为 URL 时的外部链接（http/https）
 */
public record AppBannerRequest(
        @NotBlank(message = "请输入标题") @Size(max = 64, message = "标题不能超过 64 个字符") String title,
        @NotBlank(message = "请上传图片") String imageFileId,
        @NotBlank(message = "请选择跳转类型")
        @Pattern(regexp = "NONE|VIDEO|CATEGORY|URL", message = "跳转类型只能是 NONE、VIDEO、CATEGORY 或 URL") String targetType,
        String targetId,
        Instant startAt,
        Instant endAt,
        @NotNull(message = "请选择是否启用") Boolean enabled,
        @NotNull(message = "请输入顺序") @Min(value = 0, message = "顺序不能为负数") Integer sortNo,
        @Size(max = 512, message = "外部链接不能超过 512 个字符") String targetUrl) {
}
