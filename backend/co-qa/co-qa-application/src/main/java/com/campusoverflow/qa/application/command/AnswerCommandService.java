package com.campusoverflow.qa.application.command;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：提交、编辑、删除回答，采纳最佳答案，以及教师认证。
 */
@Service
public class AnswerCommandService {

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final QuestionListCache listCache;
    private final AuditTrail audit;
    private final Clock clock;

    public AnswerCommandService(
            QuestionRepository questions,
            AnswerRepository answers,
            IdentityApi identity,
            IntegrationEventPublisher events,
            QuestionListCache listCache,
            AuditTrail audit,
            Clock clock) {
        this.questions = questions;
        this.answers = answers;
        this.identity = identity;
        this.events = events;
        this.listCache = listCache;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 提交回答。
     */
    @Transactional
    public long submit(Actor actor, long questionId, String body) {
        Question question = loadQuestion(questionId);

        // 已删除或关闭的问题不能继续回答。
        question.ensureOpenForAnswers();

        Instant now = clock.instant();
        MarkdownBody answerBody = MarkdownBody.ofAnswer(body);
        Answer answer = Answer.submit(question, actor.userId(), answerBody, now);

        Answer savedAnswer = answers.save(answer);

        // 通过仓储原子更新回答数，避免覆盖并发请求的计数结果。
        questions.adjustAnswerCount(questionId, 1);

        events.publish(new AnswerSubmittedEvent(
                EventIds.next(),
                now,
                savedAnswer.id(),
                question.id(),
                savedAnswer.authorId(),
                question.authorId(),
                question.title().value()
        ));

        listCache.invalidateAll();

        return savedAnswer.id();
    }

    /**
     * 编辑回答。
     */
    @Transactional
    public void edit(Actor actor, long answerId, String body) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());

        boolean actorManagesCourse =
                identity.canManageCourse(actor.userId(), question.courseId());

        answer.edit(
                actor,
                actorManagesCourse,
                MarkdownBody.ofAnswer(body),
                clock.instant()
        );

        answers.save(answer);
        listCache.invalidateAll();
    }

    /**
     * 删除回答。
     */
    @Transactional
    public void delete(Actor actor, long answerId) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());

        boolean actorManagesCourse =
                identity.canManageCourse(actor.userId(), question.courseId());

        boolean accepted = question.isAccepted(answerId);
        Instant now = clock.instant();

        // 领域对象负责校验删除权限和已采纳回答的删除规则。
        answer.softDelete(actor, actorManagesCourse, accepted, now);

        answers.save(answer);
        questions.adjustAnswerCount(question.id(), -1);

        events.publish(new ContentDeletedEvent(
                EventIds.next(),
                now,
                "ANSWER",
                answer.id(),
                question.id()
        ));

        audit.record(
                "ANSWER_DELETED",
                actor.userId(),
                "ANSWER",
                String.valueOf(answer.id()),
                "questionId=" + question.id()
        );

        listCache.invalidateAll();
    }

    /**
     * 采纳最佳答案。
     * 只有提问者或课程管理者可以采纳，具体权限规则由 Question 聚合根校验。
     */
    @Transactional
    public void accept(Actor actor, long questionId, long answerId) {
        Question question = loadQuestion(questionId);
        Answer answer = loadAnswer(answerId);

        if (answer.questionId() != questionId) {
            throw new NotFoundException(
                    "ANSWER_NOT_FOUND",
                    "该回答不属于指定问题"
            );
        }

        boolean actorManagesCourse =
                identity.canManageCourse(actor.userId(), question.courseId());

        Instant now = clock.instant();

        question.accept(answer, actor, actorManagesCourse);
        questions.save(question);

        events.publish(new AnswerAcceptedEvent(
                EventIds.next(),
                now,
                answer.id(),
                question.id(),
                answer.authorId(),
                question.authorId(),
                actor.userId(),
                question.title().value()
        ));

        audit.record(
                "ANSWER_ACCEPTED",
                actor.userId(),
                "ANSWER",
                String.valueOf(answer.id()),
                "questionId=" + question.id()
        );

        listCache.invalidateAll();
    }

    /**
     * 教师认证回答。
     */
    @Transactional
    public void endorse(Actor actor, long answerId) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());

        boolean actorManagesCourse =
                identity.canManageCourse(actor.userId(), question.courseId());

        answer.endorse(actor, actorManagesCourse);
        answers.save(answer);

        Instant now = clock.instant();

        events.publish(new AnswerEndorsedEvent(
                EventIds.next(),
                now,
                answer.id(),
                question.id(),
                answer.authorId(),
                actor.userId(),
                question.title().value()
        ));

        listCache.invalidateAll();
    }

    /**
     * 根据 ID 获取问题；不存在时抛出领域异常。
     */
    private Question loadQuestion(long id) {
        return questions.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "QUESTION_NOT_FOUND",
                        "问题不存在，id=" + id
                ));
    }

    /**
     * 根据 ID 获取回答；不存在时抛出领域异常。
     */
    private Answer loadAnswer(long id) {
        return answers.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "ANSWER_NOT_FOUND",
                        "回答不存在，id=" + id
                ));
    }
}
