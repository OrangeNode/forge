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
 * <p>对应表 {@code sys_menu}。菜单是 RBAC 的唯一载体：{@code routeKey} 是前端本地路由白名单中的标识，
 * 后端不保存组件路径，也不向下发组件代码；{@code permCodes} 是鉴权使用的代码索引，
 * {@code permissionsJson} 同时保存代码、中文名称与说明。角色授予该菜单节点即同时获得这些接口权限，
 * 因此不需要独立的权限主表与角色权限关系表。</p>
 *
 * <p>落库内容与 Java 列表的互转统一走 {@code MenuPermissionCodes}，
 * 实体本身只保存列内容，不在这里做解析。</p>
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
     * 菜单类型：directory 目录、page 页面。
     */
    private String menuType;

    /**
     * 图标标识，由前端内置图标白名单解析。
     */
    private String icon;

    /**
     * 前端本地路由标识，目录为 {@code null}。
     */
    private String routeKey;

    /**
     * 该节点声明的接口权限标识，多个用英文逗号分隔；没有权限时为 {@code null}。
     */
    private String permCodes;

    /**
     * 权限代码、中文名称与说明组成的 JSON 数组。
     */
    private String permissionsJson;

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
