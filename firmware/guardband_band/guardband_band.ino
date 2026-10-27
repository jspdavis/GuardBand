// GuardBand band firmware: the prototype on an ESP32 with three LEDs and one
// button, writing alerts straight to Firebase Realtime Database over Wi-Fi.
//
// It behaves like the app's debug Mock Sender (BandButtonDecoder.kt and
// AlertSender.kt), which is the reference for every timing and rule here:
//   - hold 1.5 s                -> PANIC, fired while still held; then tracking
//   - two presses within 2 s    -> CHECKIN
//   - during a panic, hold 5 s  -> cancel: tracking stops and a CHECKIN is sent
//   - one press alone           -> nothing
//   - once the panic has landed, a TRACKING_UPDATE every 5 s for 30 min
//   - battery at or below 20%   -> LOW_BATTERY, once, until it goes back above 20%
// A check-in does not cancel a panic, because a double-press can be forced.
//
// Payloads follow SCHEMA.md v1.0. Each alert is one PUT to
// /devices/{id}/history/{sequenceId}, then one to /devices/{id}/latest.
//
// Wiring: red LED GPIO25, yellow GPIO26, green GPIO27, each through a
// resistor to GND; the button from GPIO33 to GND (internal pull-up).
//
// LEDs:
//   red    steady 3 s   = panic pressed     red fast blink    = a send failed
//   yellow steady       = button held       yellow slow blink = tracking
//   green  steady 1.5 s = check-in pressed  green short flash = Wi-Fi OK at boot
//
// The laptop stands in for the GPS and the battery, over Serial (115200 baud,
// one command per line):
//   GPS,10.3157,123.8854[,8.5]   a fix (accuracy in metres, default 10)
//   NOFIX                        forget the fix
//   BATT,85                      battery percent
//   STATUS                       what the band has right now
//
// See firmware/README.md for setup and how to check an alert end to end.

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <atomic>
#include <sys/time.h>
#include <time.h>

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Copy secrets.example.h to secrets.h and put your Wi-Fi name and password in it."
#endif

// ---------- Firebase ----------
// The database the app reads (firebase_url in app/google-services.json).
const char* DB_URL = "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app";
// The one band every install of the app watches, until pairing exists
// (DeviceConstants.DEFAULT_DEVICE_ID). Any other id and nothing shows up.
const char* DEVICE_ID = "guardband-001";

// ---------- Pins ----------
const int PIN_RED = 25, PIN_YELLOW = 26, PIN_GREEN = 27, PIN_BUTTON = 33;

// ---------- Button: BandButtonDecoder's timings ----------
const uint32_t PANIC_HOLD_MS = 1500;
const uint32_t CANCEL_HOLD_MS = 5000;
const uint32_t DOUBLE_PRESS_MS = 2000;
const uint32_t DEBOUNCE_MS = 30;

// ---------- Tracking: the Mock Sender's ----------
// Every 5 s rather than FR-09's 60 s, so a demo shows a path within a minute.
const uint32_t TRACKING_INTERVAL_MS = 5000;
const uint32_t TRACKING_DURATION_MS = 30UL * 60UL * 1000UL;  // FR-09

// ---------- Battery ----------
const int LOW_BATTERY_PERCENT = 20;  // FR-11

// ---------- Position without a fix ----------
// true: like the Mock Sender, an alert with no fix gets a random point near
// Cebu City and tracking walks a ~165 m loop, so a desk demo still shows
// something on the map. false: the location is left out of the payload.
// Turn it off once the NEO-6M supplies real fixes.
const bool SIMULATE_WITHOUT_FIX = true;
const double CEBU_LAT = 10.3157, CEBU_LNG = 123.8854;
const int LOOP_STEPS = 24;
const double LOOP_RADIUS_DEG = 0.0015;
const float LOOP_ACCURACY_M = 10.0;
const float DEFAULT_ACCURACY_M = 10.0;

// ---------- LEDs ----------
const uint32_t PANIC_LED_MS = 3000, CHECKIN_LED_MS = 1500;
const uint32_t TRACKING_BLINK_MS = 500;  // yellow, slow
const uint32_t FAILED_BLINK_MS = 150;    // red, fast, so it never reads as a panic

