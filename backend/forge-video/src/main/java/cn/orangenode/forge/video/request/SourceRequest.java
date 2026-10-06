package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SourceRequest业务入参，与数据库实体分离。
 */
public record SourceRequest(@NotBlank @Size(max = 64) String name, @NotBlank @Size(max = 2048) String apiUrl, @NotNull Boolean enabled, @NotBlank String sourceZone) {
}
