package com.campusoverflow.discovery.domain.search;

import java.util.Comparator;
import java.util.List;

public final class RelatedQuestionPolicy {

    private RelatedQuestionPolicy() {
    }

    public static double jaccard(int shared, int sizeA, int sizeB) {
        if (shared < 0 || sizeA < 0 || sizeB < 0 || shared > sizeA || shared > sizeB) {
            throw new IllegalArgumentException("Invalid tag counts");
        }
        int union = sizeA + sizeB - shared;
        return union == 0 ? 0.0 : (double) shared / union;
    }

    public static List<SearchIndex.RelatedCandidate> rank(List<SearchIndex.RelatedCandidate> candidates, int ownTagCount,
                                                          int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.sharedTags() > 0)
                .sorted(Comparator
                        .comparingDouble((SearchIndex.RelatedCandidate c) ->
                                jaccard(c.sharedTags(), ownTagCount, c.tagCount()))
                        .reversed()
                        .thenComparing(SearchIndex.RelatedCandidate::accepted, Comparator.reverseOrder())
                        .thenComparing(Comparator.comparingInt(SearchIndex.RelatedCandidate::score).reversed())
                        .thenComparing(Comparator.comparingInt(SearchIndex.RelatedCandidate::answerCount).reversed())
                        .thenComparingLong(SearchIndex.RelatedCandidate::questionId))
                .limit(limit)
                .toList();
    }
}
