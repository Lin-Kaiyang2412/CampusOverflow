package com.campusoverflow.identity.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import java.time.Instant;
import java.util.Objects;

/** 课程成员关系：授权判定（谁能在该课程中采纳、认证、悬赏）的依据。 */
public class CourseMember {

    private final long courseId;
    private final long userId;
    private CourseRole role;
    private CourseMemberStatus status;
    private final Instant joinedAt;

    private CourseMember(long courseId, long userId, CourseRole role, CourseMemberStatus status, Instant joinedAt) {
        this.courseId = courseId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    /** 内部直接添加正式学生，供课程初始化与演示数据使用。 */
    public static CourseMember student(long courseId, long userId, Instant now) {
        return new CourseMember(courseId, userId, CourseRole.STUDENT, CourseMemberStatus.APPROVED, now);
    }

    public static CourseMember applicant(long courseId, long userId, Instant now) {
        return new CourseMember(courseId, userId, CourseRole.STUDENT, CourseMemberStatus.PENDING, now);
    }

    public static CourseMember teacher(long courseId, long userId, Instant now) {
        return new CourseMember(courseId, userId, CourseRole.TEACHER, CourseMemberStatus.APPROVED, now);
    }

    public static CourseMember reconstitute(long courseId, long userId, CourseRole role, CourseMemberStatus status,
                                            Instant joinedAt) {
        return new CourseMember(courseId, userId, role, status, joinedAt);
    }

    public void approve() {
        requirePending();
        status = CourseMemberStatus.APPROVED;
    }

    public void reject() {
        requirePending();
        status = CourseMemberStatus.REJECTED;
    }

    public void reapply() {
        if (status != CourseMemberStatus.REJECTED) {
            throw new BusinessRuleException("ID-1014", "只有被拒绝的申请可以重新提交");
        }
        status = CourseMemberStatus.PENDING;
    }

    /** 调整课程内角色（如任命助教）。课程教师本人的角色不可被降级。 */
    public void changeRole(CourseRole newRole) {
        if (status != CourseMemberStatus.APPROVED) {
            throw new BusinessRuleException("ID-1015", "待审核或已拒绝的申请不能调整课程角色");
        }
        if (role == CourseRole.TEACHER && newRole != CourseRole.TEACHER) {
            throw new BusinessRuleException("ID-1013", "不能修改课程教师的角色");
        }
        this.role = Objects.requireNonNull(newRole);
    }

    public boolean isApproved() {
        return status == CourseMemberStatus.APPROVED;
    }

    private void requirePending() {
        if (status != CourseMemberStatus.PENDING) {
            throw new ConflictException("ID-2007", "该选课申请已处理");
        }
    }

    public long courseId() { return courseId; }
    public long userId() { return userId; }
    public CourseRole role() { return role; }
    public CourseMemberStatus status() { return status; }
    public Instant joinedAt() { return joinedAt; }
}
