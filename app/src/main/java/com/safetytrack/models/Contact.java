//models/Contact.java
package com.safetytrack.models;

public class Contact {
    private String id;
    private String name;
    private String phoneNumber;
    private String rawContactInfo;
    private boolean isSelected;

    public Contact() {
        // Default constructor required for Firestore
    }

    public Contact(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.isSelected = false;
    }

    public Contact(String rawContactInfo) {
        this.rawContactInfo = rawContactInfo;
        parseContactInfo(rawContactInfo);
        this.isSelected = false;
    }

    private void parseContactInfo(String rawInfo) {
        String[] parts = rawInfo.split("\n");
        if (parts.length >= 2) {
            this.name = parts[0];
            this.phoneNumber = parts[1].replaceAll("[^0-9+]", "");
        } else {
            this.name = rawInfo;
            this.phoneNumber = "";
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRawContactInfo() { return rawContactInfo; }
    public void setRawContactInfo(String rawContactInfo) {
        this.rawContactInfo = rawContactInfo;
        parseContactInfo(rawContactInfo);
    }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    @Override
    public String toString() {
        return rawContactInfo != null ? rawContactInfo : name + "\n" + phoneNumber;
    }
}