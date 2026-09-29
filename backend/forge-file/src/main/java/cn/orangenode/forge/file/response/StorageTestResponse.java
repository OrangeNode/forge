package cn.orangenode.forge.file.response;

/**
 * 存储连接检测结果响应。
 *
 * <p>检测本身是一次成功的查询：目标不可用时也返回 {@code body.code=0}，由 {@code available}
 * 表达检测结论，避免管理界面把“检测失败”当作接口异常处理。{@code message} 只说明结论，
 * 不包含访问地址、桶名称与凭据。</p>
 *
 * @param id            被检测的存储配置版本 ID，对外为字符串
 * @param provider      存储类型，{@code local} 或 {@code s3}
 * @param available     目标是否可用
 * @param elapsedMillis 检测耗时（毫秒）
 * @param message       面向使用者的检测结论
 */
public record StorageTestResponse(String id, String provider, boolean available, long elapsedMillis, String message) {
}
