package cn.orangenode.forge.file.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.file.entity.FileRecordEntity;

/**
 * 文件元数据数据访问。
 *
 * <p>只由文件模块内部使用。引用检查使用显式 SQL 且不附加逻辑删除条件：
 * 文件记录即使被逻辑删除，行仍然存在并继续引用存储配置版本
 * （外键为 {@code ON DELETE RESTRICT}），因此引用判断必须包含已删除记录。</p>
 */
@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecordEntity> {

    /**
     * 统计引用指定存储配置版本的文件记录数。
     *
     * @param storageConfigId 存储配置版本 ID
     * @return 引用数量，包含已逻辑删除的记录
     */
    @Select("select count(1) from file_record where storage_config_id = #{storageConfigId}")
    long countByStorageConfigId(@Param("storageConfigId") Long storageConfigId);
}
