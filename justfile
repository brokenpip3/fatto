tw_cmd := "docker compose exec taskwarrior-client task"

# clean jni libraries
build-clean-jni:
    rm -rf android/app/src/main/jniLibs
    mkdir -p android/app/src/main/jniLibs

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
    rm -rf android/app/src/main/uniffi && mkdir -p android/app/src/main/uniffi
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
        echo "Warning: FATTO_KEYSTORE_BASE64 not set. Building unsigned release."; \
        cd android && ./gradlew assembleRelease; \
    else \
        echo "Decoding release keystore..."; \
        echo "${FATTO_KEYSTORE_BASE64:-}" | base64 -d > android/app/release.jks; \
        export SOURCE_DATE_EPOCH=$(git log -1 --format=%ct); \
        export FATTO_KEYSTORE_PATH=release.jks; \
        export FATTO_KEYSTORE_PASSWORD="${FATTO_KEYSTORE_PASSWORD:-}"; \
        export FATTO_KEY_ALIAS="${FATTO_KEY_ALIAS:-}"; \
        trap 'rm -f android/app/release.jks' EXIT; \
        cd android && ./gradlew assembleRelease; \
    fi
    @mkdir -p dist
    @VERSION=$(grep 'VERSION_NAME=' android/version.properties | cut -d'=' -f2); \
    for apk in android/app/build/outputs/apk/release/*.apk; do \
        if [ ! -f "$apk" ]; then continue; fi; \
        base=$(basename "$apk"); \
        new_name=$(echo "$base" | sed -E "s/^app(-)?(.*)-release(-unsigned)?\.apk$/fatto-v$VERSION\1\2\3.apk/"); \
        cp "$apk" "dist/$new_name"; \
        echo "Release APK created at: dist/$new_name"; \
    done

# build and bundle release aab/apk
build-release: build-rust-all build-bindings build-release-apk

# build signed beta release
build-beta: build-rust-all build-bindings build-beta-apk

# build signed beta apk (assumes rust libs and bindings are already built)
build-beta-apk:
    @if [ -z "${FATTO_KEYSTORE_BASE64:-}" ]; then \
        echo "Warning: FATTO_KEYSTORE_BASE64 not set. Building unsigned beta."; \
        cd android && ./gradlew assembleBeta; \
    else \
        echo "Decoding beta keystore..."; \
        echo "${FATTO_KEYSTORE_BASE64:-}" | base64 -d > android/app/release.jks; \
        export SOURCE_DATE_EPOCH=$(git log -1 --format=%ct); \
        export FATTO_KEYSTORE_PATH=release.jks; \
        export FATTO_KEYSTORE_PASSWORD="${FATTO_KEYSTORE_PASSWORD:-}"; \
        export FATTO_KEY_ALIAS="${FATTO_KEY_ALIAS:-}"; \
        trap 'rm -f android/app/release.jks' EXIT; \
        cd android && ./gradlew assembleBeta; \
    fi
    @mkdir -p dist
    @VERSION=$(grep 'VERSION_NAME=' android/version.properties | cut -d'=' -f2); \
    for apk in android/app/build/outputs/apk/beta/*.apk; do \
        if [ ! -f "$apk" ]; then continue; fi; \
        base=$(basename "$apk"); \
        new_name=$(echo "$base" | sed -E "s/^app(-)?(.*)-beta(-unsigned)?\.apk$/fatto-v$VERSION\1\2-beta\3.apk/"); \
        cp "$apk" "dist/$new_name"; \
        echo "Beta APK created at: dist/$new_name"; \
    done

# build rust libs and uniffi bindings via nix (reproducible)
build-nix-libs:
    nix build .#taskchampion-android
    rm -rf android/app/src/main/jniLibs android/app/src/main/uniffi
    ln -sf $(pwd)/result/jniLibs android/app/src/main/jniLibs
    ln -sf $(pwd)/result/uniffi android/app/src/main/uniffi

# reproducible release build using nix for rust layer
build-release-repro: build-nix-libs build-release-apk

# run fast unit tests (Rust unit + Kotlin unit)
test-fast: test-rust test-kotlin

# run every test in the project (requires emulator and sync server)
test-all: sync-up run-emulator-start test-fast test-android test-integration run-emulator-stop sync-down

# run android tests in CI (headless)
test-ci-android: sync-up
    @echo "Creating AVD..."
    echo "no" | avdmanager create avd -n ci_emulator -k "system-images;android-36;google_apis_playstore;x86_64" --force
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

# run rust integration tests against local tss and minio
test-integration:
    cd rust/taskchampion-android && \
    TASKCHAMPION_SYNC_URL=http://localhost:8080 \
    TASKCHAMPION_CLIENT_ID=768d9f09-accd-406d-8685-7b977b83d5c6 \
    TASKCHAMPION_SYNC_SECRET=foobar \
    TASKCHAMPION_S3_ENDPOINT=http://localhost:9000 \
    TASKCHAMPION_S3_BUCKET=fatto-tasks \
    TASKCHAMPION_S3_ACCESS_KEY_ID=minioadmin \
    TASKCHAMPION_S3_SECRET_ACCESS_KEY=minioadmin \
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
    adb install -r android/app/build/outputs/apk/debug/app-x86_64-debug.apk

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

# start android emulator with fresh data
run-emulator-start-fresh:
    @command -v emulator >/dev/null 2>&1 || { echo >&2 "emulator is required but not installed."; exit 1; }
    @if adb devices | grep -q emulator; then \
        echo "stopping running emulator first..."; \
        adb emu kill; \
        sleep 2; \
    fi
    export ANDROID_AVD_HOME="$HOME/.config/.android/avd" && \
    (emulator -avd dev_emulator -no-audio -no-boot-anim -no-snapshot-load -wipe-data &) && \
    adb wait-for-device

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

# generate F-Droid changelog since last tag
version-changelog:
    @VERSION_CODE=$(grep 'VERSION_CODE=' android/version.properties | cut -d'=' -f2); \
    PREV_TAG=$(git tag --sort=-creatordate | head -1); \
    echo "Generating changelog for version code $VERSION_CODE (since $PREV_TAG)..."; \
    git cliff "$PREV_TAG..HEAD" > "fastlane/metadata/android/en-US/changelogs/$VERSION_CODE"; \
    echo "Written to fastlane/metadata/android/en-US/changelogs/$VERSION_CODE"
