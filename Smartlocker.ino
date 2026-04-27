#include <WiFi.h>
#include <HTTPClient.h>
#include "time.h"

const char* ssid = "";
const char* pass = "";
String FIREBASE_URL = "https://project-default-rtdb.asia-southeast1.firebasedatabase.app/";

#define LDR_PIN 34
#define TRIG_PIN 5
#define ECHO_PIN 18
#define RELAY_PIN 21
#define GLED_PIN 26
#define RLED_PIN 27

bool hasPackage = false;
bool isDoorOpen = false;

// ตัวแปรจับเวลา False Alarm
unsigned long ultrasonicTriggerTime = 0;
bool isUltrasonicTriggered = false;
const unsigned long PACKAGE_THRESHOLD = 3000;  // ต้องมีของวางแช่เกิน 3 วินาที

unsigned long ldrTriggerTime = 0;
bool isLdrTriggered = false;
const unsigned long LDR_THRESHOLD = 2000;  // ประตูต้องเปิดค้างเกิน 2 วินาที

// ตัวแปรเช็กเปิดประตูถูกต้อง(กันการแจ้งเตือนงัด)
unsigned long lastUnlockTime = 0;
const unsigned long LEGIT_OPEN_DURATION = 15000;  // ให้เวลาเปิดประตู 15 วินาทีหลังกดปลดล็อก

void updateStatus(int status) {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(FIREBASE_URL + "locker/status.json");
    http.PUT(String(status));
    http.end();
  }
}

void pushLog(String eventMsg) {
  if (WiFi.status() == WL_CONNECTED) {
    struct tm timeinfo;
    if (!getLocalTime(&timeinfo)) { return; }
    char timeString[10];
    strftime(timeString, sizeof(timeString), "%H:%M:%S", &timeinfo);

    // สร้าง JSON Payload
    String jsonPayload = "{\"time\":\"" + String(timeString) + "\", \"event\":\"" + eventMsg + "\"}";

    HTTPClient http;
    http.begin(FIREBASE_URL + "locker/logs.json");
    http.addHeader("Content-Type", "application/json");
    http.POST(jsonPayload);
    http.end();
  }
}

void checkUnlockCommand() {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(FIREBASE_URL + "locker/unlock.json");
    int httpCode = http.GET();

    if (httpCode == 200) {
      String payload = http.getString();
      if (payload == "1") {  // ถ้าแอปส่งเลข 1 มา
        Serial.println("สั่งปลดล็อกตู้!");

        digitalWrite(RELAY_PIN, LOW);
        delay(3000);  // ปลดล็อก 3 วินาที (ตามโค้ดเดิม)
        digitalWrite(RELAY_PIN, HIGH);

        // เซ็ตค่ากลับเป็น 0 เพื่อรอคำสั่งครั้งต่อไป
        http.begin(FIREBASE_URL + "locker/unlock.json");
        http.PUT("0");

        lastUnlockTime = millis();  // จดจำเวลาที่ปลดล็อกถูกต้อง
        pushLog("ปลดล็อกตู้");
      }
    }
    http.end();
  }
}

void checkSensors() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  long duration = pulseIn(ECHO_PIN, HIGH);
  float distance = duration * 0.034 / 2;

  if (distance > 1 && distance < 10) {
    if (!isUltrasonicTriggered) {
      isUltrasonicTriggered = true;
      ultrasonicTriggerTime = millis();  // เริ่มจับเวลา
    }
    // กรอง False Alarm: ถ้าวางเกิน 3 วินาที ถึงจะถือว่าเป็นของจริง
    else if (millis() - ultrasonicTriggerTime > PACKAGE_THRESHOLD && !hasPackage) {
      hasPackage = true;
      updateStatus(1);
      pushLog("พัสดุมาส่ง");
      digitalWrite(RLED_PIN, HIGH);
      digitalWrite(GLED_PIN, LOW);
    }
  } else if (distance >= 10 || distance <= 0) {
    isUltrasonicTriggered = false;  // รีเซ็ตตัวจับเวลาถ้าเป็นแค่แมลงบินผ่าน
    if (hasPackage) {
      hasPackage = false;
      updateStatus(0);
      pushLog("พัสดุถูกนำออกจากตู้");
      digitalWrite(RLED_PIN, LOW);
      digitalWrite(GLED_PIN, HIGH);
    }
  }

  // --- 2. วิเคราะห์แสง (เปิด/งัดแงะตู้) ---
  int lightValue = analogRead(LDR_PIN);

  if (lightValue < 3000) {  // มีแสงเข้า = ประตูเปิด
    if (!isLdrTriggered) {
      isLdrTriggered = true;
      ldrTriggerTime = millis();
    } else if (millis() - ldrTriggerTime > LDR_THRESHOLD && !isDoorOpen) {
      isDoorOpen = true;

      // AI กรองพฤติกรรม: เช็กว่าเพิ่งกดปุ่มปลดล็อกตู้ใน 15 วินาทีที่ผ่านมาไหม?
      if (millis() - lastUnlockTime <= LEGIT_OPEN_DURATION) {
        pushLog("ปลดล็อกตู้");
      } else {
        pushLog("เตือนภัย: ประตูถูกงัด!");  // ถ้าเปิดโดยไม่ได้กดแอป = งัดแงะ
      }
    }
  } else if (lightValue > 3000) {  // มืด = ประตูปิด
    isLdrTriggered = false;
    if (isDoorOpen) {
      isDoorOpen = false;
    }
  }
}

void setup() {
  Serial.begin(115200);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(RLED_PIN, OUTPUT);
  pinMode(GLED_PIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, HIGH);

  digitalWrite(GLED_PIN, HIGH);

  WiFi.begin(ssid, pass);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\เชื่อมwifi");

  configTime(7 * 3600, 0, "pool.ntp.org");
}

void loop() {
  checkUnlockCommand(); 
  checkSensors();       
  delay(500);
}