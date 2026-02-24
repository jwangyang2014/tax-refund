package com.intuit.taxrefund.auth.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    // ✅ Profile fields
    @Column(nullable = false, name = "first_name", length = 100)
    private String firstName;

    @Column(nullable = false, name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = true, name = "address", length = 255)
    private String address;

    @Column(nullable = false, name = "city", length = 100)
    private String city;

    // State required for refund timelines
    @Column(nullable = false, name = "state", length = 2)
    private String state;

    @Column(nullable = true, name = "phone", length = 30)
    private String phone;

    // ✅ read-only from API perspective; still stored in DB
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        String address,
        String city,
        String state,
        String phone,
        Role role
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.city = city;
        this.state = state;
        this.phone = phone;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    // For testing
    public void setIdForTest(Long id) {
        this.id = id;
    }

// Add these methods to AppUser.java

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}