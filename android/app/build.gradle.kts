plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun environmentValue(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = environmentValue("ANDROID_SIGNING_STORE_FILE")
val releaseStorePassword = environmentValue("ANDROID_SIGNING_STORE_PASSWORD")
val releaseKeyAlias = environmentValue("ANDROID_SIGNING_KEY_ALIAS")
val releaseKeyPassword = environmentValue("ANDROID_SIGNING_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { it != null }
val hasReleaseSigning = releaseSigningValues.all { it != null }

check(!hasAnyReleaseSigningValue || hasReleaseSigning) {
    "Android release signing is partially configured. Set ANDROID_SIGNING_STORE_FILE, " +
        "ANDROID_SIGNING_STORE_PASSWORD, ANDROID_SIGNING_KEY_ALIAS and ANDROID_SIGNING_KEY_PASSWORD together."
}

val versionNameInput = environmentValue("ANDROID_VERSION_NAME")
val versionCodeInput = environmentValue("ANDROID_VERSION_CODE")
check((versionNameInput == null) == (versionCodeInput == null)) {
    "ANDROID_VERSION_NAME and ANDROID_VERSION_CODE must be configured together. " +
        "Use scripts/android-version.sh to derive the version code."
}
val resolvedVersionCode = versionCodeInput?.toIntOrNull()
    ?: if (versionCodeInput == null) 10_209_999 else error("ANDROID_VERSION_CODE must be a positive integer.")
check(resolvedVersionCode > 0) { "ANDROID_VERSION_CODE must be a positive integer." }
val resolvedVersionName = versionNameInput ?: "1.2.9"

android {
    // The modular implementation keeps its existing source namespace to avoid a
    // risky package-only rewrite. The public product identity is com.nowen.video.
    namespace = "com.nowen.video.v2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nowen.video"
        minSdk = 26
        targetSdk = 28
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        // 本地 release 回退：未提供 production keystore 时复用 debug.keystore。
        // 让 release APK 仍能装到电视上（debug.keystore 跨机器通用）。该 key
        // 与仓库固定的生产签名 SHA-256 不一致，仅供本地调试用，不能上 Play。
        create("debugFallback") {
            val debugStore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storeFile = debugStore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // 临时关闭混淆与资源压缩：让 release 在不提供 production keystore 的
            // 情况下也能产出可装的 APK；libgav1 JNI 反射 + Compose runtime 对 R8
            // 配置非常敏感，关掉可以避开一类冷启动 / native 调用失败。
            // 恢复生产签名与混淆时，反向改回 true 即可。
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debugFallback")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 平台变体：phone 为原手机版；tv 为 Android TV 版（独立包名 .tv）。
    flavorDimensions += "platform"
    productFlavors {
        create("phone") {
            dimension = "platform"
        }
        create("tv") {
            dimension = "platform"
            applicationIdSuffix = ".tv"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        // 本地 release 临时关闭 Google Play 对 targetSdk 的硬性要求。
        // 当前 targetSdk=28 是为了在 Android 9 电视上跑而设的；Android 9 电视
        // 不通过 Play 上传，所以这个 lint 不需要拦截本地构建。
        disable += "ExpiredTargetSdkVersion"
        abortOnError = false
        checkReleaseBuilds = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":feature:main"))
    "tvImplementation"(project(":feature:tv"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
