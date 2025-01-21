#include <BluetoothSerial.h>

BluetoothSerial SerialBT;

String receivedData = "";
float speedInMetersPerSecond = 0.0;
float speedInKilometersPerHour = 0.0;

void setup() {

  Serial.begin(115200);

  SerialBT.begin("ESP32_GPS");  // Ganti dengan nama Bluetooth yang diinginkan
  Serial.println("Bluetooth is ready. Waiting for GPS data...");
}

void loop() {
  if (SerialBT.available()) {
    char incomingChar = SerialBT.read(); 

    if (incomingChar == '\n') {
      processReceivedData(receivedData);
      receivedData = ""; 
    } else {
      receivedData += incomingChar; 
    }
  }
}


void processReceivedData(String data) {
  Serial.println("Received data: " + data);
  int speedIndex = data.indexOf("Speed:");
  if (speedIndex != -1) {
    String speedString = data.substring(speedIndex + 7, data.indexOf(" m/s", speedIndex));
    speedInMetersPerSecond = speedString.toFloat();
    speedInKilometersPerHour = speedInMetersPerSecond * 3.6;
    displaySpeed();
  } else {
    Serial.println("Speed data not found in the received message.");
  }
}

void displaySpeed() {
  Serial.println("Speed Data:");
  Serial.printf("  - %.2f m/s\n", speedInMetersPerSecond);
  Serial.printf("  - %.2f km/h\n", speedInKilometersPerHour);
  Serial.println("--------------------------");
}
