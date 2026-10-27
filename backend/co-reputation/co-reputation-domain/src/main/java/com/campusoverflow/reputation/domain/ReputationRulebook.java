package com.campusoverflow.reputation.domain;

import com.campusoverflow.shared.security.Role;

public final class ReputationRulebook {

    public static final int ACCEPTED = 15;
    public static final int UPVOTE = 10;
    public static final int DOWNVOTE = -2;
    public static final int DAILY_VOTE_CAP = 200;

    private ReputationRulebook() {
    }

    public static int voteEffect(int value) {
        return switch (value) {
            case 1 -> UPVOTE;
            case -1 -> DOWNVOTE;
            case 0 -> 0;
            default -> throw new IllegalArgumentException("投票值必须为 -1、0 或 1");
        };
    }

    public static int voteDelta(int oldValue, int newValue) {
        return voteEffect(newValue) - voteEffect(oldValue);
    }

    public static int acceptedDelta(long answerAuthorId, long questionAuthorId) {
        return answerAuthorId == questionAuthorId ? 0 : ACCEPTED;
    }

    public static int applyDailyCap(int delta, int positiveVoteGainToday) {
        if (delta <= 0) {
            return delta;
        }
        int remaining = Math.max(0, DAILY_VOTE_CAP - positiveVoteGainToday);
        return Math.min(delta, remaining);
    }

    public static int initialPoints(Role role) {
        return role == Role.TEACHER ? 1000 : 0;
    }
}
