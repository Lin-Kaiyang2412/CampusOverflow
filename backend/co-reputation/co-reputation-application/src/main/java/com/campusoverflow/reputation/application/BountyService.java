package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 用例：悬赏的发起、结算、到期退款。积分冻结与悬赏状态在同一事务中变更。 */
@Service
public class BountyService {

    private final BountyRepository bounties;
    private final ReputationAccountRepository accounts;
    private final ReputationLedgerService ledgerService;
    private final QaQueryApi qa;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final AuditTrail audit;
    private final Clock clock;

    public BountyService(BountyRepository bounties, ReputationAccountRepository accounts,
                         ReputationLedgerService ledgerService, QaQueryApi qa, IdentityApi identity,
                         IntegrationEventPublisher events, AuditTrail audit, Clock clock) {
        this.bounties = bounties;
        this.accounts = accounts;
        this.ledgerService = ledgerService;
        this.qa = qa;
        this.identity = identity;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public long open(Actor actor, long questionId, int points, int days) {
        if (bounties.findOpenByQuestion(questionId).isPresent()) {
            throw new ConflictException("REP-2001", "该问题已有进行中的悬赏");
        }
        QuestionRef question = qa.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在，id=" + questionId));
        boolean managesCourse = identity.canManageCourse(actor.userId(), question.courseId());
        Instant now = clock.instant();
        Bounty bounty = Bounty.open(questionId, !question.deleted() && question.acceptedAnswerId() == null,
                actor, managesCourse, points, days, now);
        ReputationAccount sponsor = accounts.findByUserId(actor.userId())
                .orElseThrow(() -> new NotFoundException("REP-4001", "声誉账户不存在"));
        sponsor.freezePoints(points);
        accounts.save(sponsor);
        // 先查后写存在并发窗口：两个请求可能同时通过上面的检查。数据库唯一索引
        // uk_bounty_question_open 是最终裁决，命中即翻译为业务冲突并整体回滚（冻结的积分不扣）。
        Bounty saved;
        try {
            saved = bounties.save(bounty);
        } catch (DataIntegrityViolationException ex) {
            if (exceptionChainMessages(ex).contains("uk_bounty_question_open")) {
                throw new ConflictException("REP-2001", "该问题已有进行中的悬赏");
            }
            throw ex;
        }
        events.publish(new BountyOpenedEvent(EventIds.next(), now, saved.id(), questionId, actor.userId(), points,
                saved.expiresAt()));
        audit.record("BOUNTY_OPENED", actor.userId(), "BOUNTY", String.valueOf(saved.id()),
                "questionId=" + questionId + ", points=" + points);
        return saved.id();
    }

    /** 由 AnswerAccepted 事件触发（在处理器事务内）。幂等：悬赏已结束则直接返回。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleOnAccept(long questionId, long answerId, long winnerId, String sourceEventId) {
        var openBounty = bounties.findOpenByQuestion(questionId);
        if (openBounty.isEmpty()) {
            return;
        }
        Bounty bounty = openBounty.get();
        ReputationAccount sponsor = accounts.findByUserId(bounty.sponsorId())
                .orElseThrow(() -> new NotFoundException("REP-4001", "悬赏发起人账户不存在"));
        Instant now = clock.instant();
        if (winnerId == bounty.sponsorId() || bounty.isDue(now)) {
            closeAndRefund(bounty, sponsor, now);
            return;
        }

        bounty.award(answerId, winnerId, now);
        sponsor.spendFrozen(bounty.points());
        accounts.save(sponsor);
        bounties.save(bounty);
        ledgerService.record(winnerId, sourceEventId, ReputationReason.BOUNTY_AWARDED, bounty.points(),
                "BOUNTY", bounty.id());
        events.publish(new BountyAwardedEvent(EventIds.next(), now, bounty.id(), questionId, bounty.sponsorId(),
                winnerId, bounty.points()));
    }

    /** 到期未采纳的悬赏：关闭并退回冻结积分。由定时任务调用。 */
    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        List<Bounty> due = bounties.findDue(now, 100);
        for (Bounty bounty : due) {
            ReputationAccount sponsor = accounts.findByUserId(bounty.sponsorId())
                    .orElseThrow(() -> new NotFoundException("REP-4001", "悬赏发起人账户不存在"));
            closeAndRefund(bounty, sponsor, now);
        }
        return due.size();
    }

    @Transactional(readOnly = true)
    public Optional<BountyView> latest(long questionId) {
        return bounties.findLatestByQuestion(questionId).map(bounty -> {
            String sponsorName = identity.findUser(bounty.sponsorId()).map(UserSummary::displayName)
                    .orElse("已注销用户");
            return new BountyView(bounty.id(), questionId, bounty.sponsorId(), sponsorName, bounty.points(),
                    bounty.status().name(), bounty.createdAt(), bounty.expiresAt(), bounty.winnerId(),
                    bounty.answerId());
        });
    }

    private void closeAndRefund(Bounty bounty, ReputationAccount sponsor, Instant now) {
        sponsor.refundFrozen(bounty.points());
        accounts.save(sponsor);
        bounty.close(now);
        bounties.save(bounty);
        events.publish(new BountyClosedEvent(EventIds.next(), now, bounty.id(), bounty.questionId(),
                bounty.sponsorId(), bounty.points()));
    }

    /** 收集异常链上所有 message，按约束名匹配（驱动与方言不同，message 是稳定交集）。 */
    private static String exceptionChainMessages(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null && sb.length() < 4096; t = t.getCause()) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append('\n');
            }
        }
        return sb.toString();
    }
}
