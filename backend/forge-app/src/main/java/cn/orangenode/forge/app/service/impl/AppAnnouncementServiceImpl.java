package cn.orangenode.forge.app.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.app.entity.AppAnnouncementEntity;
import cn.orangenode.forge.app.entity.AppAnnouncementReadEntity;
import cn.orangenode.forge.app.mapper.AppAnnouncementMapper;
import cn.orangenode.forge.app.mapper.AppAnnouncementReadMapper;
import cn.orangenode.forge.app.request.AppAnnouncementRequest;
import cn.orangenode.forge.app.response.AppAnnouncementPopupResponse;
import cn.orangenode.forge.app.response.AppAnnouncementResponse;
import cn.orangenode.forge.app.service.AppAnnouncementService;
import cn.orangenode.forge.app.support.AnnouncementHtml;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.catalog.VideoCatalogPort;

import lombok.RequiredArgsConstructor;

/**
 * 公告实现：HTML 保存前白名单过滤；已读按“用户 + 公告”记录看过的版本，内容修改后版本加一会再弹一次。
 */
@Service
@RequiredArgsConstructor
public class AppAnnouncementServiceImpl implements AppAnnouncementService {
    private static final String TYPE_HTML = "HTML";
    private static final String ACTION_NONE = "NONE";
    private static final String ACTION_VIDEO = "VIDEO";
    private static final String ACTION_URL = "URL";
    private final AppAnnouncementMapper announcements;
    private final AppAnnouncementReadMapper reads;
    private final VideoCatalogPort catalog;

    /**
     * 查询全部公告，优先级高的在前，同优先级新建的在前。
     */
    @Override
    public List<AppAnnouncementResponse> announcements() {
        return announcements.selectList(Wrappers.<AppAnnouncementEntity>lambdaQuery()
                .orderByAsc(AppAnnouncementEntity::getSortNo).orderByDesc(AppAnnouncementEntity::getId))
                .stream().map(this::response).toList();
    }

    /**
     * 新增或修改公告。
     */
    @Override
    @Transactional
    public AppAnnouncementResponse save(Long id, AppAnnouncementRequest r) {
        AppValues.assertWindow(r.startAt(), r.endAt());
        AppAnnouncementEntity e = id == null ? new AppAnnouncementEntity() : require(id);
        String content = TYPE_HTML.equals(r.contentType()) ? AnnouncementHtml.sanitize(r.content()) : r.content().strip();
        if (TYPE_HTML.equals(r.contentType()) && !AnnouncementHtml.hasContent(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "公告内容过滤后为空，请检查 HTML");
        }
        String actionType = r.actionType() == null ? ACTION_NONE : r.actionType();
        String actionValue = actionValue(actionType, r.actionValue());
        String title = r.title().strip();
        boolean changed = id == null || !title.equals(e.getTitle()) || !r.contentType().equals(e.getContentType())
                || !content.equals(e.getContent()) || !actionType.equals(e.getActionType())
                || !Objects.equals(actionValue, e.getActionValue());
        int revision = id == null ? 1 : e.getRevision() + (changed ? 1 : 0);
        LocalDateTime now = AppValues.now();
        if (id == null) {
            e.setTitle(title);
            e.setContentType(r.contentType());
            e.setContent(content);
            e.setRevision(revision);
            e.setActionType(actionType);
            e.setActionValue(actionValue);
            e.setStartAt(AppValues.utc(r.startAt()));
            e.setEndAt(AppValues.utc(r.endAt()));
            e.setEnabled(r.enabled());
            e.setSortNo(r.sortNo());
            e.setDeleted(0);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            announcements.insert(e);
        } else {
            announcements.update(null, Wrappers.<AppAnnouncementEntity>lambdaUpdate()
                    .set(AppAnnouncementEntity::getTitle, title)
                    .set(AppAnnouncementEntity::getContentType, r.contentType())
                    .set(AppAnnouncementEntity::getContent, content)
                    .set(AppAnnouncementEntity::getRevision, revision)
                    .set(AppAnnouncementEntity::getActionType, actionType)
                    .set(AppAnnouncementEntity::getActionValue, actionValue)
                    .set(AppAnnouncementEntity::getStartAt, AppValues.utc(r.startAt()))
                    .set(AppAnnouncementEntity::getEndAt, AppValues.utc(r.endAt()))
                    .set(AppAnnouncementEntity::getEnabled, r.enabled())
                    .set(AppAnnouncementEntity::getSortNo, r.sortNo())
                    .set(AppAnnouncementEntity::getUpdatedAt, now)
                    .eq(AppAnnouncementEntity::getId, id));
        }
        return response(require(e.getId()));
    }

    /**
     * 逻辑删除公告，已读记录随之失去作用，不单独清理。
     */
    @Override
    @Transactional
    public void delete(Long id) {
        require(id);
        announcements.deleteById(id);
    }