// ---------- Sending ----------
const uint32_t RETRY_MS = 5000;
const int MAX_ATTEMPTS = 5;  // then the alert is dropped and the red LED keeps blinking
const uint32_t WIFI_WAIT_MS = 5000;
const uint16_t HTTP_TIMEOUT_MS = 10000;
const int QUEUE_LENGTH = 16;

// ---------- Serial ----------
const uint32_t SERIAL_IDLE_MS = 200;

// Every type sits above the first function: the Arduino IDE inserts its own
// function prototypes there, and a prototype naming a type declared later
// does not compile.
enum class AlertType : uint8_t { Panic, CheckIn, LowBattery, TrackingUpdate };

enum class SendResult { Sent, HistoryOnly, NotSent };

// One alert, captured the moment it happens. Its position and battery are the
// ones at that moment, and atMillis becomes its timestamp when it is sent, so
// a retried panic keeps the time it was pressed.
struct Alert {
  AlertType type;
  uint32_t atMillis;
  bool hasLocation;
  bool simulated;  // no GPS fix: the point was made up (SIMULATE_WITHOUT_FIX)
  double lat, lng;
  float accuracyMeters;
  int batteryPercent;
};

// ---------- Shared between loop() and the sender task ----------
// The HTTPS calls take a second or more each, so they run in their own task;
// loop() keeps reading the button and blinking the LEDs meanwhile.
QueueHandle_t alertQueue;
std::atomic<bool> panicLanded{false};   // set by the sender when a PANIC's history row lands
std::atomic<bool> panicGaveUp{false};   // set when a PANIC failed MAX_ATTEMPTS times
std::atomic<bool> sendFailing{false};   // set on a failed send, cleared on the next good one
std::atomic<int> trackingQueued{0};     // TRACKING_UPDATEs waiting, so they never pile up

// ---------- loop() only ----------
bool hasFix = false;
double fixLat = 0, fixLng = 0;
float fixAccuracy = DEFAULT_ACCURACY_M;
int batteryPercent = 100;
bool lowBatteryArmed = true;

bool panicPending = false;  // a panic was pressed and has not landed yet
bool tracking = false;
uint32_t trackingStartedAt = 0, nextTrackingAt = 0;
int trackingStep = 0;

bool lastReading = false, pressed = false;
uint32_t lastChangeAt = 0;
uint32_t downAt = 0;
bool panicAtDown = false;  // whether this press could be a cancel
bool holdFired = false;    // this hold already fired, so its release does nothing
bool waitingSecond = false;
uint32_t firstPressAt = 0;

bool redOn = false, greenOn = false;
uint32_t redSince = 0, greenSince = 0;

char lineBuf[64];
int lineLen = 0;

// ---------- Sender task only ----------
unsigned long nextSequenceId = 0;  // 0 until seeded from the server

// =====================================================================
// Capturing alerts (loop)
// =====================================================================

const char* typeName(AlertType type) {
  switch (type) {
    case AlertType::Panic: return "PANIC";
    case AlertType::CheckIn: return "CHECKIN";
    case AlertType::LowBattery: return "LOW_BATTERY";
    default: return "TRACKING_UPDATE";
  }
}

double randomBetween(double lo, double hi) {
  return lo + (hi - lo) * (random(0, 1000001) / 1000000.0);
}

Alert makeAlert(AlertType type) {
  Alert alert{};
  alert.type = type;
  alert.atMillis = millis();
  alert.batteryPercent = batteryPercent;
  if (hasFix) {
    alert.hasLocation = true;
    alert.lat = fixLat;
    alert.lng = fixLng;
    alert.accuracyMeters = fixAccuracy;
  } else if (SIMULATE_WITHOUT_FIX) {
    alert.hasLocation = true;
    alert.simulated = true;
    if (type == AlertType::TrackingUpdate) {
      double angle = 2 * PI * (trackingStep % LOOP_STEPS) / LOOP_STEPS;
      alert.lat = CEBU_LAT + LOOP_RADIUS_DEG * sin(angle);
      // Longitude degrees shrink with latitude; dividing by cos(lat) keeps the loop round.
      alert.lng = CEBU_LNG + LOOP_RADIUS_DEG * cos(angle) / cos(radians(CEBU_LAT));
      alert.accuracyMeters = LOOP_ACCURACY_M;
    } else {
      alert.lat = CEBU_LAT + randomBetween(-0.01, 0.01);
      alert.lng = CEBU_LNG + randomBetween(-0.01, 0.01);
      alert.accuracyMeters = randomBetween(5.0, 15.0);
    }
  }
  return alert;
}

