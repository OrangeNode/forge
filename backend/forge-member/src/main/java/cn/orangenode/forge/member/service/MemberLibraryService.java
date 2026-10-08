package cn.orangenode.forge.member.service;

import java.util.Optional;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.member.request.MemberHistoryRequest;
import cn.orangenode.forge.member.response.MemberFavoriteResponse;
import cn.orangenode.forge.member.response.MemberHistoryResponse;

/**
 * 当前普通用户的观看历史与收藏，手机与电视共用同一份云端数据。
 */
public interface MemberLibraryService {

    /**
     * 按最近观看时间倒序分页读取观看历史；已下架影片被跳过。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 观看历史分页
     */
    PageResponse<MemberHistoryResponse> history(Integer pageNum, Integer pageSize);

    /**
     * 读取某部影片的观看进度，用于断点续播。
     *
     * @param videoId 影片 ID
     * @return 观看进度，没有记录或影片已下架时为空
     */
    Optional<MemberHistoryResponse> progress(Long videoId);

    /**
     * 上报观看进度：同一影片只保留最近一次。
     *
     * @param videoId 影片 ID
     * @param request 进度
     */
    void saveProgress(Long videoId, MemberHistoryRequest request);

    /**
     * 删除一条观看历史，不存在时视为成功。
     *
     * @param videoId 影片 ID
     */
    void deleteHistory(Long videoId);

    /**
     * 清空当前用户的观看历史。
     */
    void clearHistory();

    /**
     * 按收藏时间倒序分页读取收藏；已下架影片被跳过。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 收藏分页
     */
    PageResponse<MemberFavoriteResponse> favorites(Integer pageNum, Integer pageSize);

    /**
     * 是否已收藏。
     *
     * @param videoId 影片 ID
     * @return 已收藏为真
     */
    boolean favorited(Long videoId);

    /**
     * 收藏影片，重复收藏视为成功。
     *
     * @param videoId 影片 ID
     */
    void addFavorite(Long videoId);

    /**
     * 取消收藏，未收藏时视为成功。
     *
     * @param videoId 影片 ID
     */
    void removeFavorite(Long videoId);
}
