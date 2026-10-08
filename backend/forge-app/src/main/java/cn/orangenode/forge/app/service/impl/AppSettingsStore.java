package cn.orangenode.forge.app.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.app.entity.AppSettingsEntity;
import cn.orangenode.forge.app.mapper.AppSettingsMapper;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 全局设置单行的读取与词表编解码，供设置用例、公开用例与开关端口共用。
 */
@Component
@RequiredArgsConstructor
public class AppSettingsStore {
    /**
     * 单行设置的固定主键。
     */
    static final long SETTINGS_ID = 1L;
    private final AppSettingsMapper mapper;
    private final JsonMapper jsonMapper;

    /**
     * 读取设置行，迁移未执行时返回 500。
     */
    public AppSettingsEntity require() {
        AppSettingsEntity settings = mapper.selectById(SETTINGS_ID);
        if (settings == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "App 设置尚未初始化");
        }
        return settings;
    }

    /**
     * 读取设置行，不存在时为空。
     */
    public AppSettingsEntity find() {
        return mapper.selectById(SETTINGS_ID);
    }

    /**
     * 解析词表 JSON，损坏时按空表处理。
     */
    public List<String> words(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(jsonMapper.readValue(json, new TypeReference<List<String>>() { }));
        } catch (JacksonException failure) {
            return List.of();
        }
    }

    /**
     * 规范化词表：去空白、去重并保持顺序。
     */
    public List<String> normalize(List<String> words) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String word : words == null ? List.<String>of() : words) {
            if (word != null && !word.isBlank()) {
                result.add(word.strip());
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 词表编码为 JSON。
     */
    public String json(List<String> words) {
        return jsonMapper.writeValueAsString(words);
    }
}
