import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

val portalBaseUrl = providers.gradleProperty("portalBaseUrl")
    .orElse(providers.environmentVariable("PORTAL_BASE_URL"))
    .orElse("").get().trim().trimEnd('/')
if (portalBaseUrl.isNotEmpty()) {
    val endpoint = URI(portalBaseUrl)
    require(endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null &&
        endpoint.rawQuery == null && endpoint.rawFragment == null && endpoint.path.isNullOrEmpty()) {
        "portalBaseUrl must be an HTTPS origin, for example https://portal.example.com."
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    val sqliteTemp = layout.buildDirectory.dir("room-tmp").get().asFile.apply { mkdirs() }
    kaptProcessJvmArgs.add("-Dorg.sqlite.tmpdir=${sqliteTemp.absolutePath}")
    kaptProcessJvmArgs.add("-Djava.io.tmpdir=${sqliteTemp.absolutePath}")
}

android {
    namespace = "com.venuelink.studio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.venuelink.studio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PORTAL_BASE_URL", "\"${portalBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    val navVersion = "2.9.5"
    val lifecycleVersion = "2.9.4"
    val cameraVersion = "1.5.0"
    val datastoreVersion = "1.1.7"

    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:$navVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.datastore:datastore-preferences:$datastoreVersion")
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("com.google.ar:core:1.33.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
