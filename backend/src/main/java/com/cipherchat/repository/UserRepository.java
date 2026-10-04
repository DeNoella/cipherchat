package com.cipherchat.repository;

import com.cipherchat.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // Explicit ESCAPE: on PostgreSQL Hibernate otherwise renders "escape ''", so "\_" would not match "_".
    @Query("""
            select u from User u
            where u.username like concat(:prefix, '%') escape '\\' and u.username <> :exclude
            order by u.username
            """)
    List<User> searchByPrefix(@Param("prefix") String prefix, @Param("exclude") String exclude, Pageable pageable);
}
