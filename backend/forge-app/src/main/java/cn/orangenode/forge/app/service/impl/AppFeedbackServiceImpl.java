package cn.orangenode.forge.app.service.impl;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.app.config.AppProperties;
import cn.orangenode.forge.app.converter.AppConverter;
import cn.orangenode.forge.app.entity.AppFeedbackEntity;
import cn.orangenode.forge.app.mapper.AppFeedbackMapper;
import cn.orangenode.forge.app.request.AppFeedbackHandleRequest;
import cn.orangenode.forge.app.request.AppFeedbackSubmitRequest;
import cn.orangenode.forge.app.response.AppFeedbackResponse;
import cn.orangenode.forge.app.service.AppFeedbackService;
import cn.orangenode.forge.app.support.AppPaging;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.catalog.VideoCatalogPort;
import cn.orangenode.forge.framework.member.AuthenticatedMember;
import cn.orangenode.forge.framework.member.CurrentMember;
import cn.orangenode.forge.framework.page.PageResponses;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;

import lombok.RequiredArgsConstructor;

/**
 * 意见反馈实现：提交时保存提交人用户名与影片名称快照，后台只做查看与标记已处理。
 */
@Service
@RequiredArgsConstructor
public class AppFeedbackServiceImpl implements AppFeedbackService {
    private static final String PLAYBACK = "PLAYBACK";
    private static final String PENDING = "PENDING";
    private static final String RESOLVED = "RESOLVED";
    private final AppFeedbackMapper feedbacks;
    private final VideoCatalogPort catalog;
    private final AppProperties properties;
    private final AppPaging paging;
    private final AppConverter converter;

    /**
     * 校验每日条数与播放报错的影片后写入。
     */
    @Override
    @Transactional
    public void submit(AppFeedbackSubmitRequest request) {
        AuthenticatedMember member = CurrentMember.require();
        LocalDateTime now = AppValues.now();
        long today = feedbacks.selectCount(Wrappers.<AppFeedbackEntity>lambdaQuery()
                .eq(AppFeedbackEntity::getMemberId, member.id())
                .ge(AppFeedbackEntity::getCreatedAt, now.minusDays(1)));
        if (today >= properties.getFeedbackDailyLimit()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "今天提交的反馈较多，请明天再试");
        }
        Long videoId = AppValues.optionalId(request.videoId(), "影片");
        String videoName = null;
        if (videoId != null) {
            videoName = catalog.videoName(videoId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "影片不存在"));
        } else if (PLAYBACK.equals(request.type())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "播放报错需要指定影片");
        }
        AppFeedbackEntity feedback = new AppFeedbackEntity();
        feedback.setMemberId(member.id());
        feedback.setMemberUsername(member.username());
        feedback.setFeedbackType(request.type());
        feedback.setVideoId(videoId);
        feedback.setVideoName(videoName);
        feedback.setLineId(AppValues.optionalId(request.lineId(), "线路"));
        feedback.setLineName(AppValues.clean(request.lineName()));
        feedback.setEpisodeName(AppValues.clean(request.episodeName()));
        feedback.setContent(request.content().strip());
        feedback.setDeviceName(AppValues.clean(request.deviceName()));
        feedback.setAppVersion(AppValues.clean(request.appVersion()));
        feedback.setStatus(PENDING);
        feedback.setCreatedAt(now);
        feedback.setUpdatedAt(now);
        feedbacks.insert(feedback);
    }

    /**
     * 后台分页查询。
     */
    @Override
    public PageResponse<AppFeedbackResponse> page(Integer pageNum, Integer pageSize, String status, String type) {
        Page<AppFeedbackEntity> page = feedbacks.selectPage(paging.page(pageNum, pageSize),
                Wrappers.<AppFeedbackEntity>lambdaQuery()
                        .eq(status != null && !status.isBlank(), AppFeedbackEntity::getStatus, status)
                        .eq(type != null && !type.isBlank(), AppFeedbackEntity::getFeedbackType, type)
                        .orderByAsc(AppFeedbackEntity::getStatus)
                        .orderByDesc(AppFeedbackEntity::getId));
        return PageResponses.from(page, converter::feedback);
    }

    /**
     * 条件更新为已处理，避免并发重复处理。
     */
    @Override
    @Transactional
    public void resolve(Long id, AppFeedbackHandleRequest request) {
        AppFeedbackEntity feedback = feedbacks.selectById(id);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "反馈不存在");
        }
        LocalDateTime now = AppValues.now();
        int updated = feedbacks.update(null, Wrappers.<AppFeedbackEntity>lambdaUpdate()
                .set(AppFeedbackEntity::getStatus, RESOLVED)
                .set(AppFeedbackEntity::getHandleRemark, AppValues.clean(request == null ? null : request.remark()))
                .set(AppFeedbackEntity::getHandledBy, currentAdminId())
                .set(AppFeedbackEntity::getHandledAt, now)
                .set(AppFeedbackEntity::getUpdatedAt, now)
                .eq(AppFeedbackEntity::getId, id).eq(AppFeedbackEntity::getStatus, PENDING));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "反馈已处理");
        }
    }

    /**
     * 从安全上下文读取当前管理员 ID。
     */
    private Long currentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin
                ? admin.id() : null;
    }
}
