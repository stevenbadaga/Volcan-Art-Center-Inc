package com.volcanoartscenter.platform.web.shared;

import com.volcanoartscenter.platform.config.SiteContactProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class SiteContactAdvice {

    private final SiteContactProperties siteContactProperties;

    @ModelAttribute("siteContact")
    public SiteContactView siteContact() {
        return SiteContactView.from(siteContactProperties);
    }
}
