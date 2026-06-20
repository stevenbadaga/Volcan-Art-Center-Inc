package com.volcanoartscenter.platform.web.external.account;

import com.volcanoartscenter.platform.shared.exception.PlatformException;
import com.volcanoartscenter.platform.shared.messaging.Conversation;
import com.volcanoartscenter.platform.shared.messaging.Message;
import com.volcanoartscenter.platform.shared.messaging.MessageSenderRole;
import com.volcanoartscenter.platform.shared.messaging.MessagingService;
import com.volcanoartscenter.platform.shared.model.Product;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.ProductRepository;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AccountMessagesController {

    private final MessagingService messaging;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public AccountMessagesController(MessagingService messaging,
                                     UserRepository userRepository,
                                     ProductRepository productRepository) {
        this.messaging = messaging;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("/account/messages")
    public String inbox(Authentication authentication,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";

        var pageData = messaging.inboxForUser(user.getId(), page, 20);

        Map<Long, String> productNames = productNamesFor(pageData.getContent());

        model.addAttribute("currentPage", "account-messages");
        model.addAttribute("pageTitle", "My Messages — Volcano Arts Center");
        model.addAttribute("conversations", pageData.getContent());
        model.addAttribute("productNames", productNames);
        model.addAttribute("page", page);
        model.addAttribute("hasNext", pageData.hasNext());
        model.addAttribute("hasPrev", pageData.hasPrevious());
        return "external/account/messages/inbox";
    }

    @GetMapping("/account/messages/{id}")
    public String thread(Authentication authentication, @PathVariable Long id, Model model) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";

        Conversation c = messaging.getConversation(id);
        if (!c.getOpenedByUserId().equals(user.getId())) {
            return "redirect:/account/messages";
        }
        messaging.markThreadReadFor(id, MessageSenderRole.CLIENT);
        List<Message> messages = messaging.messagesFor(id);
        Map<Long, String> senderNames = senderNamesFor(messages);
        Product product = c.getProductId() != null
                ? productRepository.findById(c.getProductId()).orElse(null) : null;

        model.addAttribute("currentPage", "account-messages");
        model.addAttribute("pageTitle", c.getSubject() + " — Volcano Arts Center");
        model.addAttribute("conversation", c);
        model.addAttribute("messages", messages);
        model.addAttribute("senderNames", senderNames);
        model.addAttribute("product", product);
        return "external/account/messages/thread";
    }

    @PostMapping("/account/messages/{id}/reply")
    public String reply(Authentication authentication, @PathVariable Long id,
                        @RequestParam String body, RedirectAttributes redirect) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        try {
            messaging.reply(user, id, body, MessageSenderRole.CLIENT);
        } catch (PlatformException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/account/messages/" + id;
    }

    @PostMapping("/account/messages/new")
    public String newConversation(Authentication authentication,
                                  @RequestParam(required = false) String subject,
                                  @RequestParam String body,
                                  RedirectAttributes redirect) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        try {
            Conversation c = messaging.openConversation(user, null, subject, body);
            redirect.addFlashAttribute("successMessage", "Message sent. Our team will respond shortly.");
            return "redirect:/account/messages/" + c.getId();
        } catch (Exception ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/client/dashboard";
        }
    }

    private User currentUser(Authentication a) {
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getName())) return null;
        return userRepository.findByEmail(a.getName()).orElse(null);
    }

    private Map<Long, String> productNamesFor(List<Conversation> convs) {
        List<Long> ids = convs.stream()
                .map(Conversation::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        return productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
    }

    private Map<Long, String> senderNamesFor(List<Message> messages) {
        Map<Long, String> map = new HashMap<>();
        for (Message m : messages) {
            map.computeIfAbsent(m.getSenderUserId(),
                    uid -> userRepository.findById(uid).map(User::getFullName).orElse("Unknown"));
        }
        return map;
    }
}
