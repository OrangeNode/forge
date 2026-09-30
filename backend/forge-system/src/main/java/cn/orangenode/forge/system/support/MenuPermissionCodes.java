package cn.orangenode.forge.system.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单节点上的权限标识集合处理。
 *
 * <p>RBAC 收敛为管理员、角色、菜单三张主表后，权限标识不再是独立主数据：
 * 它作为菜单节点上的一列配置保存，一个节点可以声明多个权限，用英文逗号分隔。
 * 列内容与 Java 列表之间的转换、逗号与空白的容错解析集中在这里，
 * 避免解析规则散落在实体、服务与转换器里各写一份。</p>
 */
public final class MenuPermissionCodes {

    /**
     * 多个权限标识之间的分隔符。
     */
    private static final String SEPARATOR = ",";

    /**
     * 解析数据库列内容为权限标识列表。
     *
     * <p>容忍历史数据里的空白与中文逗号：先按逗号切分，再逐个去空白并丢弃空项，
     * 保持原有顺序并去重。大小写不做转换，格式校验由写入路径负责。</p>
     *
     * @param raw 列内容，允许为 {@code null}
     * @return 权限标识列表，没有内容时返回空列表
     */
    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String part : raw.split("[" + SEPARATOR + "，;；]")) {
            String code = part.strip();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return List.copyOf(codes);
    }

    /**
     * 把权限标识列表拼成数据库列内容。
     *
     * <p>去空白、去重并保持顺序；列表为空时返回 {@code null}，让目录节点与纯可见性节点不占列内容，
     * 也避免出现空字符串这种需要额外判断的中间状态。</p>
     *
     * @param codes 权限标识列表，允许为 {@code null}
     * @return 以英文逗号分隔的列内容，没有权限时返回 {@code null}
     */
    public static String join(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            String value = PermissionCodeFormat.normalize(code);
            if (value != null) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? null : String.join(SEPARATOR, normalized);
    }
}
