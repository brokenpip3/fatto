buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

// allprojects block removed because it conflicts with RepositoryMode.FAIL_ON_PROJECT_REPOS in settings.gradle.kts
