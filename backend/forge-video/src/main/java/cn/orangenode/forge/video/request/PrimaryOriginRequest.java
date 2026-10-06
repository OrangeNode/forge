package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;

/**
 * PrimaryOriginRequest业务入参，与数据库实体分离。
 */
public record PrimaryOriginRequest(@NotBlank String originId) {
}
