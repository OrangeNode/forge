package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * SettingsResponse对外业务响应，主键使用字符串。
 */
public record SettingsResponse(List<String> matchFields, List<String> updateFields, boolean autoPublish,
        java.util.Map<String, String> missingPolicies) {
    /**
     * 构造原有规则，未配置字段按既有行为处理。
     */
    public SettingsResponse(List<String> matchFields, List<String> updateFields, boolean autoPublish) {
        this(matchFields, updateFields, autoPublish, java.util.Map.of());
    }
    /**
     * 兼容未包含缺失策略的历史规则快照。
     */
    public String missingPolicy(String field) {
        if (missingPolicies != null && missingPolicies.containsKey(field)) {
            return missingPolicies.get(field);
        }
        return matchFields.contains(field) || java.util.Set.of("name", "category", "playUrls").contains(field)
                ? "PENDING" : "ALLOW";
    }
}
