package com.campusoverflow.qa.application.command;

import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Vote;
import com.campusoverflow.qa.domain.VoteRepository;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：投票 / 改投 / 撤销。
 * value ∈ {-1, 0, 1}，0 表示撤销。
 * 事件携带旧值与新值，供下游计算声誉增量。
 */
@Service
public class VoteService {

    private final VoteRepository votes;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public VoteService(
            VoteRepository votes,
            QuestionRepository questions,
            AnswerRepository answers,
            IntegrationEventPublisher events,
            Clock clock) {
        this.votes = votes;
        this.questions = questions;
        this.answers = answers;
        this.events = events;
        this.clock = clock;
    }

    public record VoteResult(int myVote, int scoreDelta) {
    }

    private record Target(long authorId, long questionId) {
    }

    @Transactional
    public VoteResult cast(Actor actor, TargetType type, long targetId, int value) {
        if (actor == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }

        if (type == null) {
            throw new BusinessRuleException("QA-1015", "投票目标类型不能为空");
        }

        if (value != -1 && value != 0 && value != 1) {
            throw new BusinessRuleException("QA-1015", "投票值只能是 -1、0 或 1");
        }

        Target target = resolve(type, targetId);

        Optional<Vote> existing =
                votes.find(actor.userId(), type, targetId);

        int oldValue = existing.map(Vote::value).orElse(0);

        // 重复提交相同投票时不重复更新分数，也不重复发布事件。
        if (oldValue == value) {
            return new VoteResult(oldValue, 0);
        }

        Instant now = clock.instant();

        if (value == 0) {
            // 撤销已有投票。
            existing.ifPresent(votes::delete);
        } else if (existing.isPresent()) {
            // 修改已有投票。
            Vote vote = existing.get();
            vote.change(value, now);
            votes.save(vote);
        } else {
            // 创建新投票。领域对象负责禁止给自己的内容投票。
            Vote vote = Vote.cast(
                    actor.userId(),
                    target.authorId(),
                    type,
                    targetId,
                    value,
                    now);

            votes.save(vote);
        }

        int scoreDelta = value - oldValue;

        if (type == TargetType.QUESTION) {
            questions.adjustScore(targetId, scoreDelta);
        } else {
            answers.adjustScore(targetId, scoreDelta);
        }

        events.publish(new VoteCastEvent(
                EventIds.next(),
                now,
                type.name(),
                targetId,
                target.authorId(),
                actor.userId(),
                target.questionId(),
                oldValue,
                value));

        return new VoteResult(value, scoreDelta);
    }

    private Target resolve(TargetType type, long targetId) {
        if (targetId <= 0) {
            throw new BusinessRuleException("QA-1015", "投票目标 ID 无效");
        }

        if (type == TargetType.QUESTION) {
            Question question = questions.findById(targetId)
                    .orElseThrow(() ->
                            new NotFoundException(
                                    "QA-1004", "问题不存在，ID：" + targetId));

            if (question.isDeleted()) {
                throw new BusinessRuleException("QA-1015", "不能给已删除的问题投票");
            }

            return new Target(question.authorId(), question.id());
        }

        if (type == TargetType.ANSWER) {
            Answer answer = answers.findById(targetId)
                    .orElseThrow(() ->
                            new NotFoundException(
                                    "QA-1004", "回答不存在，ID：" + targetId));

            if (answer.isDeleted()) {
                throw new BusinessRuleException("QA-1015", "不能给已删除的回答投票");
            }

            // 通过问题确认回答所属关系，并取得所属问题 ID。
            Question question = questions.findById(answer.questionId())
                    .orElseThrow(() ->
                            new NotFoundException(
                                    "QA-1004", "回答所属的问题不存在"));

            if (question.isDeleted()) {
                throw new BusinessRuleException("QA-1015", "不能给已删除问题下的回答投票");
            }

            return new Target(answer.authorId(), question.id());
        }

        throw new BusinessRuleException("QA-1015", "不支持的投票目标类型");
    }
}