bool enqueue(const Alert& alert) {
  if (xQueueSend(alertQueue, &alert, 0) != pdTRUE) {
    Serial.printf("Queue full: %s dropped\n", typeName(alert.type));
    return false;
  }
  return true;
}

// =====================================================================
// Gestures (loop)
// =====================================================================

bool panicActive() { return panicPending || tracking; }

void onPanic(uint32_t now) {
  Serial.println("PANIC");
  redOn = true;
  redSince = now;
  panicPending = true;
  enqueue(makeAlert(AlertType::Panic));
}

void onCheckIn(uint32_t now) {
  Serial.println("CHECKIN");
  greenOn = true;
  greenSince = now;
  enqueue(makeAlert(AlertType::CheckIn));
}

void onCancelPanic(uint32_t now) {
  Serial.println("Panic cancelled: tracking stopped, sending a check-in");
  panicPending = false;
  tracking = false;
  onCheckIn(now);
}

// BandButtonDecoder, on a polled, debounced pin instead of touch events.
void readButton(uint32_t now) {
  bool reading = digitalRead(PIN_BUTTON) == LOW;
  if (reading != lastReading) {
    lastReading = reading;
    lastChangeAt = now;
  }

  if (now - lastChangeAt > DEBOUNCE_MS && reading != pressed) {
    pressed = reading;
    if (pressed) {
      downAt = now;
      panicAtDown = panicActive();
      holdFired = false;
    } else if (!holdFired) {
      if (now - downAt >= PANIC_HOLD_MS) {
        // Only reachable during a panic: otherwise the hold would have fired.
        waitingSecond = false;
        Serial.println("Cancel needs a 5 s hold");
      } else if (waitingSecond && now - firstPressAt <= DOUBLE_PRESS_MS) {
        waitingSecond = false;
        onCheckIn(now);
      } else {
        waitingSecond = true;
        firstPressAt = now;
      }
    }
  }

  uint32_t holdNeeded = panicAtDown ? CANCEL_HOLD_MS : PANIC_HOLD_MS;
  if (pressed && !holdFired && now - downAt >= holdNeeded) {
    holdFired = true;
    waitingSecond = false;
    if (panicAtDown) onCancelPanic(now);
    else onPanic(now);
  }

  if (waitingSecond && !pressed && now - firstPressAt > DOUBLE_PRESS_MS) {
    waitingSecond = false;
    Serial.println("Single press: nothing sent");
  }
}

// =====================================================================
// Tracking and battery (loop)
// =====================================================================

void startTracking(uint32_t now) {
  panicPending = false;
  tracking = true;
  trackingStartedAt = now;
  nextTrackingAt = now;  // the first position goes straight after the panic
  trackingStep = 0;
  Serial.println("Tracking: every 5 s for 30 min");
}

void updateTracking(uint32_t now) {
  if (!tracking) return;
  if (now - trackingStartedAt >= TRACKING_DURATION_MS) {
    tracking = false;
    Serial.println("Tracking finished (30 min)");
    return;
  }
  if ((int32_t)(now - nextTrackingAt) < 0) return;
  nextTrackingAt = now + TRACKING_INTERVAL_MS;

  if (trackingQueued > 0) {
    Serial.println("Tracking update skipped: the last one has not gone yet");
    return;
  }
  Alert alert = makeAlert(AlertType::TrackingUpdate);
  trackingStep++;
  trackingQueued++;
  if (!enqueue(alert)) trackingQueued--;
}

void setBattery(int percent) {
  batteryPercent = constrain(percent, 0, 100);
  Serial.printf("Battery %d%%\n", batteryPercent);
  if (batteryPercent > LOW_BATTERY_PERCENT) {
    lowBatteryArmed = true;
  } else if (lowBatteryArmed) {
    lowBatteryArmed = false;
    Serial.println("LOW_BATTERY");
    enqueue(makeAlert(AlertType::LowBattery));
  }
}

// =====================================================================
// Serial commands from the laptop (loop)
// =====================================================================

