package com.melina.projectflow.user.model;

import com.melina.projectflow.common.model.MutableEntity;
import com.melina.projectflow.plancontainer.project.model.ProjectMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "app_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_users_email", columnNames = "email")
)
@Getter
@Setter
@NoArgsConstructor
public class User extends MutableEntity {

    @Email
    @NotBlank
    @Size(max = 254)
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "user")
    private Set<ProjectMember> memberships = new LinkedHashSet<>();

    public void addMembership(ProjectMember membership) {
        memberships.add(membership);
        membership.setUser(this);
    }

    public void removeMembership(ProjectMember membership) {
        memberships.remove(membership);
        if (membership.getUser() == this) {
            membership.setUser(null);
        }
    }
}
