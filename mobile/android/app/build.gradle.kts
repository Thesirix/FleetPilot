plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.fleetpilot.driver"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fleetpilot.driver"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The journey through the running platform only runs when asked for: ./gradlew connectedDebugAndroidTest -Pplatform
        if (!project.hasProperty("platform")) {
            testInstrumentationRunnerArguments["notClass"] = "com.fleetpilot.driver.PlatformJourneyTest"
        }

        // The emulator reaches the platform of the computer through "adb reverse" (see the README of the app): the
        // same URLs as in the browser, so the tokens of Keycloak carry the issuer the services expect.
        buildConfigField("String", "API_BASE_URL", "\"http://localhost/\"")
        buildConfigField("String", "AUTH_ISSUER", "\"http://localhost:8180/realms/fleetpilot\"")
        buildConfigField("String", "AUTH_CLIENT_ID", "\"fleetpilot-mobile\"")
        buildConfigField("String", "AUTH_REDIRECT_URI", "\"com.fleetpilot.driver:/oauth2redirect\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "com.fleetpilot.driver"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.fragment)
    implementation(libs.activity)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.appauth)
    implementation(libs.browser)
    implementation(libs.exifinterface)
    implementation(libs.osmdroid)

    testImplementation(libs.junit)
    testImplementation(libs.core.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.fragment.testing)
    androidTestImplementation(libs.uiautomator)
    debugImplementation(libs.fragment.testing.manifest)
}
