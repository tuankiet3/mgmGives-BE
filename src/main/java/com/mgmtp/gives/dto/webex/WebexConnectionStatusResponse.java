package com.mgmtp.gives.dto.webex;

import java.time.LocalDateTime;

public record WebexConnectionStatusResponse(
        boolean connected,
        String webexEmail,
        String webexPersonId,
        LocalDateTime connectedAt
) {
}
