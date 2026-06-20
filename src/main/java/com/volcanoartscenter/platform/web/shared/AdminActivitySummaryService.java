package com.volcanoartscenter.platform.web.shared;

import com.volcanoartscenter.platform.shared.messaging.MessagingService;
import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.ContactInquiry;
import com.volcanoartscenter.platform.shared.model.Review;
import com.volcanoartscenter.platform.shared.model.ShippingOrder;
import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.model.TourOperatorRequest;
import com.volcanoartscenter.platform.shared.repository.BookingRepository;
import com.volcanoartscenter.platform.shared.repository.ContactInquiryRepository;
import com.volcanoartscenter.platform.shared.repository.ReviewRepository;
import com.volcanoartscenter.platform.shared.repository.ShippingOrderRepository;
import com.volcanoartscenter.platform.shared.repository.TalentApplicationRepository;
import com.volcanoartscenter.platform.shared.repository.TourOperatorRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminActivitySummaryService {

    private final BookingRepository bookingRepository;
    private final ShippingOrderRepository shippingOrderRepository;
    private final ContactInquiryRepository contactInquiryRepository;
    private final TourOperatorRequestRepository tourOperatorRequestRepository;
    private final TalentApplicationRepository talentApplicationRepository;
    private final ReviewRepository reviewRepository;
    private final MessagingService messagingService;

    @Transactional(readOnly = true)
    public AdminActivitySummary summarize() {
        long pendingBookings = bookingRepository.countByStatus(Booking.BookingStatus.PENDING);
        long pendingOrders = shippingOrderRepository.countByStatusIn(java.util.List.of(
                ShippingOrder.OrderStatus.PENDING, ShippingOrder.OrderStatus.PROCESSING));
        long openInquiries = contactInquiryRepository.countByStatusNot(ContactInquiry.InquiryStatus.CLOSED);
        long openOperatorRequests = tourOperatorRequestRepository.countByStatusNotIn(java.util.List.of(
                TourOperatorRequest.RequestStatus.CONFIRMED, TourOperatorRequest.RequestStatus.DECLINED));
        long pendingTalentApplications = talentApplicationRepository.countByStatusIn(java.util.List.of(
                TalentApplication.ApplicationStatus.PENDING, TalentApplication.ApplicationStatus.AWAITING_INFO));
        long pendingReviews = reviewRepository.countByApprovedFalse();
        long awaitingStaffMessages = messagingService.countAwaitingStaffThreads();

        return new AdminActivitySummary(
                pendingBookings,
                pendingOrders,
                openInquiries,
                openOperatorRequests,
                pendingTalentApplications,
                pendingReviews,
                awaitingStaffMessages);
    }

    public record AdminActivitySummary(
            long pendingBookings,
            long pendingOrders,
            long openInquiries,
            long openOperatorRequests,
            long pendingTalentApplications,
            long pendingReviews,
            long awaitingStaffMessages
    ) {
        public long dashboardTotal() {
            return pendingBookings + pendingOrders + openInquiries + openOperatorRequests
                    + pendingTalentApplications + pendingReviews + awaitingStaffMessages;
        }

        static AdminActivitySummary empty() {
            return new AdminActivitySummary(0, 0, 0, 0, 0, 0, 0);
        }
    }
}
