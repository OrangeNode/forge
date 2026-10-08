package cn.orangenode.forge.app.response;

/**
 * 后台反馈列表条目。
 *
 * @param id           反馈 ID
 * @param memberId     提交人 ID
 * @param memberUsername 提交人用户名
 * @param type         PLAYBACK / GENERAL
 * @param videoId      影片 ID
 * @param videoName    影片名称
 * @param lineId       线路 ID
 * @param lineName     线路名称
 * @param episodeName  分集名称
 * @param content      反馈内容
 * @param deviceName   设备名称
 * @param appVersion   App 版本
 * @param status       PENDING / RESOLVED
 * @param handleRemark 处理备注
 * @param handledAt    处理时间
 * @param createdAt    提交时间
 */
public record AppFeedbackResponse(String id, String memberId, String memberUsername, String type, String videoId,
        String videoName, String lineId, String lineName, String episodeName, String content, String deviceName,
        String appVersion, String status, String handleRemark, String handledAt, String createdAt) {
}
