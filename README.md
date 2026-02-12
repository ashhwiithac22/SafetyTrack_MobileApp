# 🛡️ SafetyTrack – Smart Journey Safety Application

- SafetyTrack is a real-time journey safety Android application that automatically shares the user’s live location via SMS at regular intervals and sends emergency alerts using SOS and voice-based detection.
- The application is designed to enhance personal safety during travel by ensuring continuous location updates to trusted contacts.

## 🚀 Core Features
### 🔐 1. User Authentication

- User Registration (Sign Up)
- Secure Login (Sign In)
- Email-based and Mobile Number OTP Password Reset
- Auto-login (Dashboard opens if already logged in)Firestore database integration

### 📍 2. Real-Time Location Tracking
- GPS-based location tracking using Fused Location Provider
- Generates Google Maps link
- Continuous location updates during active journey for every 2 minutes
- Works even without internet (GPS + SMS)

### 📲 3. Automatic SMS Alerts (Core Functionality)

- Location sent when user clicks Start Journey
- Automatic SMS sent every 2 minutes
- Safe Arrival message with location
- Stop Journey stops automatic alerts
- SMS directly delivered to recipient inbox

### 🚨 4. SOS Emergency Alert
- Sends HIGH ALERT message instantly
- Includes current location

### 🎤 5. Simple NLP Voice-Based Alert

Detects keywords like:

- “Help”
- “Danger”
- “Emergency”
- Automatically triggers emergency SMS
- Uses Android Speech Recognizer (free implementation)

### 👥 6. Emergency Contact Management

- Contact Picker integration
- Multiple contact selection
- Displays number of selected contacts
- Used for alert notifications

## 🏗️ Tech Stack

- Language: Java
- IDE: Android Studio
- Database: Firebase Firestore
- Authentication: Firebase Auth
- Location Services: Fused Location Provider API
- SMS Service: Android SmsManager
- Speech Recognition: Android SpeechRecognizer API
- UI: XML Layouts

## 📱 Application Flow

- User installs app
- Registers account
- Logs in
- Dashboard opens
- Select emergency contacts
- Click “Start Journey”
- Location sent every 2 minutes via SMS

User can:

- Click Safe Arrival
- Click Stop Journey
- Trigger voice-based alert
- If user is already logged in, dashboard opens directly without showing login screen.

### 🔄 System Architecture Overview
- Authentication Layer (Firebase)
- Location Module
- SMS Alert Module
- Voice Detection Module
- Dashboard UI Controller
- Contact Management Module
- Each module works independently but integrates through the main dashboard activity.

### 📡 Offline Capability
Feature	Works Without Internet?
- GPS Location	✅ Yes
- SMS Alerts	  ✅ Yes

### 🎯 Core Innovation

The core innovation of SafetyTrack is:

Automatic real-time location sharing via SMS every 2 minutes during a journey, combined with SOS emergency alerts and voice-triggered high alert detection


### 🧠 Future Enhancements

- Live tracking dashboard for family members
- Background service optimization
- Advanced NLP threat detection
- AI-based abnormal route detection
- Panic gesture detection
- Cloud-based emergency monitoring system
