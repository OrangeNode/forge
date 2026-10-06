package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;

/**
 * MappingRequest业务入参，与数据库实体分离。
 */
public record MappingRequest(@NotBlank String upstreamId, String categoryId) {
}
