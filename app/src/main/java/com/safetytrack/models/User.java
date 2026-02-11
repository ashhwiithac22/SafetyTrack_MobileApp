//apr/src/main/java/com/safetytrack/models/user
// apr/src/main/java/com/safetytrack/models/User.java
package com.safetytrack.models;

public class User {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String password;
    private boolean locationPermission;
    private long timestamp;

    // Default constructor required for Firebase
    public User() {
    }

    // Updated constructor with email parameter
    public User(String name, String email, String phone, String password, boolean locationPermission) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.locationPermission = locationPermission;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isLocationPermission() { return locationPermission; }
    public void setLocationPermission(boolean locationPermission) {
        this.locationPermission = locationPermission;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}