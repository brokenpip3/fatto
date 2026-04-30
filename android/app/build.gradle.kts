import org.gradle.api.JavaVersion
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
}

android {
    namespace = "com.brokenpip3.fatto"
    compileSdk = 34

    val versionProps =
        Properties().apply {
            val versionPropsFile = rootProject.file("version.properties")
            if (versionPropsFile.exists()) {
                load(versionPropsFile.inputStream())
            }
        }

    val buildTime = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    defaultConfig {
        applicationId = "com.brokenpip3.fatto"
        minSdk = 26
        targetSdk = 34
        versionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()
        versionName = versionProps.getProperty("VERSION_NAME", "1.0.0")

        buildConfigField("String", "BUILD_DATE", "\"$buildTime\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        manifestPlaceholders["appLabel"] = "Fatto"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("FATTO_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("FATTO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FATTO_KEY_ALIAS")
                keyPassword = System.getenv("FATTO_KEYSTORE_PASSWORD")
            }
        }

        create("beta") {
            val keystorePath = System.getenv("FATTO_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("FATTO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FATTO_KEY_ALIAS")
                keyPassword = System.getenv("FATTO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            manifestPlaceholders["appLabel"] = "Fatto Beta"
            val betaSigning = signingConfigs.getByName("beta")
            if (betaSigning.storeFile != null) {
                signingConfig = betaSigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    tasks.withType<Test> {
        systemProperty("jna.library.path", System.getProperty("jna.library.path") ?: "")
        systemProperty("jna.debug_load", System.getProperty("jna.debug_load") ?: "")
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin", "src/main/uniffi")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/uniffi/**")
    }
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
}
