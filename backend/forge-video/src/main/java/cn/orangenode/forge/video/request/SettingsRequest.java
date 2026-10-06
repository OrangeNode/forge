package cn.orangenode.forge.video.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * SettingsRequest业务入参，与数据库实体分离。
 */
public record SettingsRequest(@NotEmpty List<@NotBlank String> matchFields, @NotNull List<@NotBlank String> updateFields,
        @NotNull Boolean autoPublish, java.util.Map<@NotBlank String, @NotBlank String> missingPolicies) {
    /**
     * 兼容仅维护去重和更新规则的调用方。
     */
    public SettingsRequest(List<String> matchFields, List<String> updateFields, Boolean autoPublish) {
        this(matchFields, updateFields, autoPublish, null);
    }
}
