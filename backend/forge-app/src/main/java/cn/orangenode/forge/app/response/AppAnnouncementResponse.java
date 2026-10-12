package cn.orangenode.forge.app.response;

/**
 * 后台公告项。
 *
 * @param id          公告 ID
 * @param title       标题
 * @param contentType 内容格式 TEXT / HTML
 * @param content     内容；HTML 为白名单过滤后的结果
 * @param revision    内容版本
 * @param actionType  “去了解”按钮 NONE / VIDEO / URL
 * @param actionValue 跳转影片 ID 或外部链接
 * @param popupFrequency 弹出频率 ONCE / DAILY / EVERY_LAUNCH
 * @param actionName  跳转影片名称，非影片或影片已删除时为空
 * @param startAt     生效开始时间
 * @param endAt       生效结束时间
 * @param enabled     是否启用
 * @param sortNo      优先级
 * @param active      当前是否处于生效状态
 * @param updatedAt   最近修改时间
 */
public record AppAnnouncementResponse(String id, String title, String contentType, String content, int revision,
        String actionType, String actionValue, String popupFrequency, String actionName, String startAt, String endAt, boolean enabled,
        int sortNo, boolean active, String updatedAt) {
}
