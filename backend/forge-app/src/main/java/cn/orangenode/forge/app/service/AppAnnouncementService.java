package cn.orangenode.forge.app.service;

import java.util.List;
import java.util.Optional;

import cn.orangenode.forge.app.request.AppAnnouncementRequest;
import cn.orangenode.forge.app.response.AppAnnouncementPopupResponse;
import cn.orangenode.forge.app.response.AppAnnouncementResponse;

/**
 * App 公告用例：后台维护，App 进入首页时读取要弹出的一条并上报已看过。
 */
public interface AppAnnouncementService {

    /**
     * 查询全部公告。
     */
    List<AppAnnouncementResponse> announcements();

    /**
     * 新增或修改公告；标题、内容或按钮变化时内容版本加一。
     */
    AppAnnouncementResponse save(Long id, AppAnnouncementRequest request);

    /**
     * 逻辑删除公告。
     */
    void delete(Long id);

    /**
     * 读取当前用户要弹出的公告：生效中、当前版本未看过的公告里优先级最高的一条。
     */
    Optional<AppAnnouncementPopupResponse> popup(Long memberId);

    /**
     * 记录当前用户已看过公告的某个版本。
     */
    void markRead(Long memberId, Long announcementId, int revision);
}