void printStatus() {
  Serial.printf("Wi-Fi %s, clock %s, %s, battery %d%%, %s%s\n",
    wifiStatusName(WiFi.status()),
    time(nullptr) > 1700000000 ? "synced" : "not synced",
    hasFix ? "GPS fix" : "no GPS fix",
    batteryPercent,
    tracking ? "tracking" : (panicPending ? "panic waiting to send" : "idle"),
    sendFailing ? ", last send FAILED" : "");
}

void handleLine(char* line) {
  if (strncasecmp(line, "GPS,", 4) == 0) {
    double lat, lng;
    float accuracy = DEFAULT_ACCURACY_M;
    int read = sscanf(line + 4, "%lf,%lf,%f", &lat, &lng, &accuracy);
    if (read < 2 || lat < -90 || lat > 90 || lng < -180 || lng > 180 || accuracy <= 0) {
      Serial.println("Usage: GPS,lat,lng[,accuracyMeters]");
      return;
    }
    fixLat = lat;
    fixLng = lng;
    fixAccuracy = accuracy;
    hasFix = true;
    // The coordinates are not echoed back: STATUS says whether there is a fix.
    Serial.printf("GPS fix set (%.1f m)\n", fixAccuracy);
  } else if (strcasecmp(line, "NOFIX") == 0) {
    hasFix = false;
    Serial.println(SIMULATE_WITHOUT_FIX ? "No GPS fix: positions are simulated" : "No GPS fix: location left out");
  } else if (strncasecmp(line, "BATT,", 5) == 0) {
    setBattery(atoi(line + 5));
  } else if (strcasecmp(line, "STATUS") == 0) {
    printStatus();
  } else {
    Serial.println("Commands: GPS,lat,lng[,acc]  NOFIX  BATT,n  STATUS");
  }
}

void runLine() {
  lineBuf[lineLen] = 0;
  handleLine(lineBuf);
  lineLen = 0;
}

// A command ends at a newline, or after SERIAL_IDLE_MS of silence, so it also
// works with the Serial Monitor set to "No Line Ending" (which otherwise
// leaves the command waiting forever).
void readSerial(uint32_t now) {
  static uint32_t lastCharAt = 0;
  while (Serial.available()) {
    char c = Serial.read();
    lastCharAt = now;
    if (c == '\n' || c == '\r') {
      if (lineLen > 0) runLine();
    } else if (lineLen < (int)sizeof(lineBuf) - 1) {
      lineBuf[lineLen++] = c;
    }
  }
  if (lineLen > 0 && now - lastCharAt > SERIAL_IDLE_MS) runLine();
}

// =====================================================================
// LEDs (loop)
// =====================================================================

void renderLeds(uint32_t now) {
  if (redOn && now - redSince >= PANIC_LED_MS) redOn = false;
  if (greenOn && now - greenSince >= CHECKIN_LED_MS) greenOn = false;

  bool failedBlink = sendFailing && (now / FAILED_BLINK_MS) % 2 == 0;
  bool trackingBlink = tracking && (now / TRACKING_BLINK_MS) % 2 == 0;

  digitalWrite(PIN_RED, (redOn || failedBlink) ? HIGH : LOW);
  digitalWrite(PIN_YELLOW, (pressed || trackingBlink) ? HIGH : LOW);
  digitalWrite(PIN_GREEN, greenOn ? HIGH : LOW);
}

// =====================================================================
// Wi-Fi diagnostics
// =====================================================================

const char* wifiStatusName(wl_status_t status) {
  switch (status) {
    case WL_CONNECTED: return "connected";
    case WL_NO_SSID_AVAIL: return "network not found";
    case WL_CONNECT_FAILED: return "connection failed";
    case WL_CONNECTION_LOST: return "connection lost";
    case WL_DISCONNECTED: return "disconnected";
    case WL_IDLE_STATUS: return "idle";
    default: return "unknown";
  }
}

// The reasons the ESP32 gives most often for refusing or dropping a
// connection, with what usually causes each.
const char* disconnectHint(uint8_t reason) {
  switch (reason) {
    case 2: case 15: case 202: case 204: return "usually a wrong password";
    case 201: return "network not found: wrong name, out of range, or 5 GHz only";
    case 200: return "router stopped answering: weak signal?";
    case 203: case 205: return "router refused the board: MAC filter or too many devices?";
    case 210: case 211: return "router security not supported (WPA3-only?): try WPA2";
    case 8: return "the board disconnected itself";
    default: return "see esp_wifi_types.h for the code";
  }
}

void onWifiEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  switch (event) {
    case ARDUINO_EVENT_WIFI_STA_GOT_IP:
      Serial.printf("Wi-Fi: connected to \"%s\", IP %s, signal %d dBm\n",
        WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(), (int)WiFi.RSSI());
      break;
    case ARDUINO_EVENT_WIFI_STA_DISCONNECTED: {
      // The ESP32 retries every second or so with the same reason: say it
      // once, then again only when it changes or 30 s have passed.
      static uint8_t lastReason = 0;
      static uint32_t lastAt = 0;
      uint8_t reason = info.wifi_sta_disconnected.reason;
      if (reason != lastReason || millis() - lastAt > 30000) {
        Serial.printf("Wi-Fi: not connected, reason %u (%s)\n", reason, disconnectHint(reason));
        lastReason = reason;
        lastAt = millis();
      }
      break;
    }
    default:
      break;
  }
}

// Run when the first connection fails: is the network visible at all?
void reportWifiScan() {
  Serial.println("Wi-Fi: scanning for networks the board can see...");
  WiFi.disconnect();  // a scan cannot run while a connection attempt is in progress
  int found = WiFi.scanNetworks();
  if (found <= 0) {
    Serial.println("Wi-Fi: no networks found at all (antenna, or the board is too far away?)");
  } else {
    bool seen = false;
    for (int i = 0; i < found; i++) {
      if (WiFi.SSID(i) == WIFI_SSID) {
        seen = true;
        Serial.printf("Wi-Fi: \"%s\" is visible, channel %d, signal %d dBm",
          WIFI_SSID, (int)WiFi.channel(i), (int)WiFi.RSSI(i));
        Serial.println(WiFi.RSSI(i) < -80 ? " (weak: move closer)" : "");
      }
    }
    if (!seen) {
      Serial.printf("Wi-Fi: \"%s\" is NOT among the %d networks visible. It may be 5 GHz only, "
        "or the name in secrets.h differs (capitals and spaces count). Visible:\n", WIFI_SSID, found);
      for (int i = 0; i < found && i < 10; i++) {
        Serial.printf("  \"%s\" (%d dBm)\n", WiFi.SSID(i).c_str(), (int)WiFi.RSSI(i));
      }
    }
  }
  WiFi.scanDelete();
  WiFi.begin(WIFI_SSID, WIFI_PASS);  // keep trying in the background
}

// =====================================================================
// Sending (sender task)
// =====================================================================

bool ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) return true;
  Serial.printf("Wi-Fi: down before sending (%s), reconnecting...\n", wifiStatusName(WiFi.status()));
  WiFi.reconnect();
  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < WIFI_WAIT_MS) {
    vTaskDelay(pdMS_TO_TICKS(100));
  }
  return WiFi.status() == WL_CONNECTED;
}

// Without NTP the clock reads 1970, and the app would show the alert as
// decades old, so nothing is sent until it has synced.
bool clockSynced() { return time(nullptr) > 1700000000; }

// ISO 8601 UTC with milliseconds, like the Mock Sender: 2026-10-28T08:15:30.123Z
void formatTimestamp(uint32_t atMillis, char* out, size_t size) {
  struct timeval tv;
  gettimeofday(&tv, nullptr);
  int64_t nowMs = (int64_t)tv.tv_sec * 1000 + tv.tv_usec / 1000;
  int64_t atMs = nowMs - (int64_t)(uint32_t)(millis() - atMillis);
  time_t seconds = atMs / 1000;
  struct tm t;
  gmtime_r(&seconds, &t);
  char base[24];
  strftime(base, sizeof(base), "%Y-%m-%dT%H:%M:%S", &t);
  snprintf(out, size, "%s.%03dZ", base, (int)(atMs % 1000));
}

