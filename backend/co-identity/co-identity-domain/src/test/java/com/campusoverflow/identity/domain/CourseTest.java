package com.campusoverflow.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CourseTest {

    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");

    @Test
    void teacherCreatesCourseAndBecomesItsTeacher() {
        Course c = Course.create(new Actor(5, "王老师", Role.TEACHER), "ds-101", "数据结构", "2026-秋", NOW);
        assertThat(c.code()).isEqualTo("DS-101");
        assertThat(c.teacherId()).isEqualTo(5);
    }

    @Test
    void studentCannotCreateCourse() {
        assertThatThrownBy(() -> Course.create(new Actor(2, "alice", Role.STUDENT), "DS", "数据结构", "2026-秋", NOW))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void courseTeacherRoleCannotBeDowngraded() {
        CourseMember m = CourseMember.teacher(1, 5, NOW);
        assertThatThrownBy(() -> m.changeRole(CourseRole.STUDENT)).isInstanceOf(BusinessRuleException.class);
        CourseMember s = CourseMember.student(1, 6, NOW);
        s.changeRole(CourseRole.TA);
        assertThat(s.role().canManage()).isTrue();
    }

    @Test
    void applicationRequiresApprovalBeforeBecomingMember() {
        CourseMember application = CourseMember.applicant(1, 2, NOW);
        assertThat(application.status()).isEqualTo(CourseMemberStatus.PENDING);
        assertThat(application.isApproved()).isFalse();
        assertThatThrownBy(() -> application.changeRole(CourseRole.TA))
                .isInstanceOf(BusinessRuleException.class);

        application.approve();

        assertThat(application.status()).isEqualTo(CourseMemberStatus.APPROVED);
        assertThat(application.isApproved()).isTrue();
        assertThatThrownBy(application::approve).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectedApplicationCanBeSubmittedAgain() {
        CourseMember application = CourseMember.applicant(1, 2, NOW);

        application.reject();
        assertThat(application.status()).isEqualTo(CourseMemberStatus.REJECTED);
        application.reapply();

        assertThat(application.status()).isEqualTo(CourseMemberStatus.PENDING);
    }
}
