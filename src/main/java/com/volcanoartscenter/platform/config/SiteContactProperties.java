package com.volcanoartscenter.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.site")
public class SiteContactProperties {

    private String whatsappNumber = "";
    private String inquiryMessage =
            "Hello Volcano Arts Center! I would like to inquire about your artworks, experiences, and cultural collections.";
    private String siteUrl = "https://volcanoartscenter.rw";
    private Social social = new Social();

    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }

    public String getInquiryMessage() { return inquiryMessage; }
    public void setInquiryMessage(String inquiryMessage) { this.inquiryMessage = inquiryMessage; }

    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }

    public Social getSocial() { return social; }
    public void setSocial(Social social) { this.social = social; }

    public static class Social {
        private String youtube = "";
        private String twitter = "";
        private String instagram = "";
        private String facebook = "";

        public String getYoutube() { return youtube; }
        public void setYoutube(String youtube) { this.youtube = youtube; }

        public String getTwitter() { return twitter; }
        public void setTwitter(String twitter) { this.twitter = twitter; }

        public String getInstagram() { return instagram; }
        public void setInstagram(String instagram) { this.instagram = instagram; }

        public String getFacebook() { return facebook; }
        public void setFacebook(String facebook) { this.facebook = facebook; }
    }
}
