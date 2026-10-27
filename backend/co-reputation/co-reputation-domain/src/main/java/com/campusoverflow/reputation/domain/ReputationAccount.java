package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.domain.BusinessRuleException;

public class ReputationAccount {

    private final long userId;
    private int reputation;
    private int availablePoints;
    private int frozenPoints;
    private final long version;

    private ReputationAccount(long userId, int reputation, int availablePoints,
                              int frozenPoints, long version) {
        this.userId = userId;
        this.reputation = reputation;
        this.availablePoints = availablePoints;
        this.frozenPoints = frozenPoints;
        this.version = version;
    }

    public static ReputationAccount open(long userId, int initialPoints) {
        if (userId <= 0 || initialPoints < 0) {
            throw new BusinessRuleException("INVALID_ACCOUNT", "账户参数不合法");
        }
        return new ReputationAccount(userId, 0, initialPoints, 0, 0);
    }

    public static ReputationAccount reconstitute(long userId, int reputation,
                                                 int availablePoints, int frozenPoints,
                                                 long version) {
        return new ReputationAccount(userId, reputation, availablePoints,
                frozenPoints, version);
    }

    public void applyReputation(int delta) {
        long result = (long) reputation + delta;
        if (result < 0 || result > Integer.MAX_VALUE) {
            throw new BusinessRuleException("INVALID_REPUTATION", "声誉值超出允许范围");
        }
        reputation = (int) result;
    }

    public void freezePoints(int points) {
        requirePositive(points);
        if (availablePoints < points) {
            throw new BusinessRuleException("INSUFFICIENT_POINTS", "可用悬赏积分不足");
        }
        availablePoints -= points;
        frozenPoints = Math.addExact(frozenPoints, points);
    }

    public void spendFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        frozenPoints -= points;
    }

    public void refundFrozen(int points) {
        requirePositive(points);
        ensureFrozen(points);
        frozenPoints -= points;
        availablePoints = Math.addExact(availablePoints, points);
    }

    private void ensureFrozen(int points) {
        if (frozenPoints < points) {
            throw new BusinessRuleException("INSUFFICIENT_FROZEN_POINTS", "冻结积分不足");
        }
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new BusinessRuleException("INVALID_POINTS", "积分必须大于零");
        }
    }

    public long userId() {
        return userId;
    }

    public int reputation() {
        return reputation;
    }

    public int availablePoints() {
        return availablePoints;
    }

    public int frozenPoints() {
        return frozenPoints;
    }

    public long version() {
        return version;
    }
}
