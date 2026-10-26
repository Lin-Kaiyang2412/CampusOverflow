package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.util.Objects;

/** 评论：挂在问题或回答下，支持一层“二级回复”。 */
public class Comment {

    private Long id;
    private final TargetType targetType;
    private final long targetId;
    private final long questionId;
    private final long authorId;
    private final Long parentId;
    private final String body;
    private final Instant createdAt;

    private Comment(Long id, TargetType targetType, long targetId, long questionId, long authorId, Long parentId,
                    String body, Instant createdAt) {
        this.id = id;
        this.targetType = targetType;
        this.targetId = targetId;
        this.questionId = questionId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public static Comment post(TargetType targetType, long targetId, long questionId, long authorId,
                               Comment parent, String body, Instant now) {
        Objects.requireNonNull(targetType, "评论目标类型不能为空");
        Objects.requireNonNull(now, "评论时间不能为空");

        if (targetId <= 0 || questionId <= 0 || authorId <= 0) {
            throw new BusinessRuleException("QA-1002", "评论目标、问题和作者 ID 必须有效");
        }

        if (body == null || body.isBlank()) {
            throw new BusinessRuleException("QA-1002", "评论内容不能为空");
        }

        String normalizedBody = body.strip();
        Long parentId = null;

        if (parent != null) {
            if (parent.id() == null) {
                throw new BusinessRuleException("QA-1002", "回复的评论必须已经存在");
            }

            if (parent.parentId() != null) {
                throw new BusinessRuleException("QA-1002", "评论最多只能回复一层");
            }

            if (parent.targetType() != targetType
                    || parent.targetId() != targetId
                    || parent.questionId() != questionId) {
                throw new BusinessRuleException("QA-1002", "回复必须属于相同的评论目标和问题");
            }

            parentId = parent.id();
        }

        return new Comment(
                null,
                targetType,
                targetId,
                questionId,
                authorId,
                parentId,
                normalizedBody,
                now
        );
    }

    public static Comment reconstitute(Long id, TargetType targetType, long targetId, long questionId, long authorId,
                                       Long parentId, String body, Instant createdAt) {
        return new Comment(id, targetType, targetId, questionId, authorId, parentId, body, createdAt);
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public TargetType targetType() { return targetType; }
    public long targetId() { return targetId; }
    public long questionId() { return questionId; }
    public long authorId() { return authorId; }
    public Long parentId() { return parentId; }
    public String body() { return body; }
    public Instant createdAt() { return createdAt; }
}