package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * TaskRequest业务入参，与数据库实体分离。
 */
public record TaskRequest(@NotBlank @Size(max = 128) String name, @NotBlank String sourceId, @NotBlank @Pattern(regexp = "DAY|WEEK|ALL") String rangeCode, @Size(max = 128) String upstreamCategoryId, @NotBlank @Size(max = 128) String cronExpression, @NotNull Boolean enabled) {
}
