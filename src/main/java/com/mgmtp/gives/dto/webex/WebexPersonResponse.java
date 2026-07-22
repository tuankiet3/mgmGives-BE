package com.mgmtp.gives.dto.webex;

import java.util.List;

public record WebexPersonResponse(
        String id,
        List<String> emails,
        String displayName
) {
}
