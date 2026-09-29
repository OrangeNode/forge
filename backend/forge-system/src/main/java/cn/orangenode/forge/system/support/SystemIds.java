package cn.orangenode.forge.system.support;

import java.util.LinkedHashSet;
import java.util.List;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 对外字符串 ID 与业务层 Long ID 的转换。
 *
 * <p>接口规范要求路径、查询、请求体与响应体中的 ID 都是字符串，业务层使用 Long。
 * 转换在此统一完成：只接受正十进制整数且不超过 Long 上限，非法格式与溢出都按参数错误处理，
 * 不把 {@link NumberFormatException} 泄漏给调用方，也不让各 Service 各写一套解析。</p>
 */
public final class SystemIds {

    /**
     * 工具类不允许实例化。
     */
    private SystemIds() {
    }

    /**
     * 把对外字符串 ID 解析为业务层 Long ID。
     *
     * @param rawId   对外 ID，允许为 {@code null}
     * @param subject 参数用途的中文说明，用于错误提示，例如“管理员 ID”
     * @return 大于 0 的业务层 ID
     * @throws BusinessException ID 缺失、格式非法、非正数或超出 Long 范围时抛出 400
     */
    public static Long toLong(String rawId, String subject) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少" + subject);
        }
        long value;
        try {
            value = Long.parseLong(rawId.trim());
        } catch (NumberFormatException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, subject + "格式不正确");
        }
        if (value <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, subject + "必须是正整数");
        }
        return value;
    }

    /**
     * 把业务层 Long ID 转换为对外字符串 ID。
     *
     * @param id 业务层 ID，允许为 {@code null}
     * @return 字符串 ID，入参为空时返回 {@code null}
     */
    public static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    /**
     * 把对外字符串 ID 集合解析为业务层 ID 列表，并去除重复项。
     *
     * <p>关系表使用组合主键，重复 ID 会导致重复插入而触发主键冲突；
     * 这里按提交顺序保留首次出现的 ID，既避免冲突又保持前端提交的顺序稳定。</p>
     *
     * @param rawIds  对外 ID 集合，允许为 {@code null}
     * @param subject 参数用途的中文说明，用于错误提示
     * @return 去重后的业务层 ID 列表，入参为空时返回空列表
     * @throws BusinessException 任一 ID 非法时抛出 400
     */
    public static List<Long> toLongList(List<String> rawIds, String subject) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            distinct.add(toLong(rawId, subject));
        }
        return List.copyOf(distinct);
    }
}