    /**
     * 读取要弹出的公告；跳转影片已被删除时不显示“去了解”。
     */
    @Override
    public Optional<AppAnnouncementPopupResponse> popup(Long memberId) {
        LocalDateTime now = AppValues.now();
        List<AppAnnouncementEntity> active = announcements.selectList(Wrappers.<AppAnnouncementEntity>lambdaQuery()
                .eq(AppAnnouncementEntity::getEnabled, true).orderByAsc(AppAnnouncementEntity::getSortNo)
                .orderByDesc(AppAnnouncementEntity::getId)).stream()
                .filter(e -> AppValues.active(e.getEnabled(), e.getStartAt(), e.getEndAt(), now)).toList();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Integer> seen = reads.selectList(Wrappers.<AppAnnouncementReadEntity>lambdaQuery()
                .eq(AppAnnouncementReadEntity::getMemberId, memberId)
                .in(AppAnnouncementReadEntity::getAnnouncementId, active.stream().map(AppAnnouncementEntity::getId)
                        .toList()))
                .stream().collect(Collectors.toMap(AppAnnouncementReadEntity::getAnnouncementId,
                        AppAnnouncementReadEntity::getRevision, Math::max));
        return active.stream().filter(e -> seen.getOrDefault(e.getId(), 0) < e.getRevision()).findFirst()
                .map(this::popupResponse);
    }

    /**
     * 记录已看过的版本；上报的版本高于当前版本时按当前版本记录，低于已记录的版本时不回退。
     */
    @Override
    @Transactional
    public void markRead(Long memberId, Long announcementId, int revision) {
        AppAnnouncementEntity announcement = require(announcementId);
        int value = Math.min(revision, announcement.getRevision());
        LocalDateTime now = AppValues.now();
        if (updateRead(memberId, announcementId, value, now) > 0) {
            return;
        }
        AppAnnouncementReadEntity read = new AppAnnouncementReadEntity();
        read.setMemberId(memberId);
        read.setAnnouncementId(announcementId);
        read.setRevision(value);
        read.setReadAt(now);
        try {
            reads.insert(read);
        } catch (DuplicateKeyException concurrent) {
            updateRead(memberId, announcementId, value, now);
        }
    }

    /**
     * 更新已有已读记录，返回命中的行数。
     */
    private int updateRead(Long memberId, Long announcementId, int revision, LocalDateTime now) {
        int matched = Math.toIntExact(reads.selectCount(Wrappers.<AppAnnouncementReadEntity>lambdaQuery()
                .eq(AppAnnouncementReadEntity::getMemberId, memberId)
                .eq(AppAnnouncementReadEntity::getAnnouncementId, announcementId)));
        if (matched > 0) {
            reads.update(null, Wrappers.<AppAnnouncementReadEntity>lambdaUpdate()
                    .set(AppAnnouncementReadEntity::getRevision, revision)
                    .set(AppAnnouncementReadEntity::getReadAt, now)
                    .eq(AppAnnouncementReadEntity::getMemberId, memberId)
                    .eq(AppAnnouncementReadEntity::getAnnouncementId, announcementId)
                    .lt(AppAnnouncementReadEntity::getRevision, revision));
        }
        return matched;
    }

    /**
     * 校验按钮目标：影片必须存在，外部链接必须是 http/https，不显示时清空。
     */
    private String actionValue(String type, String value) {
        if (ACTION_VIDEO.equals(type)) {
            Long videoId = AppValues.id(value, "跳转影片 ID");
            if (catalog.videoName(videoId).isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "跳转的影片不存在");
            }
            return videoId.toString();
        }
        return ACTION_URL.equals(type) ? AppValues.httpUrl(value, "公告链接") : null;
    }

    /**
     * 转换为后台响应。
     */
    private AppAnnouncementResponse response(AppAnnouncementEntity e) {
        String actionName = ACTION_VIDEO.equals(e.getActionType()) && e.getActionValue() != null
                ? catalog.videoName(Long.valueOf(e.getActionValue())).orElse(null) : null;
        return new AppAnnouncementResponse(AppValues.textId(e.getId()), e.getTitle(), e.getContentType(),
                e.getContent(), e.getRevision(), e.getActionType(), e.getActionValue(), actionName,
                AppValues.time(e.getStartAt()), AppValues.time(e.getEndAt()), Boolean.TRUE.equals(e.getEnabled()),
                e.getSortNo(), AppValues.active(e.getEnabled(), e.getStartAt(), e.getEndAt(), AppValues.now()),
                AppValues.time(e.getUpdatedAt()));
    }

    /**
     * 转换为 App 弹窗响应。
     */
    private AppAnnouncementPopupResponse popupResponse(AppAnnouncementEntity e) {
        String actionType = e.getActionType();
        String actionValue = e.getActionValue();
        if (ACTION_VIDEO.equals(actionType)
                && (actionValue == null || catalog.videoName(Long.valueOf(actionValue)).isEmpty())) {
            actionType = ACTION_NONE;
            actionValue = null;
        }
        return new AppAnnouncementPopupResponse(AppValues.textId(e.getId()), e.getRevision(), e.getTitle(),
                e.getContentType(), e.getContent(), actionType, actionValue);
    }

    /**
     * 读取公告，不存在返回 404。
     */
    private AppAnnouncementEntity require(Long id) {
        AppAnnouncementEntity e = announcements.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return e;
    }
}
