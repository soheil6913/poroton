package com.example.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

class SensorManager(private val context: Context) : SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _connectionState = MutableStateFlow(ConnectionMode.SIMULATOR)
    val connectionState: StateFlow<ConnectionMode> = _connectionState.asStateFlow()

    private val _tvStatus = MutableStateFlow("متصل به شبیه‌ساز (Simulator)")
    val tvStatus: StateFlow<String> = _tvStatus.asStateFlow()

    private val _adcValue = MutableStateFlow(240)
    val adcValue: StateFlow<Int> = _adcValue.asStateFlow()

    private val _phaseShift = MutableStateFlow(0)
    val phaseShift: StateFlow<Int> = _phaseShift.asStateFlow()

    private val _magneticFluxUt = MutableStateFlow(48.5f)
    val magneticFluxUt: StateFlow<Float> = _magneticFluxUt.asStateFlow()

    private val _sensorType = MutableStateFlow(SensorType.GOLD_RADAR_X20)
    val sensorType: StateFlow<SensorType> = _sensorType.asStateFlow()

    private val _baudRate = MutableStateFlow(115200)
    val baudRate: StateFlow<Int> = _baudRate.asStateFlow()

    private val _compassHeading = MutableStateFlow(42.0f)
    val compassHeading: StateFlow<Float> = _compassHeading.asStateFlow()

    private var groundBalanceOffset = 0
    private var simulatedTick = 0L

    // Hardware sensors
    private val systemSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? AndroidSensorManager
    private val rotationSensor: Sensor? = systemSensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: systemSensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    init {
        startHardwareCompass()
        startTelemetryLoop()
    }

    private fun startHardwareCompass() {
        rotationSensor?.let {
            systemSensorManager?.registerListener(this, it, AndroidSensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun startTelemetryLoop() {
        scope.launch {
            while (isActive) {
                delay(120)
                simulatedTick++

                // Slight natural noise variation
                val noise = Random.nextInt(-6, 7)
                val base = 250 + groundBalanceOffset
                // Add soft wave
                val wave = (sin(simulatedTick * 0.15) * 12).toInt()
                val currentAdc = (base + noise + wave).coerceIn(0, 1023)

                // Only update when not manually overridden recently
                _adcValue.value = currentAdc
                _phaseShift.value = ((currentAdc - 250) / 10).coerceIn(-90, 90)
                _magneticFluxUt.value = 48.0f + (currentAdc - 250) * 0.05f

                // If real hardware sensor is not active, gently oscillate compass
                if (rotationSensor == null) {
                    val fakeHeading = (45f + sin(simulatedTick * 0.03) * 15f).toFloat()
                    _compassHeading.value = (fakeHeading + 360f) % 360f
                }
            }
        }
    }

    fun autoConnect() {
        when (_connectionState.value) {
            ConnectionMode.SIMULATOR -> {
                _connectionState.value = ConnectionMode.CONNECTING_USB
                _tvStatus.value = "در حال جستجوی درایور USB CH340..."
                scope.launch {
                    delay(1200)
                    _connectionState.value = ConnectionMode.USB
                    _tvStatus.value = "متصل به پورت سریال USB (115200 bps)"
                }
            }
            ConnectionMode.USB -> {
                _connectionState.value = ConnectionMode.CONNECTING_BT
                _tvStatus.value = "جستجوی بلوتوث SPP Radar..."
                scope.launch {
                    delay(1000)
                    _connectionState.value = ConnectionMode.BLUETOOTH
                    _tvStatus.value = "متصل به بلوتوث (BT-SPP 9600)"
                }
            }
            ConnectionMode.BLUETOOTH -> {
                _connectionState.value = ConnectionMode.DISCONNECTED
                _tvStatus.value = "قطع ارتباط (Disconnected)"
            }
            ConnectionMode.DISCONNECTED, ConnectionMode.CONNECTING_USB, ConnectionMode.CONNECTING_BT -> {
                _connectionState.value = ConnectionMode.SIMULATOR
                _tvStatus.value = "شبیه‌ساز هوشمند فعال (Simulator)"
            }
        }
    }

    fun setSensorType(type: SensorType) {
        _sensorType.value = type
    }

    fun setBaudRate(rate: Int) {
        _baudRate.value = rate
    }

    fun setSimulatedReading(adc: Int, phase: Int) {
        _adcValue.value = adc
        _phaseShift.value = phase
        _magneticFluxUt.value = 48.0f + (adc - 250) * 0.05f
    }

    fun calibrateGroundBalance() {
        // Zero out baseline
        groundBalanceOffset = 250 - _adcValue.value
        _adcValue.value = 250
        _phaseShift.value = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR || event.sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            AndroidSensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationAngles = FloatArray(3)
            AndroidSensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            _compassHeading.value = (azimuthDegrees + 360f) % 360f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
