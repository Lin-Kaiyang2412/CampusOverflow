package com.campusoverflow.qa.application.query;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.application.port.ViewCounter;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问答查询用例（读路径）。 */
@Service
@Transactional(readOnly = true)
public class QuestionQueryService {

    private static final int EXCERPT_LENGTH = 160;

    private final QaReadModel readModel;
    private final IdentityApi identity;
    private final ContentRenderer renderer;
    private final ViewCounter viewCounter;
    private final QuestionListCache cache;

    public QuestionQueryService(QaReadModel readModel, IdentityApi identity, ContentRenderer renderer,
                                ViewCounter viewCounter, QuestionListCache cache) {
        this.readModel = readModel;
        this.identity = identity;
        this.renderer = renderer;
        this.viewCounter = viewCounter;
        this.cache = cache;
    }

    public PageResult<QuestionSummaryView> list(Long courseId, PageRequest page) {
        var cached = cache.get(courseId, page);
        if (cached.isPresent()) {
            return cached.get();
        }

        PageResult<QuestionRow> rows = readModel.listQuestions(courseId, page);
        Map<Long, UserSummary> users = identity.findUsers(rows.items().stream().map(QuestionRow::authorId).toList());
        PageResult<QuestionSummaryView> result = rows.map(row -> new QuestionSummaryView(row.id(), row.title(),
                renderer.toPlainText(row.body(), EXCERPT_LENGTH), row.tags(), row.courseId(),
                AuthorView.of(row.authorId(), users.get(row.authorId())), row.score(), row.answerCount(),
                row.viewCount(), row.acceptedAnswerId() != null, row.createdAt()));
        cache.put(courseId, page, result);
        return result;
    }

    public QuestionDetailView detail(long questionId, Actor actorOrNull) {
        QuestionRow question = readModel.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在，id=" + questionId));
        List<AnswerRow> answerRows = readModel.findAnswers(questionId);
        List<CommentRow> commentRows = readModel.findComments(questionId);

        Set<Long> userIds = new HashSet<>();
        userIds.add(question.authorId());
        answerRows.forEach(answer -> {
            userIds.add(answer.authorId());
            if (answer.endorsedBy() != null) {
                userIds.add(answer.endorsedBy());
            }
        });
        commentRows.forEach(comment -> userIds.add(comment.authorId()));
        Map<Long, UserSummary> users = identity.findUsers(userIds);

        Map<String, Integer> myVotes = actorOrNull == null ? Map.of() : readModel.findMyVotes(actorOrNull.userId(),
                questionId, answerRows.stream().map(AnswerRow::id).toList());
        boolean canManage = actorOrNull != null && identity.canManageCourse(actorOrNull.userId(), question.courseId());
        boolean ownsQuestion = actorOrNull != null && actorOrNull.is(question.authorId());
        PermissionsView permissions = actorOrNull == null ? PermissionsView.NONE
                : new PermissionsView(ownsQuestion || canManage, canManage || (ownsQuestion && question.answerCount() == 0),
                (ownsQuestion || canManage) && question.acceptedAnswerId() == null, canManage);

        Map<String, List<CommentView>> commentsByTarget = commentRows.stream()
                .collect(Collectors.groupingBy(row -> row.targetType() + ":" + row.targetId(),
                        Collectors.mapping(row -> toCommentView(row, users), Collectors.toList())));
        List<AnswerView> answers = answerRows.stream()
                .sorted(Comparator.comparing((AnswerRow row) -> !Objects.equals(row.id(), question.acceptedAnswerId()))
                        .thenComparing(AnswerRow::score, Comparator.reverseOrder())
                        .thenComparing(AnswerRow::createdAt))
                .map(row -> {
                    boolean canEdit = actorOrNull != null && (canManage || actorOrNull.is(row.authorId()));
                    return new AnswerView(row.id(), row.body(), renderer.toSafeHtml(row.body()),
                            AuthorView.of(row.authorId(), users.get(row.authorId())), row.score(),
                            Objects.equals(row.id(), question.acceptedAnswerId()),
                            row.endorsedBy() == null ? null : AuthorView.of(row.endorsedBy(), users.get(row.endorsedBy())),
                            myVotes.getOrDefault("ANSWER:" + row.id(), 0), canEdit, row.createdAt(), row.updatedAt(),
                            commentsByTarget.getOrDefault("ANSWER:" + row.id(), List.of()));
                })
                .toList();

        CourseRefView course = question.courseId() == null ? null : identity.findCourse(question.courseId())
                .map(value -> new CourseRefView(value.id(), value.code(), value.name())).orElse(null);
        viewCounter.increment(questionId);
        return new QuestionDetailView(question.id(), question.title(), question.body(),
                renderer.toSafeHtml(question.body()), question.tags(), course,
                AuthorView.of(question.authorId(), users.get(question.authorId())), question.score(),
                question.answerCount(), question.viewCount(), question.acceptedAnswerId(),
                myVotes.getOrDefault("QUESTION:" + question.id(), 0), question.createdAt(), question.updatedAt(),
                permissions, commentsByTarget.getOrDefault("QUESTION:" + question.id(), List.of()), answers);
    }

    public List<TagView> popularTags(int limit) {
        return readModel.popularTags(Math.max(0, limit));
    }

    private CommentView toCommentView(CommentRow row, Map<Long, UserSummary> users) {
        return new CommentView(row.id(), AuthorView.of(row.authorId(), users.get(row.authorId())), row.parentId(),
                row.body(), row.createdAt());
    }
}
