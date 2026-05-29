package com.volcanoartscenter.platform.web.shared;

import com.volcanoartscenter.platform.config.SiteContactProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record SiteContactView(
        String whatsappUrl,
        String youtubeUrl,
        String twitterUrl,
        String instagramUrl,
        String facebookUrl,
        boolean hasWhatsapp,
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

        String whatsappUrl = buildWhatsappUrl(props.getWhatsappNumber(), encodedMessage);
        SiteContactProperties.Social social = props.getSocial() == null ? new SiteContactProperties.Social() : props.getSocial();

        String youtubeUrl = buildYoutubeUrl(trim(social.getYoutube()));
        String twitterUrl = buildTwitterUrl(trim(social.getTwitter()), encodedMessage);
        String instagramUrl = buildInstagramUrl(trim(social.getInstagram()));
        String facebookUrl = buildFacebookUrl(trim(social.getFacebook()), encodedMessage, encodedSite);

        return new SiteContactView(
                whatsappUrl,
                youtubeUrl,
                twitterUrl,
                instagramUrl,
                facebookUrl,
                whatsappUrl != null,
                youtubeUrl != null,
                twitterUrl != null,
                instagramUrl != null,
                facebookUrl != null
        );
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String buildWhatsappUrl(String number, String encodedMessage) {
        if (number == null || number.isBlank()) {
            return null;
        }
        String digits = number.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return "https://wa.me/" + digits + "?text=" + encodedMessage;
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
