package com.dronescan.msdksample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.dronescan.msdksample.ui.theme.DroneScan_V3Theme
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import dji.v5.common.error.IDJIError
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.manager.product.ProductManager
import dji.v5.manager.aircraft.camera.CameraManager
import dji.v5.manager.aircraft.flightcontroller.FlightControllerManager
import dji.v5.manager.KeyManager
import dji.v5.common.key.model.DJIKey
import dji.v5.common.key.model.KeyType
import dji.v5.common.key.model.Scope
import dji.v5.common.key.model.product.ProductKey
import dji.v5.manager.aircraft.camera.enums.CameraMode
import dji.v5.manager.aircraft.camera.enums.CameraShootPhotoMode
import dji.v5.utils.common.ToastUtils
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.CONNECTED
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.DISCONNECTED
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.CONNECTING
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.DISCONNECTING
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.NOT_ACTIVATED
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.NOT_SUPPORTED
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.INITIALIZING
import dji.v5.manager.interfaces.IDeviceManager.DeviceConnectionState.UNKNOWN
import dji.v5.manager.common.CommonCallbacks
import dji.v5.manager.aircraft.flightcontroller.FlightControllerKey
import dji.v5.manager.aircraft.flightcontroller.FlightControllerManager
import dji.v5.manager.aircraft.flightcontroller.FlightControllerManager.Attitude
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    // Permisos necesarios para el DJI SDK
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION, // Para Android 10+
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            ToastUtils.showToast("Todos los permisos concedidos. Inicializando SDK de DJI...")
            // Inicializar el SDK de DJI después de obtener los permisos
            initDJISDK()
        } else {
            ToastUtils.showToast("Algunos permisos no fueron concedidos. La aplicación podría no funcionar correctamente.")
            Log.e(TAG, "Permissions not granted.")
        }
    }

    private var djiProduct: ProductManager? = null
    private var cameraInstance: CameraManager? = null
    private var flightControllerInstance: FlightControllerManager? = null
    private var capturedImage: Bitmap? = null
    private var scanningText: String = "Esperando conexión con el drone..."
    private var scanResult: String = ""
    private var scanSuccess: Boolean = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroneScan_V3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Estado para controlar la pantalla actual
                    var currentScreen by remember { mutableStateOf(AppScreen.LOADING) }
                    // Estado para el mensaje de carga/conexión
                    var loadingMessage by remember { mutableStateOf("Iniciando aplicación...") }

                    // Observador de cambios de conexión del producto
                    DisposableEffect(Unit) {
                        val productConnectionListener = object : CommonCallbacks.KeyListener<DeviceConnectionState> {
                            override fun onUpdate(value: DeviceConnectionState) {
                                Log.d(TAG, "Product connection state: $value")
                                when (value) {
                                    CONNECTED -> {
                                        djiProduct = ProductManager.getInstance()
                                        loadingMessage = "Drone conectado: ${djiProduct?.productModel?.name ?: "Desconocido"}"
                                        ToastUtils.showToast(loadingMessage)
                                        currentScreen = AppScreen.SCAN
                                        setupCamera()
                                    }
                                    DISCONNECTED -> {
                                        djiProduct = null
                                        loadingMessage = "Drone desconectado."
                                        ToastUtils.showToast(loadingMessage)
                                        currentScreen = AppScreen.LOADING // Volver a la pantalla de carga/conexión
                                    }
                                    CONNECTING -> {
                                        loadingMessage = "Conectando al drone..."
                                    }
                                    DISCONNECTING -> {
                                        loadingMessage = "Desconectando del drone..."
                                    }
                                    NOT_ACTIVATED -> {
                                        loadingMessage = "SDK no activado. Por favor, asegúrese de tener una conexión a internet."
                                    }
                                    NOT_SUPPORTED -> {
                                        loadingMessage = "Dispositivo no soportado."
                                    }
                                    INITIALIZING -> {
                                        loadingMessage = "Inicializando conexión..."
                                    }
                                    UNKNOWN -> {
                                        loadingMessage = "Estado de conexión desconocido."
                                    }
                                }
                            }
                        }
                        // Escuchar cambios en el estado de conexión del producto
                        KeyManager.getInstance().listen(ProductKey.KeyConnection, Scope.PRODUCT, productConnectionListener)

                        onDispose {
                            // Limpiar el listener cuando el componente se destruye
                            KeyManager.getInstance().cancelListen(ProductKey.KeyConnection, productConnectionListener)
                        }
                    }

                    when (currentScreen) {
                        AppScreen.LOADING -> {
                            LoadingScreen(loadingMessage)
                        }
                        AppScreen.SCAN -> {
                            ScanScreen(
                                scanningText = scanningText,
                                capturedImage = capturedImage,
                                scanResult = scanResult,
                                scanSuccess = scanSuccess,
                                onPhotoCaptured = { bitmap ->
                                    capturedImage = bitmap
                                    scanBarcode(bitmap)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Solicitar permisos al iniciar la actividad
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in REQUIRED_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            ToastUtils.showToast("Todos los permisos ya concedidos. Inicializando SDK de DJI...")
            initDJISDK()
        }
    }

    private fun initDJISDK() {
        SDKManager.getInstance().init(applicationContext, object : SDKManagerCallback {
            override fun onRegister(error: IDJIError?) {
                if (error == null) {
                    ToastUtils.showToast("SDK de DJI registrado exitosamente.")
                    Log.d(TAG, "SDK registered successfully.")
                } else {
                    ToastUtils.showToast("Fallo al registrar el SDK de DJI: ${error.description()}")
                    Log.e(TAG, "SDK registration failed: ${error.description()}")
                }
            }

            override fun onProductDisconnect(productId: Int) {
                // Ya manejado por el KeyListener en el Composable
            }

            override fun onProductConnect(productId: Int) {
                // Ya manejado por el KeyListener en el Composable
            }

            override fun onProductChanged(product: ProductManager?) {
                // Ya manejado por el KeyListener en el Composable
            }

            override fun onComponentChange(
                componentKey: DJIKey<*, *>,
                oldComponent: dji.v5.common.key.model.BaseComponent?,
                newComponent: dji.v5.common.key.model.BaseComponent?
            ) {
                // No es necesario manejar aquí si ya lo haces con KeyManager.getInstance().listen
            }

            override fun onInitProcess(event: SDKManager.DJISDKInitEvent, totalProcess: Int) {
                Log.d(TAG, "Init process: ${event.name}, progress: $totalProcess%")
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                Log.d(TAG, "Database download progress: $current / $total")
            }
        })
    }

    private fun setupCamera() {
        cameraInstance = CameraManager.getInstance()
        flightControllerInstance = FlightControllerManager.getInstance()

        // Configurar la cámara para tomar fotos
        cameraInstance?.apply {
            setCameraMode(CameraMode.SHOOT_PHOTO, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("Cámara en modo foto.")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("Error al establecer modo foto: ${error.description()}")
                }
            })

            setShootPhotoMode(CameraShootPhotoMode.SINGLE, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("Modo de disparo: Sencillo.")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("Error al establecer modo de disparo: ${error.description()}")
                }
            })
        }
    }

    private fun shootPhoto() {
        cameraInstance?.apply {
            setCameraMode(CameraMode.SHOOT_PHOTO, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    startShootPhoto(object : CommonCallbacks.CompletionCallback {
                        override fun onSuccess() {
                            ToastUtils.showToast("Foto tomada")
                            captureVideoFrame()
                        }

                        override fun onFailure(error: IDJIError) {
                            ToastUtils.showToast("Error al tomar la foto: ${error.description()}")
                        }
                    })
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("Error al cambiar el modo de la cámara: ${error.description()}")
                }
            })
        }
    }

    private fun captureVideoFrame() {
        // Simular captura de foto
        val dummyBitmap = BitmapFactory.decodeResource(LocalContext.current.resources, R.drawable.placeholder_drone_image) // Reemplaza con una imagen de placeholder real
        capturedImage = dummyBitmap
        scanBarcode(dummyBitmap)
    }

    private fun scanBarcode(bitmap: Bitmap) {
        val intArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = MultiFormatReader().decode(binaryBitmap)
            val format = result.barcodeFormat.toString()
            val value = result.text
            scanResult = "Formato: $format\nValor: $value"
            scanSuccess = true
            playSound(R.raw.success_sound) // Reproducir sonido de éxito
            saveToCSV(format, value)
        } catch (e: NotFoundException) {
            scanResult = "Código no encontrado"
            scanSuccess = false
            playSound(R.raw.error_sound) // Reproducir sonido de error
            ToastUtils.showToast("Código no encontrado")
        }
    }

    private fun saveToCSV(format: String, value: String) {
        val logFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DJIDroneScanLogs")
        if (!logFolder.exists()) {
            logFolder.mkdirs()
        }
        val logFile = File(logFolder, "logs.csv")
        val writer = FileWriter(logFile, true)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date())

        // Get telemetry data
        flightControllerInstance?.apply {
            KeyManager.getInstance().getValue(FlightControllerKey.KeyAttitude, object : CommonCallbacks.CompletionCallbackWithParam<Attitude?>() {
                override fun onSuccess(attitude: Attitude?) {
                    val roll = attitude?.roll ?: 0.0
                    val pitch = attitude?.pitch ?: 0.0
                    val yaw = attitude?.yaw ?: 0.0

                    writer.append("$date,$format,$value,$roll,$pitch,$yaw\n")
                    writer.flush()
                    writer.close()
                }

                override fun onFailure(error: IDJIError) {
                    Log.e(TAG, "Error al obtener datos de telemetría: ${error.description()}")
                }
            })
        }
    }

    private fun clearLogs() {
        val logFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DJIDroneScanLogs")
        if (logFolder.exists()) {
            val files = logFolder.listFiles()
            if (files != null) {
                for (file in files) {
                    file.delete()
                }
            }
            ToastUtils.showToast("Logs borrados")
        } else {
            ToastUtils.showToast("No hay logs para borrar")
        }
    }

    private fun playSound(soundResId: Int) {
        val mediaPlayer = MediaPlayer.create(this, soundResId)
        mediaPlayer.setOnCompletionListener { mediaPlayer.release() }
        mediaPlayer.start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_MEDIA_RECORD -> {
                shootPhoto()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

// Enum para controlar las pantallas de la aplicación
enum class AppScreen {
    LOADING,
    SCAN
}

@Composable
fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))
            Image(
                painter = painterResource(id = R.drawable.dji_logo), // Asegúrate de tener un dji_logo.png en tu carpeta drawable
                contentDescription = "DJI Logo",
                modifier = Modifier.size(120.dp)
            )
        }
    }
}

@Composable
fun ScanScreen(
    scanningText: String,
    capturedImage: Bitmap?,
    scanResult: String,
    scanSuccess: Boolean,
    onPhotoCaptured: (Bitmap) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = scanningText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = capturedImage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                capturedImage?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Captured Drone View",
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .aspectRatio(16f / 9f) // Proporción de aspecto común para video/foto
                            .background(Color.Gray)
                    )
                }
            }

            AnimatedVisibility(
                visible = capturedImage == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (capturedImage != null) {
                Text(
                    text = scanResult,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (scanSuccess) Color.Green else Color.Red,
                    modifier = Modifier.padding(16.dp)
                )

                Image(
                    painter = painterResource(id = if (scanSuccess) R.drawable.ic_check else R.drawable.ic_cross), // Asegúrate de tener ic_check.png e ic_cross.png en tu carpeta drawable
                    contentDescription = if (scanSuccess) "Código detectado" else "Código no detectado",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    DroneScan_V3Theme {
        LoadingScreen("Iniciando aplicación...")
    }
}
