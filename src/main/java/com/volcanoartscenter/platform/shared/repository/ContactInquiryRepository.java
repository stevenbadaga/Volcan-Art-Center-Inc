package com.volcanoartscenter.platform.shared.repository;

import com.volcanoartscenter.platform.shared.model.ContactInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {
    long countByStatusNot(ContactInquiry.InquiryStatus status);
}
