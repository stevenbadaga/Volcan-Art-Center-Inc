package com.volcanoartscenter.platform.web.shared;

import com.volcanoartscenter.platform.config.SiteContactProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record SiteContactView(
        String whatsappUrl,
        String phoneDisplay,
        String phoneUrl,
        String emailDisplay,
        String emailUrl,
        String youtubeUrl,
        String twitterUrl,
        String instagramUrl,
        String facebookUrl,
        boolean hasWhatsapp,
        boolean hasPhone,
        boolean hasEmail,
        boolean hasYoutube,
        boolean hasTwitter,
        boolean hasInstagram,
        boolean hasFacebook
) {
    public static SiteContactView from(SiteContactProperties props) {
        String message = props.getInquiryMessage() == null ? "" : props.getInquiryMessage().trim();
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String siteUrl = props.getSiteUrl() == null ? "" : props.getSiteUrl().trim();
        String encodedSite = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8);

        String phoneDisplay = formatPhoneDisplay(props.getWhatsappNumber());
        String phoneUrl = buildPhoneUrl(props.getWhatsappNumber());
        String emailDisplay = trim(props.getContactEmail());
        String emailUrl = buildEmailUrl(emailDisplay);
        String whatsappUrl = buildWhatsappUrl(props.getWhatsappNumber(), encodedMessage);
        SiteContactProperties.Social social = props.getSocial() == null ? new SiteContactProperties.Social() : props.getSocial();

        String youtubeUrl = buildYoutubeUrl(trim(social.getYoutube()));
        String twitterUrl = buildTwitterUrl(trim(social.getTwitter()), encodedMessage);
        String instagramUrl = buildInstagramUrl(trim(social.getInstagram()));
        String facebookUrl = buildFacebookUrl(trim(social.getFacebook()), encodedMessage, encodedSite);

        return new SiteContactView(
                whatsappUrl,
                phoneDisplay,
                phoneUrl,
                emailDisplay.isEmpty() ? null : emailDisplay,
                emailUrl,
                youtubeUrl,
                twitterUrl,
                instagramUrl,
                facebookUrl,
                whatsappUrl != null,
                phoneUrl != null,
                emailUrl != null,
                youtubeUrl != null,
                twitterUrl != null,
                instagramUrl != null,
                facebookUrl != null
        );
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizePhoneDigits(String number) {
        if (number == null || number.isBlank()) {
            return "";
        }
        String digits = number.replaceAll("\\D", "");
        if (digits.length() == 10 && digits.startsWith("0")) {
            return "250" + digits.substring(1);
        }
        if (digits.length() == 9) {
            return "250" + digits;
        }
        return digits;
    }

    private static String buildWhatsappUrl(String number, String encodedMessage) {
        String digits = normalizePhoneDigits(number);
        if (digits.isEmpty()) {
            return null;
        }
        return "https://wa.me/" + digits + "?text=" + encodedMessage;
    }

    private static String buildPhoneUrl(String number) {
        String digits = normalizePhoneDigits(number);
        if (digits.isEmpty()) {
            return null;
        }
        return "tel:+" + digits;
    }

    private static String buildEmailUrl(String email) {
        String normalized = trim(email);
        if (normalized.isEmpty()) {
            return null;
        }
        return "mailto:" + normalized;
    }

    private static String formatPhoneDisplay(String number) {
        String digits = normalizePhoneDigits(number);
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() == 12 && digits.startsWith("250")) {
            return "+250 " + digits.substring(3, 6) + " " + digits.substring(6, 9) + " " + digits.substring(9);
        }
        return number.trim();
    }

    private static String buildYoutubeUrl(String handle) {
        if (handle.isEmpty()) {
            return null;
        }
        if (handle.startsWith("http://") || handle.startsWith("https://")) {
            return handle;
        }
        String normalized = handle.startsWith("@") ? handle : "@" + handle;
        return "https://www.youtube.com/" + normalized;
    }

    private static String buildTwitterUrl(String handle, String encodedMessage) {
        if (handle.isEmpty()) {
            return "https://twitter.com/intent/tweet?text=" + encodedMessage;
        }
        String username = handle.replace("@", "").replace("https://x.com/", "").replace("https://twitter.com/", "");
        if (username.contains("/")) {
            username = username.substring(0, username.indexOf('/'));
        }
        return "https://twitter.com/intent/tweet?text=" + encodedMessage + "&via=" + URLEncoder.encode(username, StandardCharsets.UTF_8);
    }

    private static String buildInstagramUrl(String handle) {
        if (handle.isEmpty()) {
            return null;
        }
        if (handle.startsWith("http://") || handle.startsWith("https://")) {
            return handle;
        }
        String username = handle.replace("@", "").replace("instagram.com/", "");
        return "https://www.instagram.com/" + username + "/";
    }

    private static String buildFacebookUrl(String handle, String encodedMessage, String encodedSite) {
        if (handle.isEmpty()) {
            return "https://www.facebook.com/sharer/sharer.php?quote=" + encodedMessage + "&u=" + encodedSite;
        }
        if (handle.startsWith("http://") || handle.startsWith("https://")) {
            return handle;
        }
        String page = handle.replace("@", "").replace("facebook.com/", "");
        if (page.matches("\\d+")) {
            return "https://m.me/" + page;
        }
        return "https://www.facebook.com/" + page;
    }
}
