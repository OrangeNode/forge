package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotNull;

/**
 * StatusRequest业务入参，与数据库实体分离。
 */
public record StatusRequest(@NotNull Boolean enabled) {
}
