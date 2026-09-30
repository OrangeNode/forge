package cn.orangenode.forge.system.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.system.response.MenuPermissionResponse;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 菜单权限配置的 JSON 列转换器。
 *
 * <p>权限代码、中文名称和说明共同保存在 {@code sys_menu.permissions_json}；
 * 本组件集中处理序列化与容错读取，避免业务服务与响应转换器各自实现一套 JSON 规则。</p>
 */
@Slf4j
@Component
public class MenuPermissionValues {

    /**
     * JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 构造菜单权限配置转换器。
     *
     * @param jsonMapper JSON 映射器
     */
    public MenuPermissionValues(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 把权限项序列化为数据库列内容。
     *
     * @param permissions 权限项列表，允许为空
     * @return JSON 数组文本；没有权限时返回 {@code null}
     */
    public String serialize(List<MenuPermissionResponse> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(permissions);
        } catch (JacksonException failure) {
            throw new IllegalStateException("菜单权限配置无法序列化", failure);
        }
    }

    /**
     * 解析数据库中的权限 JSON。
     *
     * <p>历史数据或人工修改造成内容损坏时记录告警并返回空列表，避免菜单树接口整体不可用；
     * 鉴权仍以同一菜单行的 {@code perm_codes} 为准，不会因展示资料损坏而扩大权限。</p>
     *
     * @param payload JSON 数组文本，允许为空
     * @return 权限项列表
     */
    public List<MenuPermissionResponse> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            List<?> values = jsonMapper.readValue(payload, List.class);
            List<MenuPermissionResponse> result = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Map<?, ?> item) {
                    String code = text(item.get("code"));
                    String name = text(item.get("name"));
                    if (code != null && name != null) {
                        result.add(new MenuPermissionResponse(code, name, text(item.get("description")), null, null));
                    }
                }
            }
            return List.copyOf(result);
        } catch (JacksonException failure) {
            log.warn("菜单权限展示配置无法解析，按空配置处理");
            return List.of();
        }
    }

    /**
     * 把未知 JSON 值转换为去空白文本。
     *
     * @param value JSON 值
     * @return 非空文本；空值返回 {@code null}
     */
    private String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}
