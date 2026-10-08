package cn.orangenode.forge.member.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.catalog.VideoCard;
import cn.orangenode.forge.framework.catalog.VideoCatalogPort;
import cn.orangenode.forge.framework.member.CurrentMember;
import cn.orangenode.forge.member.config.MemberProperties;
import cn.orangenode.forge.member.converter.MemberConverter;
import cn.orangenode.forge.member.entity.MemberFavoriteEntity;
import cn.orangenode.forge.member.entity.MemberWatchHistoryEntity;
import cn.orangenode.forge.member.mapper.MemberFavoriteMapper;
import cn.orangenode.forge.member.mapper.MemberWatchHistoryMapper;
import cn.orangenode.forge.member.request.MemberHistoryRequest;
import cn.orangenode.forge.member.response.MemberFavoriteResponse;
import cn.orangenode.forge.member.response.MemberHistoryResponse;
import cn.orangenode.forge.member.service.MemberLibraryService;
import cn.orangenode.forge.member.support.MemberPaging;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;

/**
 * 观看历史与收藏实现。
 *
 * <p>每个用户只保留最近观看的若干条记录（{@code forge.member.history-keep}，默认 10）。影片只保存 ID，展示信息经影片目录端口按页批量读取；已下架或删除的影片在列表中被跳过，
 * 因此某一页的条数可能少于每页条数，总数仍按库内记录计算。</p>
 */
@Service
@RequiredArgsConstructor
public class MemberLibraryServiceImpl implements MemberLibraryService {
    private final MemberWatchHistoryMapper histories;
    private final MemberFavoriteMapper favorites;
    private final VideoCatalogPort catalog;
    private final MemberPaging paging;
    private final MemberConverter converter;
    private final MemberProperties properties;

    /**
     * 分页读取观看历史。
     */
    @Override
    public PageResponse<MemberHistoryResponse> history(Integer pageNum, Integer pageSize) {
        // 只展示最近若干条：分页参数保留兼容，始终返回第一页的保留条数。
        int keep = properties.getHistoryKeep();
        Page<MemberWatchHistoryEntity> page = histories.selectPage(new Page<>(1, keep),
                Wrappers.<MemberWatchHistoryEntity>lambdaQuery()
                        .eq(MemberWatchHistoryEntity::getMemberId, memberId())
                        .orderByDesc(MemberWatchHistoryEntity::getUpdatedAt)
                        .orderByDesc(MemberWatchHistoryEntity::getId));
        Map<Long, VideoCard> cards = cards(page.getRecords().stream().map(MemberWatchHistoryEntity::getVideoId)
                .toList());
        List<MemberHistoryResponse> records = page.getRecords().stream()
                .filter(history -> cards.containsKey(history.getVideoId()))
                .map(history -> converter.history(history, cards.get(history.getVideoId())))
                .toList();
        return new PageResponse<>(records, Math.min(page.getTotal(), keep), 1, keep);
    }

    /**
     * 读取单部影片的观看进度。
     */
    @Override
    public Optional<MemberHistoryResponse> progress(Long videoId) {
        MemberWatchHistoryEntity history = findHistory(memberId(), videoId);
        if (history == null) {
            return Optional.empty();
        }
        return catalog.findPublic(List.of(videoId)).stream().findFirst()
                .map(card -> converter.history(history, card));
    }

    /**
     * 插入或更新观看进度；并发首次上报由唯一键兜底转为更新。
     */
    @Override
    @Transactional
    public void saveProgress(Long videoId, MemberHistoryRequest request) {
        assertPublic(videoId);
        Long memberId = memberId();
        Long lineId = request.lineId() == null || request.lineId().isBlank() ? null : MemberValues.id(request.lineId());
        LocalDateTime now = MemberValues.now();
        if (findHistory(memberId, videoId) == null) {
            MemberWatchHistoryEntity history = new MemberWatchHistoryEntity();
            history.setMemberId(memberId);
            history.setVideoId(videoId);
            history.setLineId(lineId);
            history.setEpisodeIndex(request.episodeIndex());
            history.setEpisodeName(request.episodeName().strip());
            history.setPositionSeconds(request.positionSeconds());
            history.setDurationSeconds(request.durationSeconds());
            history.setCreatedAt(now);
            history.setUpdatedAt(now);
            try {
                histories.insert(history);
                pruneHistory(memberId);
                return;
            } catch (DuplicateKeyException duplicate) {
                // 另一台设备同时首次上报，改为更新。
            }
        }
        histories.update(null, Wrappers.<MemberWatchHistoryEntity>lambdaUpdate()
                .set(MemberWatchHistoryEntity::getLineId, lineId)
                .set(MemberWatchHistoryEntity::getEpisodeIndex, request.episodeIndex())
                .set(MemberWatchHistoryEntity::getEpisodeName, request.episodeName().strip())
                .set(MemberWatchHistoryEntity::getPositionSeconds, request.positionSeconds())
                .set(MemberWatchHistoryEntity::getDurationSeconds, request.durationSeconds())
                .set(MemberWatchHistoryEntity::getUpdatedAt, now)
                .eq(MemberWatchHistoryEntity::getMemberId, memberId)
                .eq(MemberWatchHistoryEntity::getVideoId, videoId));
        pruneHistory(memberId);
    }

