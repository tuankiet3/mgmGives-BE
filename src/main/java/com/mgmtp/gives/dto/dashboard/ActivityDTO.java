package com.mgmtp.gives.dto.dashboard;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ActivityDTO {
    private String id;
    private String type; // "NOTIFICATION" or "ANNOUNCEMENT"
    private String title;
    private String message;
    private String linkUrl;
    private LocalDateTime createdAt;
}
