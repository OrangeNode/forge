-- 分类树使用零识别顶级节点，统一早期自动导入产生的空父编号。
UPDATE video_category SET parent_id = 0 WHERE parent_id IS NULL;
