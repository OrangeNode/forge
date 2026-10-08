package cn.orangenode.forge.app.request;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 首页推荐位新增与修改入参。
 *
 * @param title      推荐行标题
 * @param sourceType 数据来源 CATEGORY（按分类自动取最近更新）或 MANUAL（手动挑片）
 * @param categoryId 分类 ID，CATEGORY 时必填
 * @param videoIds   影片 ID 列表，MANUAL 时必填并按顺序展示
 * @param itemLimit  最多展示条数，上限来自 forge.app.section-max-items
 * @param enabled    是否启用
 * @param sortNo     显示顺序
 */
public record AppSectionRequest(
        @NotBlank(message = "请输入标题") @Size(max = 64, message = "标题不能超过 64 个字符") String title,
        @NotBlank(message = "请选择数据来源")
        @Pattern(regexp = "CATEGORY|MANUAL", message = "数据来源只能是 CATEGORY 或 MANUAL") String sourceType,
        String categoryId,
        @Size(max = 100, message = "手动挑片不能超过 100 部") List<String> videoIds,
        @NotNull(message = "请输入展示条数") @Min(value = 1, message = "展示条数至少为 1") Integer itemLimit,
        @NotNull(message = "请选择是否启用") Boolean enabled,
        @NotNull(message = "请输入顺序") @Min(value = 0, message = "顺序不能为负数") Integer sortNo) {
}
