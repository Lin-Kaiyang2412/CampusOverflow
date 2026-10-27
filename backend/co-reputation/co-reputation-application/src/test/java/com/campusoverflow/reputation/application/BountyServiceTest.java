package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 悬赏并发的应用层保障：唯一键冲突翻译为业务冲突，失败请求不持久化冻结、不发布事件。 */
class BountyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    private final BountyRepository bounties = mock(BountyRepository.class);
    private final ReputationAccountRepository accounts = mock(ReputationAccountRepository.class);
    private final ReputationLedgerService ledgerService = mock(ReputationLedgerService.class);
    private final QaQueryApi qa = mock(QaQueryApi.class);
    private final IdentityApi identity = mock(IdentityApi.class);
    private final IntegrationEventPublisher events = mock(IntegrationEventPublisher.class);
    private final AuditTrail audit = mock(AuditTrail.class);

    private final BountyService service = new BountyService(bounties, accounts, ledgerService, qa, identity,
            events, audit, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    @BeforeEach
    void setUp() {
        when(qa.findQuestion(10L)).thenReturn(Optional.of(
                new QuestionRef(10, 2, 100L, "并发悬赏测试", null, false)));
        when(identity.canManageCourse(9L, 100L)).thenReturn(true);
        when(accounts.findByUserId(9L)).thenReturn(Optional.of(ReputationAccount.open(9, 1000)));
        when(bounties.findOpenByQuestion(10L)).thenReturn(Optional.empty());
    }

    @Test
    void duplicateOpenViaUniqueViolationBecomesConflict() {
        // 模拟并发窗口：先查后写都通过，第二个请求在写入时被唯一键 uk_bounty_question_open 拒绝。
        when(bounties.save(any(Bounty.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Duplicate entry '10-1' for key 'rep_bounty.uk_bounty_question_open']"));

        assertThatThrownBy(() -> service.open(teacher, 10L, 50, 7))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getMessage())
                        .isEqualTo("该问题已有进行中的悬赏"));

        // 失败请求：悬赏事件不发布；冻结的账户变更随 @Transactional 整体回滚（应用层只负责抛出业务冲突）。
        verify(events, never()).publish(any());
    }

    @Test
    void unknownConstraintIsRethrownUntouched() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("could not execute statement [fk violation]");
        when(bounties.save(any(Bounty.class))).thenThrow(original);

        assertThatThrownBy(() -> service.open(teacher, 10L, 50, 7)).isSameAs(original);
        verify(events, never()).publish(any());
    }

    @Test
    void precheckRejectsSecondOpenBeforeFreezing() {
        Bounty existing = Bounty.open(10, true, teacher, true, 50, 7, NOW);
        when(bounties.findOpenByQuestion(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.open(teacher, 10L, 50, 7))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getMessage())
                        .isEqualTo("该问题已有进行中的悬赏"));

        // 预检查命中时不应触碰账户与事件（基本类型参数用 anyLong 匹配，避免装箱匹配器空转）。
        verify(accounts, never()).findByUserId(anyLong());
        verify(events, never()).publish(any());
    }
}
