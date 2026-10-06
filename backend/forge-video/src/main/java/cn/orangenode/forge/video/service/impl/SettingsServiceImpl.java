package cn.orangenode.forge.video.service.impl;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.entity.SettingsEntity;
import cn.orangenode.forge.video.request.SettingsRequest;
import cn.orangenode.forge.video.response.SettingsResponse;
import cn.orangenode.forge.video.service.SettingsService;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * 采集设置只改变未来运行，运行时保存规则快照。
 */
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {
    private final VideoStore store;
    private final tools.jackson.databind.json.JsonMapper json;
    private static final Set<String> MATCH = Set.of("name", "year", "category", "director", "area", "language", "actors");
    private static final Set<String> UPDATE = Set.of("name", "category", "year", "area", "language", "director",
            "actors", "posterUrl", "description", "remarks", "declaredTotal", "ended");

    /**
     * 读取迁移创建的唯一设置行。
     */
    @Override
    public SettingsResponse get() {
        SettingsEntity e = store.settings.selectById(1L);
        if (e == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "视频采集设置尚未初始化");
        }
        return new SettingsResponse(VideoValues.fields(e.getMatchFields()), VideoValues.fields(e.getUpdateFields()),
                Boolean.TRUE.equals(e.getAutoPublish()), e.getMissingPolicies() == null ? java.util.Map.of()
                        : json.readValue(e.getMissingPolicies(), new tools.jackson.core.type.TypeReference<java.util.Map<String, String>>() { }));
    }

    /**
     * 名称必选，其余条件采用且关系。
     */
    @Override
    @Transactional
    public SettingsResponse save(SettingsRequest request) {
        if (!request.matchFields().contains("name") || !MATCH.containsAll(request.matchFields())
                || !UPDATE.containsAll(request.updateFields())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采集条件不合法，名称必须参与去重");
        }
        SettingsEntity e = store.settings.selectById(1L);
        java.util.Map<String, String> policies = request.missingPolicies() == null ? java.util.Map.of() : request.missingPolicies();
        Set<String> fields = Set.of("name", "category", "year", "area", "language", "director", "actors", "posterUrl", "description", "playUrls");
        if (!fields.containsAll(policies.keySet()) || !Set.of("PENDING", "SKIP", "ALLOW").containsAll(policies.values())
                || Set.of("name", "category", "playUrls").stream().anyMatch(f -> "ALLOW".equals(policies.get(f)))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺失信息策略不合法，名称、分类和播放地址不可允许为空");
        }
        e.setMissingPolicies(json.writeValueAsString(policies));
        e.setMatchFields(String.join(",", request.matchFields().stream().distinct().toList()));
        e.setUpdateFields(String.join(",", request.updateFields().stream().distinct().toList()));
        e.setAutoPublish(request.autoPublish());
        e.setUpdatedAt(VideoValues.now());
        store.settings.updateById(e);
        return get();
    }
}
