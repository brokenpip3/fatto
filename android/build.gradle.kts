buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

// allprojects block removed because it conflicts with RepositoryMode.FAIL_ON_PROJECT_REPOS in settings.gradle.kts
