package cn.orangenode.forge.app.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.app.entity.AppBannerEntity;
import cn.orangenode.forge.app.entity.AppFeedbackEntity;
import cn.orangenode.forge.app.entity.AppSectionEntity;
import cn.orangenode.forge.app.entity.AppSplashEntity;
import cn.orangenode.forge.app.response.AppBannerResponse;
import cn.orangenode.forge.app.response.AppFeedbackResponse;
import cn.orangenode.forge.app.response.AppPickedVideoResponse;
import cn.orangenode.forge.app.response.AppSectionResponse;
import cn.orangenode.forge.app.response.AppSplashResponse;
import cn.orangenode.forge.app.response.AppVideoCardResponse;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.framework.catalog.VideoCard;

/**
 * App 设置实体与响应的显式转换。
 */
@Component
public class AppConverter {

    /**
     * 转换开屏。
     */
    public AppSplashResponse splash(AppSplashEntity e) {
        return new AppSplashResponse(AppValues.textId(e.getId()), e.getName(), AppValues.textId(e.getImageFileId()),
                AppValues.imageUrl(e.getImageFileId()), e.getDurationSeconds(), Boolean.TRUE.equals(e.getSkippable()),
                AppValues.time(e.getStartAt()), AppValues.time(e.getEndAt()), Boolean.TRUE.equals(e.getEnabled()),
                e.getSortNo(), AppValues.active(e.getEnabled(), e.getStartAt(), e.getEndAt(), AppValues.now()));
    }

    /**
     * 转换轮播。
     */
    public AppBannerResponse banner(AppBannerEntity e, String targetName) {
        return new AppBannerResponse(AppValues.textId(e.getId()), e.getTitle(), AppValues.textId(e.getImageFileId()),
                AppValues.imageUrl(e.getImageFileId()), e.getTargetType(), AppValues.textId(e.getTargetId()), targetName,
                AppValues.time(e.getStartAt()), AppValues.time(e.getEndAt()), Boolean.TRUE.equals(e.getEnabled()),
                e.getSortNo(), AppValues.active(e.getEnabled(), e.getStartAt(), e.getEndAt(), AppValues.now()));
    }

    /**
     * 转换推荐位。
     */
    public AppSectionResponse section(AppSectionEntity e, String categoryName, List<AppPickedVideoResponse> videos) {
        return new AppSectionResponse(AppValues.textId(e.getId()), e.getTitle(), e.getSourceType(),
                AppValues.textId(e.getCategoryId()), categoryName, videos, e.getItemLimit(),
                Boolean.TRUE.equals(e.getEnabled()), e.getSortNo());
    }

    /**
     * 转换后台反馈条目。
     */
    public AppFeedbackResponse feedback(AppFeedbackEntity feedback) {
        return new AppFeedbackResponse(AppValues.textId(feedback.getId()), AppValues.textId(feedback.getMemberId()),
                feedback.getMemberUsername(), feedback.getFeedbackType(), AppValues.textId(feedback.getVideoId()),
                feedback.getVideoName(), AppValues.textId(feedback.getLineId()), feedback.getLineName(),
                feedback.getEpisodeName(), feedback.getContent(), feedback.getDeviceName(), feedback.getAppVersion(),
                feedback.getStatus(), feedback.getHandleRemark(), AppValues.time(feedback.getHandledAt()),
                AppValues.time(feedback.getCreatedAt()));
    }

    /**
     * 转换影片卡片。
     */
    public AppVideoCardResponse card(VideoCard card) {
        return new AppVideoCardResponse(AppValues.textId(card.id()), card.name(), card.posterUrl(), card.remarks(),
                card.year(), AppValues.textId(card.categoryId()));
    }
}
