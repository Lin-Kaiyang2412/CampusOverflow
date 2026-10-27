package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.search.RelatedQuestionPolicy;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：搜索与筛选、相关问题推荐。 */
@Service
@Transactional(readOnly = true)
public class DiscoveryQueryService {

    private final SearchPort search;
    private final SearchIndex index;
    private final IdentityApi identity;

    public DiscoveryQueryService(SearchPort search, SearchIndex index, IdentityApi identity) {
        this.search = search;
        this.index = index;
        this.identity = identity;
    }

    public PageResult<SearchResultView> search(SearchQuery query) {
        PageResult<SearchHit> hits = search.search(query);
        Map<Long, UserSummary> users = identity.findUsers(hits.items().stream().map(SearchHit::authorId).toList());
        return hits.map(hit -> {
            UserSummary user = users.get(hit.authorId());
            var author = new SearchResultView.Author(hit.authorId(),
                    user == null ? "已注销用户" : user.displayName(),
                    user == null ? "STUDENT" : user.role().name(), user != null && user.verified());
            return new SearchResultView(hit.questionId(), hit.title(), hit.snippet(), hit.tags(), hit.courseId(), author,
                    hit.score(), hit.answerCount(), hit.accepted(), hit.bountyOpen(), hit.createdAt());
        });
    }

    public List<RelatedQuestionView> related(long questionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> ownTags = index.findTags(questionId);
        if (ownTags.isEmpty()) {
            return List.of();
        }
        var candidates = index.findCandidatesSharingTags(questionId, ownTags, Math.max(limit, limit * 3));
        return RelatedQuestionPolicy.rank(candidates, ownTags.size(), limit).stream()
                .map(candidate -> new RelatedQuestionView(candidate.questionId(), candidate.title(),
                        candidate.answerCount(), candidate.accepted(),
                        RelatedQuestionPolicy.jaccard(candidate.sharedTags(), ownTags.size(), candidate.tagCount())))
                .toList();
    }
}