String buildPayload(const Alert& alert, unsigned long sequenceId) {
  char timestamp[32];
  formatTimestamp(alert.atMillis, timestamp, sizeof(timestamp));

  char location[100] = "";
  if (alert.hasLocation) {
    snprintf(location, sizeof(location),
      "\"location\":{\"lat\":%.6f,\"lng\":%.6f,\"accuracyMeters\":%.1f},",
      alert.lat, alert.lng, alert.accuracyMeters);
  }

  char buf[400];
  snprintf(buf, sizeof(buf),
    "{\"schemaVersion\":\"1.0\",\"deviceId\":\"%s\",\"type\":\"%s\","
    "\"timestamp\":\"%s\",%s"
    "\"battery\":{\"percent\":%d,\"isCharging\":false},\"sequenceId\":%lu}",
    DEVICE_ID, typeName(alert.type), timestamp, location, alert.batteryPercent, sequenceId);
  return String(buf);
}

// Prototype only: setInsecure() skips the certificate check.
void beginHttp(HTTPClient& http, WiFiClientSecure& client, const String& url) {
  client.setInsecure();
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.begin(client, url);
}

bool putJson(const String& path, const String& body) {
  WiFiClientSecure client;
  HTTPClient http;
  beginHttp(http, client, String(DB_URL) + path + ".json");
  http.addHeader("Content-Type", "application/json");
  int code = http.PUT(body);
  bool ok = code >= 200 && code < 300;
  if (ok) {
    Serial.printf("PUT %s -> %d\n", path.c_str(), code);
  } else if (code > 0) {
    Serial.printf("PUT %s -> %d %s\n", path.c_str(), code, http.getString().c_str());
  } else {
    Serial.printf("PUT %s failed: %s\n", path.c_str(), http.errorToString(code).c_str());
  }
  http.end();
  return ok;
}

// The highest integer key under history, 0 when there is none, -1 when the
// read failed. Seeding from the server rather than from 1 is what stops the
// band from overwriting rows the Mock Sender (or an earlier run) wrote.
// shallow=true returns the keys alone, as {"1":true,"2":true}, so every quoted
// string in the body is a key; a key that is not a plain number is skipped,
// as the Mock Sender skips it.
long readHighestHistoryKey() {
  WiFiClientSecure client;
  HTTPClient http;
  beginHttp(http, client, String(DB_URL) + "/devices/" + DEVICE_ID + "/history.json?shallow=true");
  int code = http.GET();
  if (code != 200) {
    Serial.printf("Reading history keys failed: %d\n", code);
    http.end();
    return -1;
  }
  String body = http.getString();
  http.end();

  long highest = 0;
  int from = 0;
  for (;;) {
    int open = body.indexOf('"', from);
    if (open < 0) break;
    int close = body.indexOf('"', open + 1);
    if (close < 0) break;
    int length = close - open - 1;
    bool numeric = length > 0 && length <= 9;
    for (int i = open + 1; numeric && i < close; i++) numeric = isDigit(body[i]);
    if (numeric) {
      long key = body.substring(open + 1, close).toInt();
      if (key > highest) highest = key;
    }
    from = close + 1;
  }
  return highest;
}

// History first, then latest, as the Mock Sender does: the app's alert
// screens read history, so an alert that got that far is still seen, and
// only latest (the map) is left stale. The id is used up the moment its
// history row lands, so a retry can never overwrite a good row.
SendResult sendAlert(const Alert& alert) {
  const char* type = typeName(alert.type);
  if (!ensureWifi()) {
    Serial.printf("%s: no Wi-Fi\n", type);
    return SendResult::NotSent;
  }
  if (!clockSynced()) {
    Serial.printf("%s: clock not synced yet\n", type);
    return SendResult::NotSent;
  }
  if (nextSequenceId == 0) {
    long highest = readHighestHistoryKey();
    if (highest < 0) return SendResult::NotSent;
    nextSequenceId = highest + 1;
  }

  unsigned long sequenceId = nextSequenceId;
  String payload = buildPayload(alert, sequenceId);
  String base = String("/devices/") + DEVICE_ID;

  if (!putJson(base + "/history/" + sequenceId, payload)) {
    nextSequenceId = 0;  // read the keys again: someone else may have taken this id
    return SendResult::NotSent;
  }
  nextSequenceId = sequenceId + 1;

  bool latestOk = putJson(base + "/latest", payload);
  const char* position = !alert.hasLocation ? "no location"
    : alert.simulated ? "SIMULATED position: no GPS fix, send GPS,lat,lng" : "GPS fix";
  Serial.printf("%s sent as #%lu (%s)%s\n", type, sequenceId, position,
    latestOk ? "" : ", latest not updated");
  return latestOk ? SendResult::Sent : SendResult::HistoryOnly;
}

