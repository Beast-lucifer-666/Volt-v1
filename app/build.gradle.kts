import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val versionPropertiesFile = file("version.properties")

fun getOrIncrementVersion(): Pair<Int, String> {
    val props = Properties()
    if (versionPropertiesFile.exists()) {
        FileInputStream(versionPropertiesFile).use { props.load(it) }
    } else {
        props["MAJOR_VERSION"] = "1"
        props["MINOR_VERSION"] = "0"
        props["PATCH_VERSION"] = "0"
        props["VERSION_CODE"] = "1"
    }

    var major = props.getProperty("MAJOR_VERSION", "1").toInt()
    var minor = props.getProperty("MINOR_VERSION", "0").toInt()
    var patch = props.getProperty("PATCH_VERSION", "0").toInt()
    var code = props.getProperty("VERSION_CODE", "1").toInt()

    val isBuildTaskRequested = gradle.startParameter.taskNames.any { task ->
        val name = task.lowercase()
        name.contains("assemble") || name.contains("bundle") || name.contains("build") || name.contains("install")
    }

    if (isBuildTaskRequested) {
        patch += 1
        code += 1

        if (patch > 100) {
            patch = 0
            minor += 1
            if (minor >= 100) {
                minor = 0
                major += 1
            }
        }

        props["MAJOR_VERSION"] = major.toString()
        props["MINOR_VERSION"] = minor.toString()
        props["PATCH_VERSION"] = patch.toString()
        props["VERSION_CODE"] = code.toString()
        FileOutputStream(versionPropertiesFile).use {
            props.store(it, "Auto-incremented version properties")
        }
    }

    return Pair(code, "$major.$minor.$patch")
}

val (appVersionCode, appVersionName) = getOrIncrementVersion()

android {
    namespace = "com.energy.volt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.energy.volt"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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

    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)

    // Lifecycle & Core
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}