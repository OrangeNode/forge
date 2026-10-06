package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotNull;

/**
 * 一键匹配分类时是否创建缺少的本地分类。
 */
public record AutoMappingRequest(@NotNull Boolean createMissing) {
}
