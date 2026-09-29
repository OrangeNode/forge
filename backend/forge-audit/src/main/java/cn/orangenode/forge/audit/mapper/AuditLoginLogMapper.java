package cn.orangenode.forge.audit.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.audit.entity.AuditLoginLogEntity;

/**
 * 登录日志数据访问。
 *
 * <p>只由审计模块内部使用，其他模块通过 framework 的 {@code LoginLogRecorder} 端口写入，
 * 不直接注入本 Mapper。审计表只追加与按期限清理，因此不提供修改语句。</p>
 */
@Mapper
public interface AuditLoginLogMapper extends BaseMapper<AuditLoginLogEntity> {
}
