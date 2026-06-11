package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User extends BaseEntity {

    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    private String email;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

//    @OneToMany(mappedBy = "user")
//    private List<PasswordResetToken> passwordResetTokens = new ArrayList<>();
//
//    @OneToMany(mappedBy = "user")
//    private List<RefreshToken> refreshTokens = new ArrayList<>();
//
//    @OneToMany(mappedBy = "user")
//    private List<Notification> notifications  = new ArrayList<>();
//
//    @OneToMany(mappedBy = "user")
//     private List<Donation> donations = new ArrayList<>();
//
//    @OneToMany(mappedBy = "user")
//    private List<CampaignMember> campaignMembers = new ArrayList<>();
//
//    @OneToMany(mappedBy = "user")
//    private List<Campaign> campaigns = new ArrayList<>();
//
//    @OneToMany(mappedBy = "createdBy")
//    private List<Announcement> announcements = new ArrayList<>();
//
//    @OneToMany(mappedBy = "user")
//    private List<TaskAssignment> taskAssignments = new ArrayList<>();


}
