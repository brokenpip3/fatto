tw_cmd := "docker compose exec taskwarrior-client task"

# clean jni libraries
build-clean-jni:
    rm -rf android/app/src/main/jniLibs/*

# build rust lib for specific targets (default: arm64-v8a)
build-rust targets="arm64-v8a":
    @cargo ndk --version >/dev/null 2>&1 || { echo >&2 "cargo-ndk is required but not installed. Run 'cargo install cargo-ndk'."; exit 1; }
    cd rust/taskchampion-android && \
    cargo ndk \
        $(for t in {{ targets }}; do echo "-t $t"; done) \
        -o ../../android/app/src/main/jniLibs \
        build --release

# build rust lib for all supported architectures
build-rust-all: build-clean-jni
    just build-rust "arm64-v8a x86_64"

# generate uniffi bindings for kotlin
build-bindings: (build-rust "x86_64")
    cd rust/taskchampion-android && \
    cargo build --release && \
    cargo run --bin uniffi-bindgen generate \
        --library target/release/libtaskchampion_android.so \
        --language kotlin \
        --no-format \
        --out-dir ../../android/app/src/main/uniffi

# build debug apk (assumes rust libs and bindings are already built)
build-debug-only:
    cd android && ./gradlew assembleDebug

# local debug build
build-debug: build-bindings
    cd android && ./gradlew assembleDebug

# build release apk (assumes rust libs and bindings are already built)
build-release-apk:
    @if [ -z "${FATTO_KEYSTORE_BASE64:-}" ]; then \
        echo "Error: FATTO_KEYSTORE_BASE64 not set. Release builds must be signed."; \
        exit 1; \
    fi
    @echo "Decoding release keystore..."
    @echo "${FATTO_KEYSTORE_BASE64:-}" | base64 -d > android/app/release.jks
    @export FATTO_KEYSTORE_PATH=release.jks; \
    trap 'rm -f android/app/release.jks' EXIT; \
    cd android && ./gradlew assembleRelease
    @mkdir -p dist
    @VERSION=$(grep 'VERSION_NAME=' android/version.properties | cut -d'=' -f2); \
    if [ -f android/app/build/outputs/apk/release/app-release.apk ]; then \
        cp android/app/build/outputs/apk/release/app-release.apk dist/fatto-v$VERSION.apk; \
    else \
        cp android/app/build/outputs/apk/release/app-release-unsigned.apk dist/fatto-v$VERSION.apk; \
    fi; \
    echo "Release APK created at: dist/fatto-v$VERSION.apk"

# build and bundle release aab/apk
build-release: build-rust-all build-bindings build-release-apk

# build signed beta release
build-beta: build-rust-all build-bindings build-beta-apk

# build signed beta apk (assumes rust libs and bindings are already built)
build-beta-apk:
    @if [ -z "${FATTO_KEYSTORE_BASE64:-}" ]; then \
        echo "Error: FATTO_KEYSTORE_BASE64 not set. Beta builds must be signed."; \
        exit 1; \
    fi
    @echo "Decoding beta keystore..."
    @echo "${FATTO_KEYSTORE_BASE64:-}" | base64 -d > android/app/release.jks
    @export FATTO_KEYSTORE_PATH=release.jks; \
    trap 'rm -f android/app/release.jks' EXIT; \
    cd android && ./gradlew assembleBeta
    @mkdir -p dist
    @VERSION=$(grep 'VERSION_NAME=' android/version.properties | cut -d'=' -f2); \
    if [ -f android/app/build/outputs/apk/beta/app-beta.apk ]; then \
        cp android/app/build/outputs/apk/beta/app-beta.apk dist/fatto-v$VERSION-beta.apk; \
    else \
        cp android/app/build/outputs/apk/beta/app-beta-unsigned.apk dist/fatto-v$VERSION-beta.apk; \
    fi; \
    echo "Beta APK created at: dist/fatto-v$VERSION-beta.apk"

# run fast unit tests (Rust unit + Kotlin unit)
test-fast: test-rust test-kotlin

# run every test in the project (requires emulator and sync server)
test-all: sync-up run-emulator-start test-fast test-android test-integration run-emulator-stop sync-down

# run android tests in CI (headless)
test-ci-android: sync-up
    @echo "Creating AVD..."
    echo "no" | avdmanager create avd -n ci_emulator -k "system-images;android-34;google_apis_playstore;x86_64" --force
    @echo "Starting emulator..."
    (emulator -avd ci_emulator -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &)
    adb wait-for-device
    @echo "Emulator started. Granting permissions..."
    sleep 20
    adb shell pm grant com.brokenpip3.fatto android.permission.POST_NOTIFICATIONS || true
    @echo "Running tests..."
    cd android && ./gradlew connectedDebugAndroidTest
    @echo "Stopping emulator..."
    adb emu kill || true
    just sync-down

# run rust integration tests in CI
test-ci-rust: sync-up
    just test-integration
    just sync-down

# run rust unit tests
test-rust:
    cd rust/taskchampion-android && cargo test --lib

# run kotlin jvm unit tests
test-kotlin:
    cd android && ./gradlew testDebugUnitTest

# run kotlin instrumented tests (requires connected device/emulator)
test-android:
    @command -v adb >/dev/null 2>&1 || { echo >&2 "adb is required but not installed."; exit 1; }
    adb shell pm grant com.brokenpip3.fatto android.permission.POST_NOTIFICATIONS || true
    cd android && ./gradlew connectedDebugAndroidTest

# run rust integration tests against local tss
test-integration:
    cd rust/taskchampion-android && \
    TASKCHAMPION_SYNC_URL=http://localhost:8080 \
    TASKCHAMPION_CLIENT_ID=768d9f09-accd-406d-8685-7b977b83d5c6 \
    TASKCHAMPION_SYNC_SECRET=foobar \
    cargo test --test integration -- --nocapture

# format code
check-fmt:
    cd rust/taskchampion-android && cargo fmt --all
    cd android && ./gradlew ktlintFormat

# run linters (all)
check-lint: check-lint-rust check-lint-kotlin

# run rust linters
check-lint-rust:
    cd rust/taskchampion-android && cargo fmt --all -- --check
    cd rust/taskchampion-android && cargo clippy --all-targets --all-features -- -D warnings

# run kotlin linters
check-lint-kotlin:
    cd android && ./gradlew ktlintCheck detekt

# install debug apk on connected device/emulator
run-deploy: build-rust build-bindings
    @command -v adb >/dev/null 2>&1 || { echo >&2 "adb is required but not installed."; exit 1; }
    cd android && ./gradlew assembleDebug
    adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# start android emulator if not running
run-emulator-start:
    @command -v emulator >/dev/null 2>&1 || { echo >&2 "emulator is required but not installed."; exit 1; }
    @if adb devices | grep -q emulator; then \
        echo "Emulator is already running."; \
    else \
        export ANDROID_AVD_HOME="$HOME/.config/.android/avd" && \
        (emulator -avd dev_emulator -no-audio -no-boot-anim &) && \
        adb wait-for-device; \
    fi

# stop android emulator
run-emulator-stop:
    @command -v adb >/dev/null 2>&1 || { echo >&2 "adb is required but not installed."; exit 1; }
    adb emu kill

# start local sync server
sync-up:
    @command -v docker >/dev/null 2>&1 || { echo >&2 "docker is required but not installed."; exit 1; }
    docker compose up -d

# stop local sync server
sync-down:
    @command -v docker >/dev/null 2>&1 || { echo >&2 "docker is required but not installed."; exit 1; }
    docker compose down -v

# run a taskwarrior sync from the arch container
tw-sync:
    @command -v docker >/dev/null 2>&1 || { echo >&2 "docker is required but not installed."; exit 1; }
    docker compose exec taskwarrior-client task sync

# increment version code and set version name
version-bump name="":
    @if [ -z "{{ name }}" ]; then \
        echo "Error: version name is required. Usage: just version-bump 1.0.1"; \
        exit 1; \
    fi; \
    CODE=$(grep 'VERSION_CODE=' android/version.properties | cut -d'=' -f2); \
    NEXT_CODE=$((CODE + 1)); \
    echo "Bumping version to {{ name }} ($NEXT_CODE)..."; \
    echo "VERSION_NAME={{ name }}" > android/version.properties; \
    echo "VERSION_CODE=$NEXT_CODE" >> android/version.properties;

# display the current version
version-current:
    @cat android/version.properties