// One alert at a time, in order. A panic, check-in or low-battery alert is
// tried up to MAX_ATTEMPTS times, 5 s apart, then dropped; a tracking update
// is tried once, because the next one, 5 s later, replaces it.
void senderTask(void*) {
  Alert alert;
  for (;;) {
    if (xQueueReceive(alertQueue, &alert, portMAX_DELAY) != pdTRUE) continue;
    if (alert.type == AlertType::TrackingUpdate) trackingQueued--;

    for (int attempt = 1;; attempt++) {
      SendResult result = sendAlert(alert);
      if (result != SendResult::NotSent && alert.type == AlertType::Panic) panicLanded = true;
      if (result == SendResult::Sent) {
        sendFailing = false;
        break;
      }
      sendFailing = true;
      if (result == SendResult::HistoryOnly) break;
      if (alert.type == AlertType::TrackingUpdate) {
        Serial.println("TRACKING_UPDATE not sent; the next one replaces it");
        break;
      }
      if (attempt >= MAX_ATTEMPTS) {
        Serial.printf("%s dropped after %d failed attempts\n", typeName(alert.type), attempt);
        if (alert.type == AlertType::Panic) panicGaveUp = true;
        break;
      }
      Serial.printf("%s not sent (attempt %d of %d); retrying in %lu s\n", typeName(alert.type),
        attempt, MAX_ATTEMPTS, (unsigned long)(RETRY_MS / 1000));
      vTaskDelay(pdMS_TO_TICKS(RETRY_MS));
    }
  }
}

// =====================================================================

void setup() {
  Serial.begin(115200);
  delay(1500);

  pinMode(PIN_RED, OUTPUT);
  pinMode(PIN_YELLOW, OUTPUT);
  pinMode(PIN_GREEN, OUTPUT);
  pinMode(PIN_BUTTON, INPUT_PULLUP);
  renderLeds(millis());

  if (strcmp(WIFI_SSID, "YOUR_WIFI_NAME") == 0) {
    Serial.println("Wi-Fi: secrets.h still has the placeholder name. Put your network in secrets.h, "
      "not secrets.example.h.");
  }
  Serial.printf("Wi-Fi: connecting to \"%s\"...\n", WIFI_SSID);
  WiFi.onEvent(onWifiEvent);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
    delay(500);
  }
  if (WiFi.status() == WL_CONNECTED) {
    digitalWrite(PIN_GREEN, HIGH);
    delay(300);
    digitalWrite(PIN_GREEN, LOW);
  } else {
    Serial.printf("Wi-Fi: NOT connected after 15 s (status: %s). Retried before each send.\n",
      wifiStatusName(WiFi.status()));
    reportWifiScan();
  }

  configTime(0, 0, "pool.ntp.org", "time.google.com");  // UTC

  alertQueue = xQueueCreate(QUEUE_LENGTH, sizeof(Alert));
  // Core 0, beside the Wi-Fi stack; loop() runs on core 1. TLS wants a big stack.
  xTaskCreatePinnedToCore(senderTask, "sender", 16384, nullptr, 1, nullptr, 0);

  Serial.printf("GuardBand ready as %s. Commands: GPS,lat,lng[,acc]  NOFIX  BATT,n  STATUS\n", DEVICE_ID);
  Serial.println(SIMULATE_WITHOUT_FIX
    ? "No GPS fix yet: positions are simulated near Cebu City until you send GPS,lat,lng"
    : "No GPS fix yet: alerts carry no location until you send GPS,lat,lng");
}

void loop() {
  uint32_t now = millis();
  readSerial(now);
  readButton(now);
  if (panicLanded.exchange(false) && panicPending) startTracking(now);
  // A dropped panic must not leave the button in cancel mode: the next
  // 1.5 s hold sends a new panic.
  if (panicGaveUp.exchange(false)) panicPending = false;
  updateTracking(now);
  renderLeds(now);
  delay(5);
}
