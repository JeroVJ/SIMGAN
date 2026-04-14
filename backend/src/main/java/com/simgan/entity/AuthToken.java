package com.simgan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_tokens")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ganadero_id", nullable = false)
    private Ganadero ganadero;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String token;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @Column(columnDefinition = "boolean default false")
    private Boolean isRevoked = false;

    public boolean isValid() {
        return !isRevoked && LocalDateTime.now().isBefore(expiresAt);
    }
}
