package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PendingResolveRequest业务入参，与数据库实体分离。
 */
public record PendingResolveRequest(@NotBlank @Pattern(regexp = "BIND|CREATE|IGNORE") String action, String videoId, String categoryId) {
}
