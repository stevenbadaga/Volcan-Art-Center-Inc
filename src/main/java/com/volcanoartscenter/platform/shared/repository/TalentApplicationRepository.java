package com.volcanoartscenter.platform.shared.repository;

import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TalentApplicationRepository extends JpaRepository<TalentApplication, Long> {
    List<TalentApplication> findByUserOrderByCreatedAtDesc(User user);
    List<TalentApplication> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    long countByStatusIn(java.util.Collection<com.volcanoartscenter.platform.shared.model.TalentApplication.ApplicationStatus> statuses);
}
