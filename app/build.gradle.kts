plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dronescan.msdksample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dronescan.msdksample"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes.add("META-INF/rxjava.properties")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/LGPL2.1")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/DEPENDENCIES")
            // No uses pickFirsts para libc++_shared.so a menos que sea estrictamente necesario,
            // ya que puede causar problemas si múltiples librerías lo incluyen.
            // Si el error de libc++_shared.so vuelve, podemos añadir pickFirsts específicos.
        }
        // Eliminamos useLegacyPackaging = true
        // jniLibs {
        //     useLegacyPackaging = true
        // }
    }
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "src/main/kotlin")
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit) // Esta línea es para JUnit 4
    androidTestImplementation(libs.androidxTestExtJunit) // ¡CORREGIDO! Usando el nuevo alias
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Dependencias del DJI Mobile SDK (Versión 5.x)
    implementation(libs.dji.sdk)
    implementation(libs.dji.uxsdk)
    implementation(libs.dji.networkImp)

    // Dependencias de ZXing para escaneo de códigos de barras/QR
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
}
