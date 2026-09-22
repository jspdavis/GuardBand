/**
 * ESP32 LED Control via Firebase Realtime Database
 * 
 * Features:
 * - Connects to WiFi
 * - Syncs time with NTP server
 * - Reads LED command from Firebase
 * - Writes LED status to Firebase
 * - Sends heartbeat (lastSeen) to Firebase
 * - Handles disconnection gracefully
 * 
 * Database: https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/
 * Device ID: guardband-001
 * LED Pin: 13
 */

#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>
#include <time.h>

// ═══════════════════════════════════════════════════════════════════════════
// CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════

// WiFi Credentials
#define WIFI_SSID "ZTE_2.4G_KRGHr4"
#define WIFI_PASSWORD "88888888"

// Firebase Configuration
#define DATABASE_URL "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/"
#define API_KEY "AIzaSyDFbk4ZmLmQPKF9v_gdfRj8wQC8AUaLOjo"

// Device Configuration
#define DEVICE_ID "guardband-001"
#define LED_PIN 13

// Firebase Paths
#define PATH_COMMAND_LED "devices/" DEVICE_ID "/command/led"
#define PATH_STATUS_LED "devices/" DEVICE_ID "/status/led"
#define PATH_STATUS_LASTSEEN "devices/" DEVICE_ID "/status/lastSeen"

// Timing Configuration
#define COMMAND_CHECK_INTERVAL 2000    // Check commands every 2 seconds
#define HEARTBEAT_INTERVAL 5000        // Send heartbeat every 5 seconds

// NTP Configuration
#define NTP_SERVER "pool.ntp.org"
#define GMT_OFFSET_SEC 0
#define DAYLIGHT_OFFSET_SEC 0

// ═══════════════════════════════════════════════════════════════════════════
// GLOBAL VARIABLES
// ═══════════════════════════════════════════════════════════════════════════

FirebaseData fbdoCommand;
FirebaseData fbdoStatus;
FirebaseData fbdoHeartbeat;
FirebaseAuth auth;
FirebaseConfig config;

bool ledState = false;
unsigned long lastCommandCheck = 0;
unsigned long lastHeartbeat = 0;
bool firebaseReady = false;

// ═══════════════════════════════════════════════════════════════════════════
// SETUP
// ═══════════════════════════════════════════════════════════════════════════

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println("═══════════════════════════════════════════");
    Serial.println("  ESP32 LED Control - GuardBand");
    Serial.println("═══════════════════════════════════════════");
    
    // Initialize LED pin
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);
    ledState = false;
    
    // Connect to WiFi
    connectWiFi();
    
    // Sync time with NTP
    syncTime();
    
    // Initialize Firebase
    initFirebase();
    
    // Send initial status
    writeInitialStatus();
    
    Serial.println("═══════════════════════════════════════════");
    Serial.println("  Setup Complete - Entering Main Loop");
    Serial.println("═══════════════════════════════════════════");
}

// ═══════════════════════════════════════════════════════════════════════════
// MAIN LOOP
// ═══════════════════════════════════════════════════════════════════════════

