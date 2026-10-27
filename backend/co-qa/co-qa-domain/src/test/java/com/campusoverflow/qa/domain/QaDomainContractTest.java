package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Q&A 领域对象的契约测试：快照读取、ID 分配守卫、编辑/删除时序、评论嵌套规则。
 * 补齐 Lab 7「核心聚合 90%」所需的分支，不改变任何生产代码行为。
 */
class QaDomainContractTest {

    private static final Instant NOW = Instant.parse("2026-10-27T02:00:00Z");
    private static final Actor ASKER = new Actor(1, "提问者", Role.STUDENT);
    private static final Actor OTHER = new Actor(2, "路人", Role.STUDENT);

    private static Question question() {
        return Question.post(ASKER.userId(), 100L, new Title("红黑树插入后如何判断旋转方向？"),
                MarkdownBody.ofQuestion("插入节点后出现连续红色节点，什么时候左旋、什么时候右旋？"),
                Tags.of(List.of("数据结构", "红黑树")), NOW);
    }

    @Nested
    class QuestionContract {

        @Test
        void exposesSnapshotAndRejectsSecondIdAssignment() {
            Question q = question();

            assertThat(q.id()).isNull();
            assertThat(q.authorId()).isEqualTo(ASKER.userId());
            assertThat(q.courseId()).isEqualTo(100L);
            assertThat(q.score()).isZero();
            assertThat(q.answerCount()).isZero();
            assertThat(q.viewCount()).isZero();
            assertThat(q.version()).isZero();
            assertThat(q.acceptedAnswerId()).isNull();
            assertThat(q.createdAt()).isEqualTo(NOW);
            assertThat(q.updatedAt()).isEqualTo(NOW);
            assertThat(q.deletedAt()).isNull();
            assertThat(q.tags().names()).containsExactly("数据结构", "红黑树");

            q.assignId(10);
            assertThat(q.id()).isEqualTo(10L);
            assertThatThrownBy(() -> q.assignId(11)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void acceptedQuestionRefusesFurtherAnswers() {
            Question q = question();
            q.assignId(10);
            Answer a = Answer.submit(q, OTHER.userId(), MarkdownBody.ofAnswer("看叔叔节点的颜色和插入位置。"), NOW);
            a.assignId(50);
            q.accept(a, ASKER, false);

            assertThatThrownBy(q::ensureOpenForAnswers).isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void acceptRejectsUnsavedQuestion() {
            Question unsaved = question();
            Answer answer = Answer.reconstitute(50L, 999L, OTHER.userId(),
                    MarkdownBody.ofAnswer("这是在问题落库之前提交的回答内容"), 0, null, NOW, NOW, null, 0);

            // 问题没有 ID 时无法判定归属，必须先落库
            assertThatThrownBy(() -> unsaved.accept(answer, ASKER, false))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void acceptRejectsAnswerWithoutId() {
            Question q = question();
            q.assignId(10);
            Answer notPersisted =
                    Answer.submit(q, OTHER.userId(), MarkdownBody.ofAnswer("这条回答还没有被保存到数据库。"), NOW);

            assertThatThrownBy(() -> q.accept(notPersisted, ASKER, false))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(q.acceptedAnswerId()).isNull();
        }

        @Test
        void onlyAuthorOrCourseManagerMayDelete() {
            Question q = question();
            q.assignId(10);

            // 既不是提问者、又不是课程管理者：领域层直接拒绝（QA-1003 → 403）
            assertThatThrownBy(() -> q.softDelete(OTHER, false, NOW.plusSeconds(60)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("只有提问者或课程管理者可以删除问题");
            assertThat(q.deletedAt()).isNull();

            // 课程管理者可以删除他人问题；删除后不能再删除一次
            q.softDelete(new Actor(9, "助教", Role.TA), true, NOW.plusSeconds(120));
            assertThat(q.isDeleted()).isTrue();
            assertThat(q.deletedAt()).isEqualTo(NOW.plusSeconds(120));
            assertThatThrownBy(() -> q.softDelete(new Actor(9, "助教", Role.TA), true, NOW.plusSeconds(180)))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class AnswerContract {

        @Test
        void submitRequiresPersistedQuestion() {
            Question unsaved = question();

            // Answer.submit 内部会再校验一次问题是否已分配 ID
            assertThatThrownBy(() -> Answer.submit(unsaved, OTHER.userId(),
                    MarkdownBody.ofAnswer("问题尚未落库时不允许提交回答内容"), NOW))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void editUpdatesBodyAndTimestamp() {
            Question q = question();
            q.assignId(10);
            Answer a = Answer.submit(q, OTHER.userId(), MarkdownBody.ofAnswer("先看叔叔节点的颜色。"), NOW);
            a.assignId(50);

            a.edit(OTHER, false, MarkdownBody.ofAnswer("先看叔叔节点的颜色，再看插入位置决定左旋或右旋。"),
                    NOW.plusSeconds(120));

            assertThat(a.body().value()).contains("左旋或右旋");
            assertThat(a.updatedAt()).isAfter(a.createdAt());
        }

        @Test
        void deletedAnswerCannotBeEdited() {
            Question q = question();
            q.assignId(10);
            Answer a = Answer.submit(q, OTHER.userId(), MarkdownBody.ofAnswer("这条回答稍后会被删除掉。"), NOW);
            a.assignId(50);
            a.softDelete(OTHER, false, false, NOW);

            assertThatThrownBy(() -> a.edit(OTHER, false,
                    MarkdownBody.ofAnswer("删除之后不应该还能编辑这条回答"), NOW.plusSeconds(60)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void exposesSnapshotAndRejectsSecondIdAssignment() {
            Answer a = Answer.reconstitute(50L, 10L, 3, MarkdownBody.ofAnswer("已落库的回答正文内容"), 7, 9L,
                    NOW, NOW.plusSeconds(30), null, 2);

            assertThat(a.id()).isEqualTo(50L);
            assertThat(a.questionId()).isEqualTo(10L);
            assertThat(a.authorId()).isEqualTo(3L);
            assertThat(a.score()).isEqualTo(7);
            assertThat(a.endorsedBy()).isEqualTo(9L);
            assertThat(a.createdAt()).isEqualTo(NOW);
            assertThat(a.updatedAt()).isEqualTo(NOW.plusSeconds(30));
            assertThat(a.deletedAt()).isNull();
            assertThat(a.version()).isEqualTo(2L);
            assertThat(a.isDeleted()).isFalse();

            assertThatThrownBy(() -> a.assignId(51)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class VoteContract {

        @Test
        void changeKeepsHistoryAndGettersReflectLatestState() {
            Vote vote = Vote.cast(OTHER.userId(), ASKER.userId(), TargetType.ANSWER, 50L, 1, NOW);
            vote.assignId(7);

            assertThat(vote.id()).isEqualTo(7L);
            assertThat(vote.voterId()).isEqualTo(OTHER.userId());
            assertThat(vote.targetType()).isEqualTo(TargetType.ANSWER);
            assertThat(vote.targetId()).isEqualTo(50L);
            assertThat(vote.value()).isEqualTo(1);

            int previous = vote.change(-1, NOW.plusSeconds(10));

            assertThat(previous).isEqualTo(1);
            assertThat(vote.value()).isEqualTo(-1);
            assertThat(vote.updatedAt()).isEqualTo(NOW.plusSeconds(10));
        }

        @Test
        void reconstituteRestoresPersistedVote() {
            Vote vote = Vote.reconstitute(9L, OTHER.userId(), TargetType.QUESTION, 10L, -1, NOW);

            assertThat(vote.id()).isEqualTo(9L);
            assertThat(vote.value()).isEqualTo(-1);
            assertThat(vote.updatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    class CommentContract {

        @Test
        void postValidatesIdsAndBody() {
            assertThatThrownBy(() -> Comment.post(TargetType.QUESTION, 0, 10, 1, null, "内容", NOW))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> Comment.post(TargetType.QUESTION, 10, 10, 1, null, "   ", NOW))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void replyRequiresPersistedParentOfSameTarget() {
            Comment unsavedParent = Comment.post(TargetType.QUESTION, 10, 10, 1, null, "父评论", NOW);
            assertThatThrownBy(() ->
                    Comment.post(TargetType.QUESTION, 10, 10, 2, unsavedParent, "回复", NOW))
                    .isInstanceOf(BusinessRuleException.class);

            Comment parent = Comment.reconstitute(5L, TargetType.QUESTION, 10, 10, 1, null, "父评论", NOW);
            assertThatThrownBy(() ->
                    Comment.post(TargetType.ANSWER, 11, 10, 2, parent, "跨目标回复", NOW))
                    .isInstanceOf(BusinessRuleException.class);

            Comment reply = Comment.post(TargetType.QUESTION, 10, 10, 2, parent, "  一级回复  ", NOW);
            assertThat(reply.parentId()).isEqualTo(5L);
            assertThat(reply.body()).isEqualTo("一级回复");
        }

        @Test
        void replyDepthIsLimitedToOneLevel() {
            Comment parent = Comment.reconstitute(5L, TargetType.QUESTION, 10, 10, 1, null, "父评论", NOW);
            Comment reply = Comment.reconstitute(6L, TargetType.QUESTION, 10, 10, 2, 5L, "一级回复", NOW);

            assertThatThrownBy(() ->
                    Comment.post(TargetType.QUESTION, 10, 10, 3, reply, "二级回复", NOW))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void exposesSnapshotAndRejectsSecondIdAssignment() {
            Comment c = Comment.reconstitute(5L, TargetType.QUESTION, 10, 20, 1, null, "评论正文", NOW);

            assertThat(c.id()).isEqualTo(5L);
            assertThat(c.targetType()).isEqualTo(TargetType.QUESTION);
            assertThat(c.targetId()).isEqualTo(10L);
            assertThat(c.questionId()).isEqualTo(20L);
            assertThat(c.authorId()).isEqualTo(1L);
            assertThat(c.parentId()).isNull();
            assertThat(c.body()).isEqualTo("评论正文");
            assertThat(c.createdAt()).isEqualTo(NOW);

            assertThatThrownBy(() -> c.assignId(6)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class TagContract {

        @Test
        void nullInputsAreRejected() {
            assertThatThrownBy(() -> new TagName(null)).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> Tags.of(null)).isInstanceOf(BusinessRuleException.class);
        }
    }
}
