package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Instant;
import java.util.Objects;

/**
 * 问题聚合根——问答核心不变量的守护者。
 * <ul>
 *   <li>最多只有一个被采纳的答案，且采纳后不可更改；</li>
 *   <li>只有提问者或课程管理者（教师/助教/管理员）可以采纳；</li>
 *   <li>已删除的问题不能再被回答、编辑或采纳；</li>
 *   <li>提问者只能删除尚无回答的问题，课程管理者可删除任意问题。</li>
 * </ul>
 * score / answerCount / viewCount 为反规范化计数器，由仓储以原子 SQL 维护，不在此处修改。
 */
public class Question {

    private Long id;
    private final long authorId;
    private final Long courseId;
    private Title title;
    private MarkdownBody body;
    private Tags tags;
    private Long acceptedAnswerId;
    private final int score;
    private final int answerCount;
    private final int viewCount;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private final long version;

    private Question(Long id, long authorId, Long courseId, Title title, MarkdownBody body, Tags tags,
                     Long acceptedAnswerId, int score, int answerCount, int viewCount, Instant createdAt,
                     Instant updatedAt, Instant deletedAt, long version) {
        this.id = id;
        this.authorId = authorId;
        this.courseId = courseId;
        this.title = Objects.requireNonNull(title);
        this.body = Objects.requireNonNull(body);
        this.tags = Objects.requireNonNull(tags);
        this.acceptedAnswerId = acceptedAnswerId;
        this.score = score;
        this.answerCount = answerCount;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public static Question post(long authorId, Long courseId, Title title, MarkdownBody body,
                                Tags tags, Instant now) {
        Objects.requireNonNull(now, "发布时间不能为空");

        return new Question(
                null,
                authorId,
                courseId,
                title,
                body,
                tags,
                null,
                0,
                0,
                0,
                now,
                now,
                null,
                0
        );
    }

    public static Question reconstitute(Long id, long authorId, Long courseId, Title title,
                                        MarkdownBody body, Tags tags, Long acceptedAnswerId,
                                        int score, int answerCount, int viewCount, Instant createdAt,
                                        Instant updatedAt, Instant deletedAt, long version) {
        return new Question(id, authorId, courseId, title, body, tags, acceptedAnswerId,
                score, answerCount, viewCount, createdAt, updatedAt, deletedAt, version);
    }

    public void edit(Actor actor, boolean actorManagesCourse, Title newTitle,
                     MarkdownBody newBody, Tags newTags, Instant now) {
        Objects.requireNonNull(actor, "操作者不能为空");
        Objects.requireNonNull(now, "更新时间不能为空");

        ensureNotDeleted();

        if (!actor.is(authorId) && !actorManagesCourse) {
            throw new ForbiddenException("QA-1003", "只有提问者或课程管理者可以编辑问题");
        }

        this.title = Objects.requireNonNull(newTitle, "标题不能为空");
        this.body = Objects.requireNonNull(newBody, "正文不能为空");
        this.tags = Objects.requireNonNull(newTags, "标签不能为空");
        this.updatedAt = now;
    }

    /** 采纳最佳答案。 */
    public void accept(Answer answer, Actor actor, boolean actorManagesCourse) {
        Objects.requireNonNull(actor, "操作者不能为空");

        ensureNotDeleted();

        if (!actor.is(authorId) && !actorManagesCourse) {
            throw new ForbiddenException("QA-3001", "只有提问者或课程管理者可以采纳答案");
        }

        Objects.requireNonNull(answer, "答案不能为空");

        if (answer.isDeleted()) {
            throw new BusinessRuleException("QA-2002", "已删除的回答不能被采纳");
        }

        if (id == null || answer.questionId() != id.longValue()) {
            throw new BusinessRuleException("QA-2002", "只能采纳当前问题下的回答");
        }

        if (acceptedAnswerId != null) {
            throw new ConflictException("QA-2002", "问题已经采纳了一个答案");
        }

        if (answer.id() == null) {
            throw new BusinessRuleException("QA-2002", "尚未分配 ID 的回答不能被采纳");
        }

        acceptedAnswerId = answer.id();
    }

    public void softDelete(Actor actor, boolean actorManagesCourse, Instant now) {
        Objects.requireNonNull(actor, "操作者不能为空");
        Objects.requireNonNull(now, "删除时间不能为空");

        ensureNotDeleted();

        if (!actor.is(authorId) && !actorManagesCourse) {
            throw new ForbiddenException("QA-1003", "只有提问者或课程管理者可以删除问题");
        }

        if (!actorManagesCourse && answerCount > 0) {
            throw new BusinessRuleException("QA-1004", "已有回答的问题不能由提问者删除");
        }

        deletedAt = now;
    }

    public void ensureOpenForAnswers() {
        ensureNotDeleted();

        if (acceptedAnswerId != null) {
            throw new BusinessRuleException("QA-2002", "问题已采纳答案，不能继续回答");
        }
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new BusinessRuleException("QA-1004", "问题已删除，不能继续操作");
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isAccepted(long answerId) {
        return acceptedAnswerId != null && acceptedAnswerId == answerId;
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long authorId() { return authorId; }
    public Long courseId() { return courseId; }
    public Title title() { return title; }
    public MarkdownBody body() { return body; }
    public Tags tags() { return tags; }
    public Long acceptedAnswerId() { return acceptedAnswerId; }
    public int score() { return score; }
    public int answerCount() { return answerCount; }
    public int viewCount() { return viewCount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant deletedAt() { return deletedAt; }
    public long version() { return version; }
}