    /**
     * 只保留最近观看的若干条记录，删除更早的。
     */
    private void pruneHistory(Long memberId) {
        List<Long> ids = histories.selectList(Wrappers.<MemberWatchHistoryEntity>lambdaQuery()
                .select(MemberWatchHistoryEntity::getId)
                .eq(MemberWatchHistoryEntity::getMemberId, memberId)
                .orderByDesc(MemberWatchHistoryEntity::getUpdatedAt)
                .orderByDesc(MemberWatchHistoryEntity::getId))
                .stream().map(MemberWatchHistoryEntity::getId).toList();
        int keep = properties.getHistoryKeep();
        if (ids.size() > keep) {
            histories.deleteByIds(ids.subList(keep, ids.size()));
        }
    }

    /**
     * 物理删除一条观看历史。
     */
    @Override
    @Transactional
    public void deleteHistory(Long videoId) {
        histories.delete(Wrappers.<MemberWatchHistoryEntity>lambdaQuery()
                .eq(MemberWatchHistoryEntity::getMemberId, memberId())
                .eq(MemberWatchHistoryEntity::getVideoId, videoId));
    }

    /**
     * 物理删除当前用户全部观看历史。
     */
    @Override
    @Transactional
    public void clearHistory() {
        histories.delete(Wrappers.<MemberWatchHistoryEntity>lambdaQuery()
                .eq(MemberWatchHistoryEntity::getMemberId, memberId()));
    }

    /**
     * 分页读取收藏。
     */
    @Override
    public PageResponse<MemberFavoriteResponse> favorites(Integer pageNum, Integer pageSize) {
        Page<MemberFavoriteEntity> page = favorites.selectPage(paging.page(pageNum, pageSize),
                Wrappers.<MemberFavoriteEntity>lambdaQuery()
                        .eq(MemberFavoriteEntity::getMemberId, memberId())
                        .orderByDesc(MemberFavoriteEntity::getId));
        Map<Long, VideoCard> cards = cards(page.getRecords().stream().map(MemberFavoriteEntity::getVideoId).toList());
        List<MemberFavoriteResponse> records = page.getRecords().stream()
                .filter(favorite -> cards.containsKey(favorite.getVideoId()))
                .map(favorite -> converter.favorite(favorite, cards.get(favorite.getVideoId())))
                .toList();
        return new PageResponse<>(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 判断是否已收藏。
     */
    @Override
    public boolean favorited(Long videoId) {
        return favorites.selectCount(Wrappers.<MemberFavoriteEntity>lambdaQuery()
                .eq(MemberFavoriteEntity::getMemberId, memberId())
                .eq(MemberFavoriteEntity::getVideoId, videoId)) > 0;
    }

    /**
     * 收藏影片，重复收藏由唯一键兜底。
     */
    @Override
    @Transactional
    public void addFavorite(Long videoId) {
        assertPublic(videoId);
        if (favorited(videoId)) {
            return;
        }
        MemberFavoriteEntity favorite = new MemberFavoriteEntity();
        favorite.setMemberId(memberId());
        favorite.setVideoId(videoId);
        favorite.setCreatedAt(MemberValues.now());
        try {
            favorites.insert(favorite);
        } catch (DuplicateKeyException duplicate) {
            // 并发重复收藏，结果一致，视为成功。
        }
    }

    /**
     * 取消收藏。
     */
    @Override
    @Transactional
    public void removeFavorite(Long videoId) {
        favorites.delete(Wrappers.<MemberFavoriteEntity>lambdaQuery()
                .eq(MemberFavoriteEntity::getMemberId, memberId())
                .eq(MemberFavoriteEntity::getVideoId, videoId));
    }

    /**
     * 当前登录用户 ID。
     */
    private Long memberId() {
        return CurrentMember.require().id();
    }

    /**
     * 查询某用户某影片的历史行。
     */
    private MemberWatchHistoryEntity findHistory(Long memberId, Long videoId) {
        return histories.selectOne(Wrappers.<MemberWatchHistoryEntity>lambdaQuery()
                .eq(MemberWatchHistoryEntity::getMemberId, memberId)
                .eq(MemberWatchHistoryEntity::getVideoId, videoId));
    }

    /**
     * 影片不存在或未公开时返回 404。
     */
    private void assertPublic(Long videoId) {
        if (catalog.findPublic(List.of(videoId)).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "影片不存在或已下架");
        }
    }

    /**
     * 批量读取公开影片卡片并按 ID 建索引。
     */
    private Map<Long, VideoCard> cards(List<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        return catalog.findPublic(videoIds).stream()
                .collect(Collectors.toMap(VideoCard::id, Function.identity(), (first, second) -> first));
    }
}
