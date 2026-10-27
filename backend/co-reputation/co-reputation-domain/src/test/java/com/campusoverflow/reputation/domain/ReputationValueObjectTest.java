package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 声誉值对象的契约测试：枚举标签与投票判定、账本流水的构造守卫。
 * 这些类型此前无任何测试覆盖（Issue #12 覆盖率门禁的前置项）。
 */
class ReputationValueObjectTest {

    private static final Instant NOW = Instant.parse("2026-10-27T02:00:00Z");

    @Test
    void everyReasonCarriesChineseLabelAndVoteFlag() {
        assertThat(ReputationReason.ANSWER_ACCEPTED.label()).isEqualTo("回答被采纳");
        assertThat(ReputationReason.QUESTION_VOTE.label()).isEqualTo("问题被投票");
        assertThat(ReputationReason.ANSWER_VOTE.label()).isEqualTo("回答被投票");
        assertThat(ReputationReason.BOUNTY_AWARDED.label()).isEqualTo("赢得悬赏");

        assertThat(ReputationReason.QUESTION_VOTE.isVote()).isTrue();
        assertThat(ReputationReason.ANSWER_VOTE.isVote()).isTrue();
        assertThat(ReputationReason.ANSWER_ACCEPTED.isVote()).isFalse();
        assertThat(ReputationReason.BOUNTY_AWARDED.isVote()).isFalse();
    }

    @Test
    void ledgerEntryFactoryAssignsNoIdAndKeepsAllFields() {
        LedgerEntry entry = LedgerEntry.of(7, "evt-1", ReputationReason.ANSWER_VOTE, 10, "ANSWER", 42, NOW);

        assertThat(entry.id()).isNull();
        assertThat(entry.userId()).isEqualTo(7);
        assertThat(entry.eventId()).isEqualTo("evt-1");
        assertThat(entry.reason()).isEqualTo(ReputationReason.ANSWER_VOTE);
        assertThat(entry.delta()).isEqualTo(10);
        assertThat(entry.refType()).isEqualTo("ANSWER");
        assertThat(entry.refId()).isEqualTo(42);
        assertThat(entry.createdAt()).isEqualTo(NOW);
    }

    @Test
    void ledgerEntryRejectsMissingIdempotencyKeys() {
        assertThatThrownBy(() -> LedgerEntry.of(7, null, ReputationReason.ANSWER_VOTE, 10, "ANSWER", 42, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LedgerEntry.of(7, "evt-1", null, 10, "ANSWER", 42, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void badgeAwardKeepsWhoAndWhen() {
        BadgeAward award = new BadgeAward(7, BadgeType.FIRST_ANSWER, NOW);

        assertThat(award.userId()).isEqualTo(7);
        assertThat(award.type()).isEqualTo(BadgeType.FIRST_ANSWER);
        assertThat(award.awardedAt()).isEqualTo(NOW);
    }
}
