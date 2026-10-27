package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.CourseRole;
import java.time.Instant;

public record CourseMemberView(long userId, String displayName, CourseRole role, Instant joinedAt) {
}