void loop() {
    unsigned long currentMillis = millis();
    
    // Check for WiFi reconnection
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("⚠️  WiFi disconnected! Attempting to reconnect...");
        connectWiFi();
        return;
    }
    
    // Check commands periodically
    if (currentMillis - lastCommandCheck >= COMMAND_CHECK_INTERVAL) {
        lastCommandCheck = currentMillis;
        checkAndExecuteCommand();
    }
    
    // Send heartbeat periodically
    if (currentMillis - lastHeartbeat >= HEARTBEAT_INTERVAL) {
        lastHeartbeat = currentMillis;
        sendHeartbeat();
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// WIFI FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

void connectWiFi() {
    Serial.print("Connecting to WiFi: ");
    Serial.println(WIFI_SSID);
    
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        attempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
        Serial.println();
        Serial.println("✓ WiFi Connected!");
        Serial.print("IP Address: ");
        Serial.println(WiFi.localIP());
    } else {
        Serial.println();
        Serial.println("✗ WiFi Connection Failed!");
        Serial.println("Restarting in 5 seconds...");
        delay(5000);
        ESP.restart();
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// NTP TIME SYNC
// ═══════════════════════════════════════════════════════════════════════════

void syncTime() {
    Serial.println("Syncing time with NTP server...");
    configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);
    
    int attempts = 0;
    time_t now = time(nullptr);
    while (now < 1000000000 && attempts < 20) {
        delay(500);
        Serial.print(".");
        now = time(nullptr);
        attempts++;
    }
    
    Serial.println();
    if (now >= 1000000000) {
        Serial.print("✓ Time synced: ");
        Serial.println(now);
    } else {
        Serial.println("⚠️  Time sync incomplete, but continuing...");
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FIREBASE INITIALIZATION
// ═══════════════════════════════════════════════════════════════════════════

void initFirebase() {
    Serial.println("Initializing Firebase...");
    
    config.api_key = API_KEY;
    config.database_url = DATABASE_URL;
    
    // Anonymous authentication
    if (Firebase.signUp(&config, &auth, "", "")) {
        Serial.println("✓ Firebase authentication successful");
        firebaseReady = true;
    } else {
        Serial.print("✗ Firebase authentication failed: ");
        Serial.println(config.signer.signupError.message.c_str());
        firebaseReady = false;
    }
    
    config.token_status_callback = tokenStatusCallback;
    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);
    
    Serial.println("✓ Firebase initialized");
}

// Token status callback
void tokenStatusCallback(TokenInfo info) {
    if (info.status == token_status_ready) {
        firebaseReady = true;
        Serial.println("✓ Firebase token ready");
    } else {
        firebaseReady = false;
        Serial.println("⚠️  Firebase token not ready");
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMMAND HANDLING
// ═══════════════════════════════════════════════════════════════════════════

void checkAndExecuteCommand() {
    if (!firebaseReady) {
        return;
    }
    
    if (Firebase.RTDB.getBool(&fbdoCommand, PATH_COMMAND_LED)) {
        bool commandedState = fbdoCommand.boolData();
        
        if (commandedState != ledState) {
            Serial.print("📥 New command received: LED ");
            Serial.println(commandedState ? "ON" : "OFF");
            
            // Execute command
            ledState = commandedState;
            digitalWrite(LED_PIN, ledState ? HIGH : LOW);
            
            // Report new status to Firebase
            reportStatus();
        }
    } else {
        // Command path doesn't exist or error - not critical
        if (fbdoCommand.errorReason() != "path not exist") {
            Serial.print("⚠️  Command check error: ");
            Serial.println(fbdoCommand.errorReason());
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// STATUS REPORTING
// ═══════════════════════════════════════════════════════════════════════════

void reportStatus() {
    if (!firebaseReady) {
        return;
    }
    
    if (Firebase.RTDB.setBool(&fbdoStatus, PATH_STATUS_LED, ledState)) {
        Serial.print("📤 Status reported: LED ");
        Serial.println(ledState ? "ON" : "OFF");
    } else {
        Serial.print("✗ Status report failed: ");
        Serial.println(fbdoStatus.errorReason());
    }
}

void writeInitialStatus() {
    Serial.println("Writing initial status...");
    reportStatus();
    sendHeartbeat();
}

// ═══════════════════════════════════════════════════════════════════════════
// HEARTBEAT
// ═══════════════════════════════════════════════════════════════════════════

void sendHeartbeat() {
    if (!firebaseReady) {
        return;
    }
    
    time_t now = time(nullptr);
    
    if (now < 1000000000) {
        Serial.println("⚠️  Cannot send heartbeat: Time not synced");
        return;
    }
    
    if (Firebase.RTDB.setInt(&fbdoHeartbeat, PATH_STATUS_LASTSEEN, (int)now)) {
        Serial.print("💓 Heartbeat sent: ");
        Serial.println(now);
    } else {
        Serial.print("✗ Heartbeat failed: ");
        Serial.println(fbdoHeartbeat.errorReason());
    }
}
