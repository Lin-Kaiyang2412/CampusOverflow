package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReputationQueryService {

    private final ReputationAccountRepository accounts;
    private final LedgerRepository ledger;
    private final BadgeRepository badges;
    private final IdentityApi identity;

    public ReputationQueryService(ReputationAccountRepository accounts, LedgerRepository ledger,
                                  BadgeRepository badges, IdentityApi identity) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.badges = badges;
        this.identity = identity;
    }

    public ReputationProfileView profile(long userId, Actor viewerOrNull) {
        UserSummary user = identity.findUser(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "用户不存在，id=" + userId));
        ReputationAccount account = accounts.findByUserId(userId)
                .orElseGet(() -> ReputationAccount.open(userId, 0));
        boolean canSeePoints = viewerOrNull != null && (viewerOrNull.is(userId) || viewerOrNull.isAdmin());
        var badgeViews = badges.findByUser(userId).stream()
                .map(badge -> new ReputationProfileView.BadgeView(badge.type().name(), badge.type().displayName(),
                        badge.type().description(), badge.awardedAt()))
                .toList();
        return new ReputationProfileView(userId, user.displayName(), account.reputation(),
                canSeePoints ? account.availablePoints() : null, canSeePoints ? account.frozenPoints() : null,
                badgeViews);
    }

    /** 声誉流水属于个人数据：仅本人与管理员可查看。 */
    public PageResult<LedgerEntryView> ledger(long userId, Actor viewer, PageRequest page) {
        if (!viewer.is(userId) && !viewer.isAdmin()) {
            throw new ForbiddenException("REPUTATION_LEDGER_FORBIDDEN", "只能查看本人的声誉流水");
        }
        return ledger.findByUser(userId, page).map(entry -> new LedgerEntryView(entry.id(), entry.reason().name(),
                entry.reason().label(), entry.delta(), entry.refType(), entry.refId(), entry.createdAt()));
    }
}
