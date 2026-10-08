package cn.orangenode.forge.member.response;

/**
 * 收藏条目。
 *
 * @param videoId     影片 ID
 * @param videoName   影片名称
 * @param posterUrl   海报地址
 * @param remarks     更新备注
 * @param year        年份
 * @param favoritedAt 收藏时间
 */
public record MemberFavoriteResponse(String videoId, String videoName, String posterUrl, String remarks,
        Integer year, String favoritedAt) {
}
