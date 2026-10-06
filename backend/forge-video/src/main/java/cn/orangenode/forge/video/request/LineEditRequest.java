package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * LineEditRequest业务入参，与数据库实体分离。
 */
public record LineEditRequest(@NotBlank @Size(max = 128) String name, @NotNull Integer sortNo, @NotNull Boolean enabled) {
}
