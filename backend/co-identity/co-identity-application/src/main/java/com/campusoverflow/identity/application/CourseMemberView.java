package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.CourseRole;
import java.time.Instant;

/**
 * 课程成员视图。joinedAt 在 APPROVED 状态表示加入时间，在 PENDING 状态表示申请提交时间，
 * 供待审列表按申请先后排序；该字段属于 PR #31 遗留的契约补齐。
 */
public record CourseMemberView(long userId, String displayName, CourseRole role, Instant joinedAt) {
}
