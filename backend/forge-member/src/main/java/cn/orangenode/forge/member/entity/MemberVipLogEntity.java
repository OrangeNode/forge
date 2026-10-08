package cn.orangenode.forge.member.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户 VIP 变更记录，对应 {@code member_vip_log}，只追加。
 */
@Getter
@Setter
@TableName("member_vip_log")
public class MemberVipLogEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 普通用户主键。
     */
    private Long memberId;

    /**
     * 变更类型代码 EXTEND/SET/CANCEL。
     */
    private String changeType;

    /**
     * 延长天数，仅 EXTEND。
     */
    private Integer days;

    /**
     * 变更前到期时间，UTC。
     */
    private LocalDateTime beforeExpireAt;

    /**
     * 变更后到期时间，UTC。
     */
    private LocalDateTime afterExpireAt;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 操作管理员主键。
     */
    private Long operatorId;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;
}
