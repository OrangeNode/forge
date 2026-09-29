package cn.orangenode.forge.example.response;

/**
 * 公共能力验证响应。
 *
 * <p>对外 ID 使用字符串，与接口规范一致；不直接返回请求对象，转换显式完成。</p>
 *
 * @param id       回显的业务 ID 字符串
 * @param pageSize 回显的每页条数，未提交时为 {@code null}
 * @param status   回显的状态枚举代码，未提交时为 {@code null}
 */
public record ValidationSampleResponse(String id, Integer pageSize, String status) {
}
