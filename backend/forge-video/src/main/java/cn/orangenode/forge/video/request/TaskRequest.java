package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * TaskRequest业务入参，与数据库实体分离。
 */
public record TaskRequest(@NotBlank @Size(max = 128) String name, @NotBlank String sourceId, @NotBlank @Pattern(regexp = "DAY|WEEK|ALL") String rangeCode, @Size(max = 128) String upstreamCategoryId, @NotBlank @Pattern(regexp = "INTERVAL|DAILY|WEEKLY") String frequency, @Min(1) @Max(8760) Integer intervalHours, @Min(1) @Max(7) Integer weekday, @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String timeOfDay, @NotBlank String zoneId, @NotNull Boolean enabled) {
}
