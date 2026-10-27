package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import java.time.Duration;
import java.time.Instant;

public class Bounty {

    public static final int MIN_POINTS = 10;
    public static final int MAX_POINTS = 500;
    public static final int MAX_DAYS = 14;

    private Long id;
    private final long questionId;
    private final long sponsorId;
    private final int points;
    private BountyStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Long winnerId;
    private Long answerId;
    private Instant closedAt;

    private Bounty(Long id, long questionId, long sponsorId, int points,
                   BountyStatus status, Instant createdAt, Instant expiresAt,
                   Long winnerId, Long answerId, Instant closedAt) {
        this.id = id;
        this.questionId = questionId;
        this.sponsorId = sponsorId;
        this.points = points;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.winnerId = winnerId;
        this.answerId = answerId;
        this.closedAt = closedAt;
    }

    public static Bounty open(long questionId, boolean questionOpen,
                              Actor sponsor, boolean sponsorManagesCourse,
                              int points, int days, Instant now) {
        if (sponsor == null || !sponsorManagesCourse
                || !(sponsor.role() == com.campusoverflow.shared.security.Role.TEACHER
                || sponsor.role() == com.campusoverflow.shared.security.Role.TA
                || sponsor.role() == com.campusoverflow.shared.security.Role.ADMIN)) {
            throw new ForbiddenException("REP-3001", "只有课程管理者可以发起悬赏");
        }

        if (questionId <= 0 || !questionOpen) {
            throw new BusinessRuleException("REP-1011", "问题不存在、已删除或已被采纳");
        }

        if (points < MIN_POINTS || points > MAX_POINTS) {
            throw new BusinessRuleException("REP-1003", "悬赏积分必须在 10 到 500 之间");
        }

        if (days < 1 || days > MAX_DAYS) {
            throw new BusinessRuleException("REP-1004", "悬赏期限必须在 1 到 14 天之间");
        }

        if (now == null) {
            throw new BusinessRuleException("REP-1005", "发起时间不能为空");
        }

        return new Bounty(null, questionId, sponsor.userId(), points,
                BountyStatus.OPEN, now, now.plus(Duration.ofDays(days)),
                null, null, null);
    }

    public static Bounty reconstitute(Long id, long questionId, long sponsorId,
                                      int points, BountyStatus status,
                                      Instant createdAt, Instant expiresAt,
                                      Long winnerId, Long answerId, Instant closedAt) {
        return new Bounty(id, questionId, sponsorId, points, status,
                createdAt, expiresAt, winnerId, answerId, closedAt);
    }

    public void award(long answerId, long winnerId, Instant now) {
        ensureOpen();

        if (answerId <= 0 || winnerId <= 0 || now == null) {
            throw new BusinessRuleException("REP-1006", "获奖回答、用户或时间不合法");
        }

        if (winnerId == sponsorId) {
            throw new BusinessRuleException("REP-1007", "发起人不能获得自己的悬赏");
        }

        if (isDue(now)) {
            throw new ConflictException("REP-2002", "悬赏已到期，不能再结算");
        }

        this.answerId = answerId;
        this.winnerId = winnerId;
        this.status = BountyStatus.AWARDED;
        this.closedAt = now;
    }

    /**
     * 关闭悬赏。调用方应先确认悬赏到期，或采纳的是发起人自己的回答。
     */
    public void close(Instant now) {
        ensureOpen();

        if (now == null) {
            throw new BusinessRuleException("REP-1005", "关闭时间不能为空");
        }

        this.status = BountyStatus.EXPIRED;
        this.closedAt = now;
    }

    public boolean isDue(Instant now) {
        return now != null && !now.isBefore(expiresAt);
    }

    private void ensureOpen() {
        if (status != BountyStatus.OPEN) {
            throw new ConflictException("REP-2003", "悬赏已结束，不能重复操作");
        }
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID 已分配");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("ID 必须大于零");
        }
        this.id = id;
    }

    public Long id() { return id; }
    public long questionId() { return questionId; }
    public long sponsorId() { return sponsorId; }
    public int points() { return points; }
    public BountyStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Long winnerId() { return winnerId; }
    public Long answerId() { return answerId; }
    public Instant closedAt() { return closedAt; }
}
