package cn.orangenode.forge.system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 角色实体。
 *
 * <p>对应表 {@code sys_role}。角色代码稳定且不区分大小写唯一；
 * 角色是主数据，使用逻辑删除，被文件或业务引用时的删除约束由业务事务处理。</p>
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRoleEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色代码，例如 {@code super_admin}。
     */
    private String code;

    /**
     * 角色名称。
     */
    private String name;

    /**
     * 角色说明。
     */
    private String description;

    /**
     * 展示顺序，数值小的在前。
     */
    private Integer sortNo;

    /**
     * 逻辑删除标记，0 正常、1 已删除。
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（UTC）。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间（UTC）。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建者管理员 ID。
     */
    private Long createdBy;

    /**
     * 更新者管理员 ID。
     */
    private Long updatedBy;
}
