package cn.orangenode.forge.system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理员账号实体。
 *
 * <p>对应表 {@code sys_admin}。账号首版只支持启停，不开放删除流程，因此没有逻辑删除字段；
 * 时间列按 UTC 语义读写，业务层与接口层都通过转换器显式转换，不在实体上做时区推断。</p>
 */
@Getter
@Setter
@TableName("sys_admin")
public class SysAdminEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名，已按账号规范化规则存储，不区分大小写唯一。
     */
    private String username;

    /**
     * 密码编码结果，不保存明文。
     */
    private String passwordHash;

    /**
     * 显示名称。
     */
    private String displayName;

    /**
     * 账号状态代码，取值见 {@code AdminAccountStatus}。
     */
    private String status;

    /**
     * 最近一次登录成功时间（UTC）。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间（UTC）。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间（UTC）。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建者管理员 ID，初始化流程创建时为 {@code null}。
     */
    private Long createdBy;

    /**
     * 更新者管理员 ID。
     */
    private Long updatedBy;
}
