/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

val versionMajor = 7
val versionMinor = 0
val versionPatch = 2

var sourceCodeExists: Boolean = false

plugins {
    id("com.android.application") version "8.4.2"
    id("org.jetbrains.kotlin.android") version "1.8.21"
    id("org.jetbrains.kotlin.kapt") version "1.8.21"
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.8.21"
    id("com.google.dagger.hilt.android") version "2.45"
    id("de.mannodermaus.android-junit5") version "1.9.3.0"
    id("com.ibotta.gradle.aop") version "1.4.1"
}

buildscript {
    repositories {
        google()
        maven { url = uri("https://maven.google.com") }
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
        classpath("org.jetbrains.dokka:dokka-base:1.8.10")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.8.10")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

android {
    namespace = "com.siliconlabs.bluetoothmesh"

    sourceCodeExists = File("../ble_mesh-android_api-v2").exists()

    compileSdk = 34
    defaultConfig {
        val buildTimestamp = "${System.currentTimeMillis() / 1000}L"

        applicationId = "com.siliconlabs.bluetoothmesh"
        minSdk = 30
        targetSdk = 34
        versionCode = versionMajor * 10_000_000 + versionMinor * 100_000 + versionPatch * 1000
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"
        buildConfigField("long", "BUILD_TIMESTAMP", buildTimestamp)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        archivesName.set("BluetoothMesh-${versionName}")
    }

    lint {
        abortOnError = false
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro")
        }

        getByName("debug") {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs")))
    implementation("com.google.android.material:material:1.12.0")
    implementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.android.databinding:viewbinding:8.5.0")
    implementation("com.github.kirich1409:viewbindingpropertydelegate-noreflection:1.5.9")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.crypto.tink:tink-android:1.9.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")

    implementation("org.redundent:kotlin-xml-builder:1.9.0")

    // BTMesh library
    implementation("com.google.code.gson:gson:2.10.1")

    if (sourceCodeExists) {
        implementation(project(":ble_mesh-android_api"))
    } else {
        implementation(project(":ble_mesh-android_api-release"))
    }

    // logging
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-jdk14:2.0.7")
    implementation("org.tinylog:tinylog-impl:2.6.1")
    implementation("org.tinylog:tinylog-api-kotlin:2.6.1")
    implementation("org.aspectj:aspectjrt:1.9.19")
    implementation("com.jcabi:jcabi-aspects:0.24.1")

    // coroutines - asynchronous execution of tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // Apache Commons Compress - defines an API for working with compression and archive formats
    implementation("org.apache.commons:commons-compress:1.23.0")

    // RxJava
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")
    implementation("com.jakewharton.rx2:replaying-share:2.2.0")

    // Hilt (dagger wrapper)
    implementation("com.google.dagger:hilt-android:2.46.1")
    kapt("com.google.dagger:hilt-compiler:2.46.1")

    // View
    implementation("com.daimajia.swipelayout:library:1.2.0@aar")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testImplementation("io.mockk:mockk:1.13.5")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("org.mockito:mockito-android:5.3.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") {
        exclude(mapOf("group" to "com.android.support", "module" to "support-annotations"))
    }
}

kapt {
    correctErrorTypes = true
}

hilt {
    enableAggregatingTask = false   // cannot be enabled because it clashes with aop transformation
}