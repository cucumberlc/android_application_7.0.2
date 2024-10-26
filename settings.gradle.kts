/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

pluginManagement {
    repositories {
        google()
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

//ble_mesh aar
include(":ble_mesh-android_api-release")
//ble_mesh source code
val sourceCodeExists = File("../ble_mesh-android_api-v2").exists()
if (sourceCodeExists) {
    include(":ble_mesh-android_api")
    project(":ble_mesh-android_api").projectDir = File("../ble_mesh-android_api-v2/ble_mesh-android_api")
}
