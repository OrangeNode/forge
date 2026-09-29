package cn.orangenode.forge.file.support;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 对外字符串 ID 与业务层 Long ID 的转换。
 *
 * <p>接口规范要求路径、查询、请求体与响应体中的 ID 都是字符串，业务层使用 Long。
 * 转换在此统一完成：只接受正十进制整数且不超过 Long 上限，非法格式与溢出都按参数错误处理，
 * 不把 {@link NumberFormatException} 泄漏给调用方，也不让各 Controller 与 Service 各写一套解析。</p>
 *
 * <p>本类位于文件模块内：模块之间不共享实现，基础模块也不反向依赖业务模块，
 * 因此不复用其他模块的同名工具类。</p>
 */
public final class FileIds {

    /**
     * 工具类不允许实例化。
     */
    private FileIds() {
    }

    /**
     * 把对外字符串 ID 解析为业务层 Long ID。
     *
     * @param rawId   对外 ID，允许为 {@code null}
     * @param subject 参数用途的中文说明，用于错误提示，例如“文件 ID”
     * @return 大于 0 的业务层 ID
     * @throws BusinessException ID 缺失、格式非法、非正数或超出 Long 范围时抛出 400
     */
    public static Long toLong(String rawId, String subject) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少" + subject);
        }
        return parse(rawId, subject);
    }

    /**
     * 把可选的对外字符串 ID 解析为业务层 Long ID。
     *
     * <p>用于查询条件中的可空 ID：未提供时返回 {@code null} 表示不按该字段过滤，
     * 提供了则必须合法。</p>
     *
     * @param rawId   对外 ID，允许为 {@code null} 或空白
     * @param subject 参数用途的中文说明，用于错误提示
     * @return 大于 0 的业务层 ID，未提供时返回 {@code null}
     * @throws BusinessException 提供了但格式非法、非正数或超出 Long 范围时抛出 400
     */
    public static Long toOptionalLong(String rawId, String subject) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return parse(rawId, subject);
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
     * 解析并校验非空的 ID 文本。
     *
     * @param rawId   非空 ID 文本
     * @param subject 参数用途的中文说明
     * @return 大于 0 的业务层 ID
     * @throws BusinessException 格式非法、非正数或超出 Long 范围时抛出 400
     */
    private static Long parse(String rawId, String subject) {
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
}
