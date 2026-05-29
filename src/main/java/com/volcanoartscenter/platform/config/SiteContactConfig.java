package com.volcanoartscenter.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SiteContactProperties.class)
public class SiteContactConfig {
}
