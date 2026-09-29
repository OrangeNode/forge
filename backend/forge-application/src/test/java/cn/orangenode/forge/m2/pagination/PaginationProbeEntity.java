package cn.orangenode.forge.m2.pagination;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页验证用的实体。
 *
 * <p>属于测试夹具：字段刻意包含自增主键、可排序业务列与逻辑删除列，
 * 用于验证分页改写、白名单排序与稳定排序，不建生产表。</p>
 */
@Getter
@Setter
@TableName("probe_record")
public class PaginationProbeEntity {

    /**
     * 自增主键，业务层为 Long。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标题，用于模糊查询与默认排序验证。
     */
    @TableField("title")
    private String title;

    /**
     * 优先级，用于验证自定义列的升序与降序排序。
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 逻辑删除标记，与其他业务主表保持一致。
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
