package cn.orangenode.forge.video.request;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 对勾选编号或筛选后的全部记录执行批量操作。
 */
public record CollectionBatchRequest(@NotBlank @Pattern(regexp = "SELECTED|ALL") String scope,
        List<@NotBlank String> ids, String status, String runId,
        @NotBlank @Pattern(regexp = "COLLECT|STOP|DELETE") String action) {
}
