package cn.orangenode.forge.system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 权限实体。
 *
 * <p>对应表 {@code sys_permission}。权限代码格式为 {@code 模块:资源:动作}，
 * 是 {@code @PreAuthorize} 的唯一依据，提交给前端只用于隐藏按钮，
 * 不替代后端校验。</p>
 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermissionEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 权限代码，格式为 {@code 模块:资源:动作}。
     */
    private String code;

    /**
     * 权限名称。
     */
    private String name;

    /**
     * 权限说明。
     */
    private String description;

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
