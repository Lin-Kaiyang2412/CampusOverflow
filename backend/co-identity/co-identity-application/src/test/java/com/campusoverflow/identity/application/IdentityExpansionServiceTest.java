package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.CourseMemberStatus;
import com.campusoverflow.identity.domain.CourseRepository;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityExpansionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T05:00:00Z");
    private final CourseRepository courses = mock(CourseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserQueryService userQuery = mock(UserQueryService.class);
    private final CourseService courseService = new CourseService(courses, users,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Actor teacher = new Actor(1, "王老师", Role.TEACHER);
    private final Actor student = new Actor(2, "学生", Role.STUDENT);
    private Course course;

    @BeforeEach
    void setUp() {
        course = Course.create(teacher, "DS-101", "数据结构", "2026-秋", NOW);
        course.assignId(100);
        when(courses.findById(100)).thenReturn(Optional.of(course));
    }

    @Test
    void applicationBecomesVisibleOnlyAfterApproval() {
        CourseMember[] stored = new CourseMember[1];
        when(courses.findMember(100, 2))
                .thenAnswer(invocation -> Optional.ofNullable(stored[0]));
        when(courses.saveMember(any())).thenAnswer(invocation -> {
            stored[0] = invocation.getArgument(0);
            return stored[0];
        });

        courseService.apply(student, 100);
        CourseMember application = stored[0];
        assertThat(application.status()).isEqualTo(CourseMemberStatus.PENDING);

        assertThatThrownBy(() -> courseService.pendingApplications(student, 100))
                .isInstanceOf(ForbiddenException.class);

        courseService.reviewApplication(teacher, 100, 2, true);
        assertThat(application.isApproved()).isTrue();
        assertThatThrownBy(() -> courseService.reviewApplication(teacher, 100, 2, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectedApplicationCanBeSubmittedAgain() {
        CourseMember rejected = CourseMember.applicant(100, 2, NOW);
        rejected.reject();
        when(courses.findMember(100, 2)).thenReturn(Optional.of(rejected));

        courseService.apply(student, 100);

        assertThat(rejected.status()).isEqualTo(CourseMemberStatus.PENDING);
    }

    @Test
    void profileUpdateChecksUniquenessAndReturnsCurrentView() {
        User user = User.register("20230001", "旧昵称", "old@campus.edu", "hash", "学院", "一班", NOW);
        user.assignId(2);
        when(users.findById(2)).thenReturn(Optional.of(user));
        when(users.findByDisplayName("新昵称")).thenReturn(Optional.empty());
        when(users.findByEmail("new@campus.edu")).thenReturn(Optional.empty());
        MeView view = new MeView(2, "20230001", "新昵称", "new@campus.edu", Role.STUDENT, false,
                "新学院", "二班", List.of());
        when(userQuery.me(2)).thenReturn(view);

        MeView result = new UserProfileService(users, userQuery).update(student,
                new UpdateProfileCommand("新昵称", "New@Campus.edu", "新学院", "二班"));

        assertThat(result).isSameAs(view);
        assertThat(user.displayName()).isEqualTo("新昵称");
        assertThat(user.email()).isEqualTo("new@campus.edu");
    }
}
