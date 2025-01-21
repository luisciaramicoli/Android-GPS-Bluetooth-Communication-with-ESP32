# RealTime GPS Data to ESP32 via Bluetooth

A project that sends real-time GPS data (latitude, longitude, and speed) from an Android device to an ESP32 microcontroller using Bluetooth communication. This repository contains the source code for both the Android application and its integration with the ESP32.

---

## Features
- **Real-Time GPS Tracking**: Captures GPS data from the Android device.
- **Bluetooth Communication**: Sends GPS data via Bluetooth to an ESP32 device.
- **Customizable Update Interval**: Configurable intervals for GPS data updates.
- **Seamless Connection Management**: Automatically handles Bluetooth connection and disconnection.

---

## Prerequisites

### **Hardware Requirements**
- ESP32 microcontroller with Bluetooth enabled.
- Android device with GPS and Bluetooth capabilities.

### **Software Requirements**
- Android Studio for running and modifying the Android application.
- Python or Arduino IDE for configuring ESP32.

---

## Installation

### **Clone Repository**
```bash
git clone https://github.com/yourusername/RealTime-GPS-Bluetooth-ESP32.git
cd RealTime-GPS-Bluetooth-ESP32
```

### **Android Application Setup**
1. Open the `Android` folder in Android Studio.
2. Build and run the application on an Android device.
3. Grant the required permissions (Location, Bluetooth).

### **ESP32 Configuration**
1. Use the provided Arduino or MicroPython script (`esp32_receiver.ino`) to configure your ESP32.
2. Upload the script to the ESP32 using Arduino IDE or another compatible platform.

---

## Usage

1. **Start the Android App**:
   - Launch the application on your Android device.
   - Ensure Bluetooth is enabled.
   - Grant location permissions.

2. **Pair with ESP32**:
   - Open the app and scan for available Bluetooth devices.
   - Select your ESP32 from the list to establish a connection.

3. **Receive GPS Data**:
   - The ESP32 will display or process the received GPS data in real-time.

---

## Folder Structure
```
RealTime-GPS-Bluetooth-ESP32
├── Android
│   ├── app
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── com.example.gpsbluetooth
│   │   │   │   │       ├── MainActivity.kt
│   │   │   │   │       ├── BluetoothManager.kt
│   │   │   │   │       ├── LocationManager.kt
│   │   │   │   │       └── ... (other files)
├── ESP32
│   ├── esp32_receiver.ino
├── README.md
```

---

## Configuration

### **Update GPS Interval**
In the file `LocationManager.kt`, modify the `interval` and `setMinUpdateIntervalMillis` values to set the GPS update frequency.

### **Bluetooth UUID**
Ensure the Bluetooth UUID in `BluetoothManager.kt` matches the UUID of your ESP32.

```kotlin
private val uuidSpp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
```

---

## Contributing

Contributions are welcome! Please follow these steps:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature-name`).
3. Commit your changes (`git commit -m 'Add some feature'`).
4. Push to the branch (`git push origin feature-name`).
5. Create a pull request.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments
- Android Bluetooth and GPS documentation.
- ESP32 community and resources.

---

### Contact
For any inquiries, please reach out via [andreseptian07@icloud.com].
