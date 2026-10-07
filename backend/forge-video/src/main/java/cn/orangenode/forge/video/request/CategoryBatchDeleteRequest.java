package cn.orangenode.forge.video.request;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 批量删除分类入参，只接受管理员明确勾选的分类编号。
 */
public record CategoryBatchDeleteRequest(@NotEmpty List<@NotBlank String> ids) {
}
