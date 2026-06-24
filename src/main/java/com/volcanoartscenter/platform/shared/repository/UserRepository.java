package com.volcanoartscenter.platform.shared.repository;

import com.volcanoartscenter.platform.shared.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByClerkUserId(String clerkUserId);
    long countByRoles_Name(String roleName);
    List<User> findByRoles_NameIn(List<String> roleNames);
}
