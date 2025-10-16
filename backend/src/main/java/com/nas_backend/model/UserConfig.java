package com.nas_backend.model;

public class UserConfig {
    private String username;
    private String password;
    private String role;

    // Empty constructor is required by Jackson
    public UserConfig() {
    }

    public UserConfig(String username, String password, String role){
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Getters

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    // Setters

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
