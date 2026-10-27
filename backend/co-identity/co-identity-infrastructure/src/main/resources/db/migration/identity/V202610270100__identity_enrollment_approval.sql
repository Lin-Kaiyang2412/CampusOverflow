-- 选课申请审批：既有成员全部为正式成员，新申请使用 PENDING 状态。
ALTER TABLE id_course_member
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'APPROVED' COMMENT 'PENDING / APPROVED / REJECTED' AFTER role;

CREATE INDEX idx_member_course_status ON id_course_member (course_id, status);
