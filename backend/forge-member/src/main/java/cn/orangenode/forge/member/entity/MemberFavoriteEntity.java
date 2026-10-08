package cn.orangenode.forge.member.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户收藏，对应 {@code member_favorite}；取消收藏物理删除。
 */
@Getter
@Setter
@TableName("member_favorite")
public class MemberFavoriteEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 普通用户主键。
     */
    private Long memberId;

    /**
     * 影片主键。
     */
    private Long videoId;

    /**
     * 收藏时间（UTC）。
     */
    private LocalDateTime createdAt;
}
