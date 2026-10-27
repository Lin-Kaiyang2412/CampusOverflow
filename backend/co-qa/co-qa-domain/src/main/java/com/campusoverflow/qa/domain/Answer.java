package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Instant;
import java.util.Objects;

/**
 * 回答聚合根。独立于 Question 聚合以降低并发冲突，通过 questionId 引用问题。
 * “是否被采纳”由 Question.acceptedAnswerId 决定（单一事实来源），这里不重复存储。
 */
public class Answer {

    private Long id;
    private final long questionId;
    private final long authorId;
    private MarkdownBody body;
    private final int score;
    private Long endorsedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private final long version;

    private Answer(Long id, long questionId, long authorId, MarkdownBody body, int score,
                   Long endorsedBy, Instant createdAt, Instant updatedAt,
                   Instant deletedAt, long version) {
        this.id = id;
        this.questionId = questionId;
        this.authorId = authorId;
        this.body = Objects.requireNonNull(body);
        this.score = score;
        this.endorsedBy = endorsedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public static Answer submit(Question question, long authorId, MarkdownBody body, Instant now) {
        Objects.requireNonNull(question, "问题不能为空");
        Objects.requireNonNull(body, "回答正文不能为空");
        Objects.requireNonNull(now, "提交时间不能为空");

        question.ensureOpenForAnswers();

        if (question.id() == null) {
            throw new BusinessRuleException("QA-1004", "问题尚未分配 ID，不能提交回答");
        }

        return new Answer(
                null,
                question.id(),
                authorId,
                body,
                0,
                null,
                now,
                now,
                null,
                0
        );
    }

    public static Answer reconstitute(Long id, long questionId, long authorId, MarkdownBody body,
                                      int score, Long endorsedBy, Instant createdAt,
                                      Instant updatedAt, Instant deletedAt, long version) {
        return new Answer(id, questionId, authorId, body, score, endorsedBy,
                createdAt, updatedAt, deletedAt, version);
    }

    public void edit(Actor actor, boolean actorManagesCourse, MarkdownBody newBody, Instant now) {
        Objects.requireNonNull(actor, "操作者不能为空");
        Objects.requireNonNull(newBody, "回答正文不能为空");
        Objects.requireNonNull(now, "更新时间不能为空");

        ensureNotDeleted();

        if (!actor.is(authorId) && !actorManagesCourse) {
            throw new ForbiddenException("QA-3001", "只有回答者或课程管理者可以编辑回答");
        }

        this.body = newBody;
        this.updatedAt = now;
    }

    /** 教师认证：课程教师/助教为优质回答打上“教师认证”标记。 */
    public void endorse(Actor actor, boolean actorManagesCourse) {
        Objects.requireNonNull(actor, "操作者不能为空");

        ensureNotDeleted();

        if (!actorManagesCourse) {
            throw new ForbiddenException("QA-3001", "只有课程管理者可以认证回答");
        }

        if (endorsedBy != null) {
            throw new ConflictException("QA-2002", "回答已经通过教师认证");
        }

        endorsedBy = actor.userId();
    }

    /** 被采纳的回答不能由回答者本人删除，课程管理者可删除。 */
    public void softDelete(Actor actor, boolean actorManagesCourse, boolean accepted, Instant now) {
        Objects.requireNonNull(actor, "操作者不能为空");
        Objects.requireNonNull(now, "删除时间不能为空");

        ensureNotDeleted();

        if (!actor.is(authorId) && !actorManagesCourse) {
            throw new ForbiddenException("QA-3001", "只有回答者或课程管理者可以删除回答");
        }

        if (accepted && !actorManagesCourse) {
            throw new BusinessRuleException("QA-2002", "被采纳的回答不能由回答者本人删除");
        }

        deletedAt = now;
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new BusinessRuleException("QA-1004", "回答已删除，不能继续操作");
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long questionId() { return questionId; }
    public long authorId() { return authorId; }
    public MarkdownBody body() { return body; }
    public int score() { return score; }
    public Long endorsedBy() { return endorsedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant deletedAt() { return deletedAt; }
    public long version() { return version; }
}
