package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：本人修改公开资料与联系方式。 */
@Service
public class UserProfileService {

    private final UserRepository users;
    private final UserQueryService userQuery;

    public UserProfileService(UserRepository users, UserQueryService userQuery) {
        this.users = users;
        this.userQuery = userQuery;
    }

    @Transactional
    public MeView update(Actor actor, UpdateProfileCommand cmd) {
        User user = users.findById(actor.userId())
                .orElseThrow(() -> new NotFoundException("ID-4001", "用户不存在"));
        user.updateProfile(cmd.displayName(), cmd.email(), cmd.college(), cmd.className());
        ensureUnique(user);
        users.save(user);
        return userQuery.me(actor.userId());
    }

    private void ensureUnique(User user) {
        users.findByDisplayName(user.displayName())
                .filter(other -> !other.id().equals(user.id()))
                .ifPresent(other -> {
                    throw new ConflictException("ID-2002", "该昵称已被使用");
                });
        users.findByEmail(user.email())
                .filter(other -> !other.id().equals(user.id()))
                .ifPresent(other -> {
                    throw new ConflictException("ID-2003", "该邮箱已被使用");
                });
    }
}
