package com.campusoverflow.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.infrastructure.crypto.FieldEncryptor;
import com.campusoverflow.shared.domain.ConflictException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 唯一约束并发兜底：数据库唯一索引冲突时按约束名翻译成业务错误码。 */
class UserRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-27T05:00:00Z");

    private final UserJpaRepository jpa = mock(UserJpaRepository.class);
    private final FieldEncryptor encryptor = mock(FieldEncryptor.class);
    private final UserRepositoryAdapter adapter = new UserRepositoryAdapter(jpa, encryptor);

    private User user;

    @BeforeEach
    void setUp() {
        user = User.register("20230001", "张三", "zhang@campus.edu", "hash", "学院", "一班", NOW);
        user.assignId(2L);
        when(jpa.findById(2L)).thenReturn(Optional.of(new UserJpaEntity("20230001", NOW)));
        when(encryptor.encrypt(any())).thenReturn("cipher");
        when(encryptor.digest(any())).thenReturn("digest");
    }

    @Test
    void displayNameUniqueViolationBecomesConflict2002() {
        when(jpa.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Duplicate entry '张三' for key 'id_user.uk_user_display_name']"));

        assertThatThrownBy(() -> adapter.save(user))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> {
                    ConflictException conflict = (ConflictException) ex;
                    assertThat(conflict.code()).isEqualTo("ID-2002");
                    assertThat(conflict.getMessage()).isEqualTo("该昵称已被使用");
                });
    }

    @Test
    void emailUniqueViolationBecomesConflict2003() {
        when(jpa.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement [Duplicate entry 'digest' for key 'id_user.uk_user_email_hash']"));

        assertThatThrownBy(() -> adapter.save(user))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).code()).isEqualTo("ID-2003"));
    }

    @Test
    void unknownConstraintIsRethrownUntouched() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("could not execute statement [fk violation]");
        when(jpa.saveAndFlush(any())).thenThrow(original);

        assertThatThrownBy(() -> adapter.save(user)).isSameAs(original);
    }
}
