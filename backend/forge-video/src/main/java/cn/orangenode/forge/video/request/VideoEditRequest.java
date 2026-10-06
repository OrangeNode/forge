package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * VideoEditRequest业务入参，与数据库实体分离。
 */
public record VideoEditRequest(@NotBlank @Size(max = 255) String name, @NotBlank String categoryId, @Min(1) @Max(9999) Integer year, @Size(max = 255) String area, @Size(max = 255) String language, @Size(max = 2048) String director, @Size(max = 4096) String actors, @Size(max = 4096) String posterUrl, @Size(max = 65535) String description, @Size(max = 512) String remarks, @Min(0) Integer declaredTotal, Boolean ended, Boolean metadataLocked) {
}
