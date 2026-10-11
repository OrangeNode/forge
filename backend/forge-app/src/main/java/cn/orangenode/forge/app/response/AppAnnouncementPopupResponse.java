package cn.orangenode.forge.app.response;

/**
 * App 进入首页时要弹出的公告。
 *
 * @param id          公告 ID
 * @param revision    内容版本，关闭时原样上报
 * @param title       标题
 * @param contentType 内容格式 TEXT / HTML
 * @param content     内容；HTML 已按白名单过滤
 * @param actionType  “去了解”按钮 NONE 不显示 / VIDEO 影片 / URL 外部链接
 * @param actionValue 跳转影片 ID 或外部链接
 */
public record AppAnnouncementPopupResponse(String id, int revision, String title, String contentType,
        String content, String actionType, String actionValue) {
}
