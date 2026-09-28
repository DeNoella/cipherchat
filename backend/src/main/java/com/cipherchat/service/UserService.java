package com.cipherchat.service;

import com.cipherchat.dto.UserSummary;
import com.cipherchat.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final int MAX_RESULTS = 20;

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Prefix search excluding the caller. The query is pre-validated to [A-Za-z0-9_]. */
    @Transactional(readOnly = true)
    public List<UserSummary> search(String query, String excludeUsername) {
        String prefix = AuthService.normalizeUsername(query).replace("_", "\\_");
        return users.searchByPrefix(prefix, excludeUsername, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(UserSummary::from)
                .toList();
    }
}
