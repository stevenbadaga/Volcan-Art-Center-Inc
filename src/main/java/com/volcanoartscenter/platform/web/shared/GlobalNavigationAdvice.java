package com.volcanoartscenter.platform.web.shared;

import com.volcanoartscenter.platform.shared.messaging.MessagingService;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.notification.InAppNotificationService;
import com.volcanoartscenter.platform.shared.notification.Notification;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalNavigationAdvice {

    private static final Set<String> STAFF_ROLES = Set.of("SUPER_ADMIN", "CONTENT_MANAGER", "OPS_MANAGER");

    private final UserRepository userRepository;
    private final InAppNotificationService inAppNotificationService;
    private final MessagingService messagingService;
    private final AdminActivitySummaryService adminActivitySummaryService;

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName());
    }

    @ModelAttribute("navContext")
    public NavContext navContext(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return NavContext.guest();
        }
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return NavContext.guest();
        }
        Set<String> roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet());
        String userName = user.getFullName();
        if (roles.contains("SUPER_ADMIN")) {
            return new NavContext(true, "Super Admin", "/admin/dashboard", userName, user.getProfileImageUrl(),
                    true, false, false, false, false, false);
        }
        if (roles.contains("CONTENT_MANAGER")) {
            return new NavContext(true, "Content Manager", "/admin/content/dashboard", userName, user.getProfileImageUrl(),
                    false, true, false, false, false, false);
        }
        if (roles.contains("OPS_MANAGER")) {
            return new NavContext(true, "Ops Manager", "/admin/ops/dashboard", userName, user.getProfileImageUrl(),
                    false, false, true, false, false, false);
        }
        if (roles.contains("TOUR_OPERATOR")) {
            return new NavContext(true, "Tour Operator", "/tour-operators/portal", userName, user.getProfileImageUrl(),
                    false, false, false, true, false, false);
        }
        if (roles.contains("TALENT_APPLICANT")) {
            return new NavContext(true, "Talent Applicant", "/talent/dashboard", userName, user.getProfileImageUrl(),
                    false, false, false, false, true, false);
        }
        if (roles.contains("REGISTERED_CLIENT")) {
            return new NavContext(true, "Registered Client", "/client/dashboard", userName, user.getProfileImageUrl(),
                    false, false, false, false, false, true);
        }
        return NavContext.guest();
    }

    @ModelAttribute("notificationSummary")
    public NotificationSummary notificationSummary(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return NotificationSummary.empty();
        }
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return NotificationSummary.empty();
        }
        long unread = inAppNotificationService.countUnread(user.getId());
        List<Notification> recent = inAppNotificationService.recent(user.getId());
        return new NotificationSummary(unread, recent);
    }

    @ModelAttribute("unreadMessageCount")
    public long unreadMessageCount(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return 0L;
        }
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return 0L;
        }
        Set<String> roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet());
        if (roles.stream().anyMatch(STAFF_ROLES::contains)) {
            return messagingService.countAwaitingStaffThreads();
        }
        return messagingService.countUnreadThreadsForClient(user.getId());
    }

    @ModelAttribute("adminActivity")
    public AdminActivitySummaryService.AdminActivitySummary adminActivity(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return AdminActivitySummaryService.AdminActivitySummary.empty();
        }
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return AdminActivitySummaryService.AdminActivitySummary.empty();
        }
        Set<String> roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet());
        if (roles.stream().noneMatch(STAFF_ROLES::contains)) {
            return AdminActivitySummaryService.AdminActivitySummary.empty();
        }
        return adminActivitySummaryService.summarize();
    }


    public record NotificationSummary(long unread, List<Notification> recent) {
        static NotificationSummary empty() { return new NotificationSummary(0L, List.of()); }
    }

    public record NavContext(
            boolean authenticated,
            String roleLabel,
            String dashboardUrl,
            String userName,
            String profileImageUrl,
            boolean superAdmin,
            boolean contentManager,
            boolean opsManager,
            boolean tourOperator,
            boolean talentApplicant,
            boolean registeredClient
    ) {
        static NavContext guest() {
            return new NavContext(false, "Guest", "/login", "Guest", null, false, false, false, false, false, false);
        }
    }
}
