ALTER TABLE
    `question_tag_info`
    ADD
        COLUMN `category` VARCHAR(100) NOT NULL COMMENT '所属题库' AFTER `type`;

# 更新当前库的category字段为NEGATIVE
UPDATE question_tag_info
set category = 'NEGATIVE';

# 新增
BEGIN;
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425194150400000', '制度', '制度', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:27', '1',
        '2025-08-21 15:05:27', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425194871820288', '信仰', '信仰', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:27', '1',
        '2025-08-21 15:05:27', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425195459022848', '形象', '形象', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:27', '1',
        '2025-08-21 15:05:27', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425196054614016', '文化', '文化', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:27', '1',
        '2025-08-21 15:05:27', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425196633427968', '习俗', '习俗', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:27', '1',
        '2025-08-21 15:05:27', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425197178687488', '民族', '民族', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:27', '1',
        '2025-08-21 15:05:27', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425197723947008', '地理', '地理', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425198239846400', '历史', '历史', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425198764134400', '英烈', '英烈', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425199267450880', '性别', '性别', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425199783350272', '年龄', '年龄', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425200307638272', '职业', '职业', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425200844509184', '健康', '健康', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425201364602880', '幻觉', '幻觉', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:28', '1',
        '2025-08-21 15:05:28', b'0');
INSERT INTO `question_tag_info` (tag_id, tag_name, tag_desc, tag_level, parent_id, type, category, creator, create_time,
                                 updater, update_time, deleted)
VALUES ('1958425201884696576', '其他', '其他', '1', '0', '', 'FORWARD', '1', '2025-08-21 15:05:29', '1',
        '2025-08-21 15:05:29', b'0');
COMMIT;

# 更改标签
update question_info
set tags = (select tag_id from question_tag_info where category = 'FORWARD' and tag_name = '其他')
where category = 'FORWARD';

# 新增标签映射记录
insert into question_tag_mapping(tag_id, question_id)
select tags as tag_id,
       question_id
from question_info
where category = 'FORWARD';