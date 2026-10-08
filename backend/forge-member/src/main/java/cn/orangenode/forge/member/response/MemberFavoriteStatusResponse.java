package cn.orangenode.forge.member.response;

/**
 * 当前用户是否已收藏某部影片。
 *
 * @param favorited 是否已收藏
 */
public record MemberFavoriteStatusResponse(boolean favorited) {
}
