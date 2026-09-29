package cn.orangenode.forge.file.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 文件元数据实体。
 *
 * <p>对应表 {@code file_record}。记录只保存对象键与业务元数据，不保存本地绝对路径与访问凭据；
 * {@code storageConfigId} 固定为上传时使用的存储配置版本，因此之后切换默认方案或新增配置版本
 * 都不会改变历史文件的读取位置。</p>
 *
 * <p>{@code deleted} 是逻辑删除标记，表示该记录不再对调用方可见；
 * 对象本身是否已经清理由文件模块的删除策略决定，不能仅凭逻辑删除推断对象已不存在。</p>
 */
@Getter
@Setter
@TableName("file_record")
public class FileRecordEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上传时固定的存储配置版本 ID。
     */
    private Long storageConfigId;

    /**
     * 存储对象键，随机生成，不包含原始文件名。
     */
    private String objectKey;

    /**
     * 原始文件名，只保存名称部分，用于下载时的响应头。
     */
    private String originalName;

    /**
     * 上传时声明的内容类型，可为空。
     */
    private String contentType;

    /**
     * 文件大小（字节）。
     */
    private Long sizeBytes;

    /**
     * 上传者管理员 ID。
     */
    private Long uploaderId;

    /**
     * 上传者名称快照，用于列表展示。
     *
     * <p>文件模块不反向依赖账号模块，无法在查询时连接账号表取显示名称，
     * 因此与审计表一致：保存上传时的名称快照，账号改名不影响历史记录。</p>
     */
    private String uploaderName;

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
