package com.example.hardware

enum class ConnectionMode {
    DISCONNECTED,
    CONNECTING_USB,
    CONNECTING_BT,
    USB,
    BLUETOOTH,
    SIMULATOR
}

enum class SensorType {
    GOLD_RADAR_X20,
    FMG3,
    FLC100,
    HMC5883L
}
