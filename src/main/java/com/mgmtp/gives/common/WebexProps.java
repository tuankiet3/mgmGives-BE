package com.mgmtp.gives.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.webex")
public class WebexProps {
    private String clientId;
    private String clientSecret;
    private String apiBaseUrl = "https://webexapis.com/v1";
    private String redirectUri;
    private String scopes;
    private String timeZone = "Asia/Bangkok";
}
