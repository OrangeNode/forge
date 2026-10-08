package cn.orangenode.forge.member.service;

import cn.orangenode.forge.member.mail.MemberCodePurpose;

/**
 * 邮箱验证码用例：发送频率、每日次数与错误尝试次数都受限制，验证码只以摘要保存在 Redis。
 */
public interface MemberCodeService {

    /**
     * 生成并发送验证码。
     *
     * @param email    规范化邮箱
     * @param purpose  验证码用途
     * @param clientIp 来源地址，可为空
     */
    void send(String email, MemberCodePurpose purpose, String clientIp);

    /**
     * 校验并消费验证码；错误或失效返回 body.code=400，错误次数达到上限后验证码作废。
     *
     * @param email   规范化邮箱
     * @param purpose 验证码用途
     * @param code    用户输入的验证码
     */
    void consume(String email, MemberCodePurpose purpose, String code);
}
