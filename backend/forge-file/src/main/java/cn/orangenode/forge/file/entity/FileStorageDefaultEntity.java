package cn.orangenode.forge.file.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 默认存储配置指针实体。
 *
 * <p>对应表 {@code file_storage_default}，全表只有 {@code id = 1} 一行：
 * 用固定单行指针记录当前默认的存储配置版本，切换默认就是更新这一行的 {@code config_id}，
 * 只影响之后的新上传，已经写入的文件仍按记录里固定的版本读取。</p>
 *
 * <p>主键不由数据库自增，因此使用 {@link IdType#INPUT} 显式传入固定值，
 * 与本模块其他自增主键表不同。</p>
 */
@Getter
@Setter
@TableName("file_storage_default")
public class FileStorageDefaultEntity {

    /**
     * 主键，固定为 1。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 当前默认存储配置 ID。
     */
    private Long configId;

    /**
     * 更新时间（UTC）。
     */
    private LocalDateTime updatedAt;

    /**
     * 更新者管理员 ID。
     */
    private Long updatedBy;
}
