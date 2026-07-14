package com.mgmtp.gives.dto.announcement;

import java.util.List;

public record ReplyPageResponse<T>(
        List<T> content,
        Long nextCursor
) {
}
