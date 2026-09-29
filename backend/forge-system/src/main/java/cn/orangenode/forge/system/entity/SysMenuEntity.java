package cn.orangenode.forge.system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜单实体。
 *
 * <p>对应表 {@code sys_menu}。菜单只描述可见性：{@code routeKey} 是前端本地路由白名单中的标识，
 * 后端不保存组件路径，也不向下发组件代码；菜单可见性与接口权限分别管理，
 * 赋予菜单不会隐式赋予接口权限。</p>
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenuEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父菜单 ID，0 表示顶级。
     */
    private Long parentId;

    /**
     * 菜单名称。
     */
    private String name;

    /**
     * 前端本地路由标识，目录为 {@code null}。
     */
    private String routeKey;

    /**
     * 同级展示顺序，数值小的在前。
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
