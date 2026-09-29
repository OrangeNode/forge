-- M4 补丁：文件记录增加上传者名称快照。
--
-- 原因：文件模块不允许反向依赖账号模块，不能在查询时连接 sys_admin 取显示名称；
-- 因此按审计表的做法保存上传时的名称快照，列表接口可以直接展示上传者。
-- 该列只用于展示，不作为权限判断依据，账号改名不会影响历史记录。

ALTER TABLE file_record
    ADD COLUMN uploader_name VARCHAR(64) NULL COMMENT '上传者名称快照' AFTER uploader_id;
