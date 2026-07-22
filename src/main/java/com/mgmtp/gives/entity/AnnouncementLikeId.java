package com.mgmtp.gives.entity;

import java.io.Serializable;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AnnouncementLikeId implements Serializable {
    private Long announcement;
    private Long user;
}
