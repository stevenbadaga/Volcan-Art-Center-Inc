package com.volcanoartscenter.platform.web.external.account;

import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.notification.InAppNotificationService;
import com.volcanoartscenter.platform.shared.notification.NotificationCategory;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountNotificationsController {

    private final InAppNotificationService notifications;
    private final UserRepository userRepository;

    public AccountNotificationsController(InAppNotificationService notifications, UserRepository userRepository) {
        this.notifications = notifications;
        this.userRepository = userRepository;
    }

    @GetMapping("/account/notifications")
    public String inbox(Authentication authentication,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) NotificationCategory category,
                        @RequestParam(defaultValue = "false") boolean unreadOnly,
                        Model model) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";

        var pageData = notifications.inbox(user.getId(), page, 20, category, unreadOnly);

        model.addAttribute("currentPage", "account-notifications");
        model.addAttribute("pageTitle", "Notification Center");
        model.addAttribute("notifications", pageData.getContent());
        model.addAttribute("hasNext", pageData.hasNext());
        model.addAttribute("hasPrev", pageData.hasPrevious());
        model.addAttribute("page", page);
        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("activeCategory", category);
        model.addAttribute("categories", NotificationCategory.values());
        model.addAttribute("unreadCount", notifications.countUnread(user.getId()));
        return "external/account/notifications";
    }

    @PostMapping("/account/notifications/{id}/read")
    public String markRead(Authentication authentication,
                           @org.springframework.web.bind.annotation.PathVariable Long id,
                           RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        notifications.markRead(user.getId(), id);
        return "redirect:/account/notifications";
    }

    @PostMapping("/account/notifications/read-all")
    public String markAllRead(Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        int updated = notifications.markAllRead(user.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Marked " + updated + " notification(s) as read.");
        return "redirect:/account/notifications";
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
