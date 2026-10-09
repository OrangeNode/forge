package cn.orangenode.forge.member.service;

import java.util.List;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.member.request.MemberActivationBatchRequest;
import cn.orangenode.forge.member.request.MemberActivationCreateRequest;
import cn.orangenode.forge.member.response.MemberActivationBatchResponse;
import cn.orangenode.forge.member.response.MemberActivationCodeResponse;
import cn.orangenode.forge.member.support.MemberActivationUsage;

/**
 * 激活码用例：后台生成、查询、作废、删除与导出，注册或已登录用户续费时核销。
 */
public interface MemberActivationService {

    /**
     * 分页查询激活码，按生成时间倒序。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  激活码或使用者用户名关键字
     * @param status   状态 UNUSED/USED/REVOKED/EXPIRED，为空不限
     * @param remark   备注关键字
     * @return 分页结果
     */
    PageResponse<MemberActivationCodeResponse> page(Integer pageNum, Integer pageSize, String keyword, String status,
            String remark);

    /**
     * 批量生成激活码。
     *
     * @param request 数量、有效期与备注
     * @return 新生成的激活码
     */
    List<MemberActivationCodeResponse> create(MemberActivationCreateRequest request);

    /**
     * 作废一个未使用的激活码；已使用 409，已作废 409。
     *
     * @param id 激活码记录 ID
     * @return 作废后的激活码
     */
    MemberActivationCodeResponse revoke(Long id);

    /**
     * 批量作废：只处理未使用的码（含旧数据中已过期的），其余跳过。
     *
     * @param request 激活码 ID
     * @return 作废与跳过数量
     */
    MemberActivationBatchResponse revokeBatch(MemberActivationBatchRequest request);

    /**
     * 批量删除：物理删除未使用、已作废或已过期的码；已使用的码保留注册记录，跳过不删。
     *
     * @param request 激活码 ID
     * @return 删除与跳过数量
     */
    MemberActivationBatchResponse deleteBatch(MemberActivationBatchRequest request);

    /**
     * 按筛选条件导出全部匹配的激活码，按生成时间倒序；超过导出上限返回 400。
     *
     * @param keyword 激活码或使用者用户名关键字
     * @param status  状态，为空不限
     * @param remark  备注关键字
     * @return 匹配的激活码
     */
    List<MemberActivationCodeResponse> export(String keyword, String status, String remark);

    /**
     * 注册或续费前校验激活码可用，不可用时抛出 400 并计入来源地址的失败次数。
     *
     * @param code     用户输入的激活码，允许小写、空格与短横线
     * @param clientIp 来源地址
     * @return 激活码记录 ID
     */
    Long check(String code, String clientIp);

    /**
     * 在调用方事务内核销激活码并返回会员天数；并发下已被他人使用时抛出 409，调用方事务随之回滚。
     *
     * @param codeId   激活码记录 ID
     * @param memberId 使用该码的用户 ID
     * @param username 使用该码的用户名
     * @param clientIp 来源地址
     * @param usage    用途：注册或续费
     * @return 该码的 VIP 天数，空表示不含会员时长
     */
    Integer consume(Long codeId, Long memberId, String username, String clientIp, MemberActivationUsage usage);
}
