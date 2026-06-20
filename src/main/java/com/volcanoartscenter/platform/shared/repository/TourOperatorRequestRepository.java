package com.volcanoartscenter.platform.shared.repository;

import com.volcanoartscenter.platform.shared.model.TourOperatorRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourOperatorRequestRepository extends JpaRepository<TourOperatorRequest, Long> {
    List<TourOperatorRequest> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);
    List<TourOperatorRequest> findByOwnerEmailIgnoreCaseOrderByCreatedAtDesc(String ownerEmail);
    List<TourOperatorRequest> findByContactEmailIgnoreCaseOrderByCreatedAtDesc(String contactEmail);
    long countByStatusNotIn(java.util.Collection<com.volcanoartscenter.platform.shared.model.TourOperatorRequest.RequestStatus> statuses);
}
