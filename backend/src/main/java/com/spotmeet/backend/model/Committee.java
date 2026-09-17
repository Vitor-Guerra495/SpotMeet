package com.spotmeet.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * Group/committee inside an Organization.
 *
 * Rules:
 *   - The committee name cannot contain spaces (e.g. 'Financeiro', 'TI_Dev', 'RH-Geral').
 *   - Strictly bound to one Organization.
 *   - Names are unique within the same organization.
 */
@Entity
@Table(
    name = "committees",
    uniqueConstraints = @UniqueConstraint(name = "uq_committee_org_name", columnNames = {"organization_id", "name"})
)
public class Committee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome da comissao e obrigatorio.")
    @Pattern(
        regexp = "^[A-Za-z0-9_-]{2,50}$",
        message = "O nome da comissao nao pode conter espacos e deve ter entre 2 e 50 caracteres (letras, numeros, '_' ou '-')."
    )
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Committee() {}

    public Committee(String name, String description, Organization organization) {
        this.name = name;
        this.description = description;
        this.organization = organization;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
