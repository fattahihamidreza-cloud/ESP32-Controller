package com.example.esp32controller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    private var pendingBluetoothAction: (() -> Unit)? = null

    private fun requiredBluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasBluetoothPermissions(): Boolean =
        requiredBluetoothPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestBluetoothPermissions() {
        permissionLauncher.launch(requiredBluetoothPermissions())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ESP32Screen(
                    hasBluetoothPermissions = { hasBluetoothPermissions() },
                    requestBluetoothPermissions = { requestBluetoothPermissions() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ESP32Screen(
    hasBluetoothPermissions: () -> Boolean,
    requestBluetoothPermissions: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("Wi-Fi") }
    var host by remember { mutableStateOf("192.168.4.1") }
    var port by remember { mutableStateOf("8080") }
    var deviceAddress by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("LED_ON") }
    var status by remember { mutableStateOf("قطع") }
    var received by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("آماده اتصال به ESP32") }
    var socket by remember { mutableStateOf<Socket?>(null) }
    var btSocket by remember { mutableStateOf<BluetoothSocket?>(null) }
    var readerJob by remember { mutableStateOf<Job?>(null) }

    fun appendLog(line: String) { log = (log + "\n" + line).takeLast(6000) }

    fun closeConnections() {
        readerJob?.cancel()
        readerJob = null
        try { socket?.close() } catch (_: Exception) {}
        try { btSocket?.close() } catch (_: Exception) {}
        socket = null
        btSocket = null
        status = "قطع"
    }

    fun startReader(input: java.io.InputStream) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(input))
                while (true) {
                    val line = reader.readLine() ?: break
                    withContext(Dispatchers.Main) {
                        received = line
                        appendLog("RX: $line")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("دریافت پایان یافت: ${e.message ?: "اتصال بسته شد"}") }
            }
        }
    }

    fun connectWifi() {
        val p = port.toIntOrNull()
        if (p == null || p !in 1..65535) {
            appendLog("شماره پورت معتبر نیست")
            return
        }
        closeConnections()
        status = "در حال اتصال..."
        scope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    Socket().apply { connect(InetSocketAddress(host.trim(), p), 5000); soTimeout = 0 }
                }
                socket = s
                status = "متصل از طریق Wi-Fi"
                appendLog("اتصال Wi-Fi برقرار شد: ${host.trim()}:$p")
                startReader(s.getInputStream())
            } catch (e: Exception) {
                status = "خطا"
                appendLog("خطای Wi-Fi: ${e.message}")
            }
        }
    }

    fun connectBluetooth() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            appendLog("ابتدا مجوز Bluetooth را تأیید کن و دوباره اتصال را بزن")
            return
        }
        if (deviceAddress.isBlank()) {
            appendLog("آدرس MAC دستگاه Bluetooth را وارد کن")
            return
        }
        closeConnections()
        status = "در حال اتصال..."
        scope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: throw IllegalStateException("Bluetooth در این گوشی موجود نیست")
                    val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress.trim())
                    adapter.cancelDiscovery()
                    device.createRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
                }
                btSocket = s
                status = "متصل از طریق Bluetooth"
                appendLog("اتصال Bluetooth برقرار شد")
                startReader(s.inputStream)
            } catch (e: Exception) {
                status = "خطا"
                appendLog("خطای Bluetooth: ${e.message}")
            }
        }
    }

    fun sendCommand() {
        val data = (command.trim() + "\n").toByteArray(Charsets.UTF_8)
        scope.launch(Dispatchers.IO) {
            try {
                val out: OutputStream? = if (mode == "Wi-Fi") socket?.getOutputStream() else btSocket?.outputStream
                if (out == null) throw IllegalStateException("ابتدا اتصال را برقرار کن")
                out.write(data)
                out.flush()
                withContext(Dispatchers.Main) { appendLog("TX: ${command.trim()}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("خطای ارسال: ${e.message}") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("کنترلر ESP32") })
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("وضعیت: $status", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = mode == "Wi-Fi", onClick = { mode = "Wi-Fi" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Wi-Fi") }
                SegmentedButton(selected = mode == "Bluetooth", onClick = { mode = "Bluetooth" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Bluetooth") }
            }

            if (mode == "Wi-Fi") {
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("IP برد ESP32") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("TCP Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } else {
                OutlinedTextField(value = deviceAddress, onValueChange = { deviceAddress = it }, label = { Text("Bluetooth MAC Address") }, placeholder = { Text("مثلاً AA:BB:CC:11:22:33") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("دستگاه باید قبلاً از تنظیمات Bluetooth گوشی Pair شده باشد. این نسخه از Bluetooth Classic SPP استفاده می‌کند.")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (mode == "Wi-Fi") connectWifi() else connectBluetooth() }, modifier = Modifier.weight(1f)) { Text("اتصال") }
                OutlinedButton(onClick = { closeConnections(); appendLog("اتصال قطع شد") }, modifier = Modifier.weight(1f)) { Text("قطع اتصال") }
            }

            HorizontalDivider()
            Text("ارسال فرمان", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("متن فرمان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { sendCommand() }, modifier = Modifier.fillMaxWidth()) { Text("ارسال به ESP32") }

            Text("آخرین داده دریافتی: $received")
            Text("گزارش ارتباط", style = MaterialTheme.typography.titleMedium)
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Text(log, Modifier.padding(12.dp))
            }
            Text(
                "توجه: این نسخه اولیه از ارتباط TCP خام روی Wi-Fi و Bluetooth Classic SPP استفاده می‌کند. برنامه سمت ESP32 باید همین پروتکل و قالب فرمان/پاسخ را پیاده‌سازی کند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
