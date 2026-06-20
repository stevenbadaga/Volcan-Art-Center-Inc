package com.volcanoartscenter.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Configuration
public class GoogleOAuthClientConfig {

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${platform.security.oauth.google.client-id:}") String clientId,
            @Value("${platform.security.oauth.google.client-secret:}") String clientSecret,
            @Value("${platform.security.oauth.google.redirect-uri:{baseUrl}/login/oauth2/code/{registrationId}}") String redirectUri
    ) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return new InMemoryClientRegistrationRepository(Collections.emptyMap());
        }

        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientName("Google")
                .clientId(clientId.trim())
                .clientSecret(clientSecret.trim())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .scope("openid", "profile", "email")
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }
}
