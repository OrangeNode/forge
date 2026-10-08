package cn.orangenode.forge.member.response;

/**
 * 观看历史条目：影片展示信息取最新值，进度为该用户最近一次上报。
 *
 * @param videoId         影片 ID
 * @param videoName       影片名称
 * @param posterUrl       海报地址
 * @param remarks         更新备注（如“更新至第 10 集”）
 * @param lineId          最近观看的线路 ID
 * @param episodeIndex    最近观看的分集序号，从 0 开始
 * @param episodeName     最近观看的分集名称
 * @param positionSeconds 播放位置秒数
 * @param durationSeconds 分集总时长秒数，未知为 0
 * @param updatedAt       最近观看时间
 */
public record MemberHistoryResponse(String videoId, String videoName, String posterUrl, String remarks,
        String lineId, int episodeIndex, String episodeName, int positionSeconds, int durationSeconds,
        String updatedAt) {
}
