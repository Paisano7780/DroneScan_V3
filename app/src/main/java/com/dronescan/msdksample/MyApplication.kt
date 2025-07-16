package com.dronescan.msdksample

import android.app.Application
import android.content.Context
import android.util.Log
import com.secneo.sdk.Helper
import dji.v5.common.error.IDJIError
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.manager.product.ProductManager
import dji.v5.common.key.model.DJIKey
import dji.v5.common.key.model.BaseComponent

class MyApplication : Application() {

    private val TAG = "MyApplication"

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Importante: Inicializa el Helper de DJI aquí para cargar las clases del SDK
        Helper.install(this)
        Log.d(TAG, "DJI Helper installed.")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MyApplication onCreate. Initializing DJI SDK...")
        // ¡CRUCIAL! Inicializa el SDK de DJI aquí
        SDKManager.getInstance().init(applicationContext, object : SDKManagerCallback {
            override fun onRegister(error: IDJIError?) {
                if (error == null) {
                    Log.d(TAG, "SDK de DJI registrado exitosamente.")
                } else {
                    Log.e(TAG, "Fallo al registrar el SDK de DJI: ${error.description()}")
                }
            }

            override fun onProductDisconnect(productId: Int) {
                Log.d(TAG, "Producto DJI desconectado: $productId")
            }

            override fun onProductConnect(productId: Int) {
                Log.d(TAG, "Producto DJI conectado: $productId")
            }

            override fun onProductChanged(product: ProductManager?) {
                Log.d(TAG, "Producto DJI cambiado: ${product?.productModel?.name ?: "N/A"}")
            }

            override fun onComponentChange(
                componentKey: DJIKey<*, *>,
                oldComponent: BaseComponent?,
                newComponent: BaseComponent?
            ) {
                Log.d(TAG, "Componente DJI cambiado: ${componentKey.name()}")
            }

            override fun onInitProcess(event: SDKManager.DJISDKInitEvent, totalProcess: Int) {
                Log.d(TAG, "Proceso de inicialización DJI: ${event.name}, progreso: $totalProcess%")
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                Log.d(TAG, "Progreso de descarga de base de datos: $current / $total")
            }
        })
    }
}
