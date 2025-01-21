import serial


bluetooth_serial_port = "/dev/ttyUSB0"  # Change with your port
baud_rate = 9600 

try:
  
    ser = serial.Serial(bluetooth_serial_port, baud_rate, timeout=1)
    print("Koneksi Bluetooth berhasil dibuka di port:", bluetooth_serial_port)

    while True:

        if ser.in_waiting > 0:
            data = ser.readline().decode('utf-8').strip()
            print("Data diterima:", data)

except serial.SerialException as e:
    print("Kesalahan serial:", e)
except KeyboardInterrupt:
    print("Program dihentikan oleh pengguna.")
finally:
    if 'ser' in locals() and ser.is_open:
        ser.close()
        print("Koneksi Bluetooth ditutup.")
