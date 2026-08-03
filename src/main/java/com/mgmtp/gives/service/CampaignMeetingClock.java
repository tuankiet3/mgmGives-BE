package com.mgmtp.gives.service;

import com.mgmtp.gives.common.WebexProps;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class CampaignMeetingClock {

    private final WebexProps webexProps;

    public LocalDateTime now() {
        return LocalDateTime.now(zoneId());
    }

    public ZoneId zoneId() {
        return ZoneId.of(webexProps.getTimeZone());
    }
}
