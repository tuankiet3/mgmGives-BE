package com.mgmtp.gives.common;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProps {
    private String frontendUrl;
    private String fromMail;
    private long verifyExpiration;
}
