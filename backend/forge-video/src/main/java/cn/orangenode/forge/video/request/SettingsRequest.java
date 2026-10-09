package cn.orangenode.forge.video.request;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * SettingsRequest业务入参，与数据库实体分离。
 *
 * @param maxConcurrency 同时运行的最大采集数，为空保持原值；上限见配置 {@code forge.video.max-concurrency-limit}
 */
public record SettingsRequest(@NotEmpty List<@NotBlank String> matchFields, @NotNull List<@NotBlank String> updateFields,
        @NotNull Boolean autoPublish, java.util.Map<@NotBlank String, @NotBlank String> missingPolicies,
        @Min(value = 1, message = "最大并发采集数至少为 1") Integer maxConcurrency) {
    /**
     * 兼容仅维护去重和更新规则的调用方。
     */
    public SettingsRequest(List<String> matchFields, List<String> updateFields, Boolean autoPublish) {
        this(matchFields, updateFields, autoPublish, null, null);
    }

    /**
     * 兼容不修改最大并发采集数的调用方。
     */
    public SettingsRequest(List<String> matchFields, List<String> updateFields, Boolean autoPublish,
            java.util.Map<String, String> missingPolicies) {
        this(matchFields, updateFields, autoPublish, missingPolicies, null);
    }
}
