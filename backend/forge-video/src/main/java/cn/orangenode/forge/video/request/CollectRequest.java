package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * CollectRequest业务入参，与数据库实体分离。
 */
public record CollectRequest(@NotBlank String sourceId, @NotBlank @Pattern(regexp = "DAY|WEEK|ALL") String rangeCode, String upstreamCategoryId) {
}
