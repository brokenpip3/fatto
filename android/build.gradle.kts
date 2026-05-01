buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    }
}

// allprojects block removed because it conflicts with RepositoryMode.FAIL_ON_PROJECT_REPOS in settings.gradle.kts
