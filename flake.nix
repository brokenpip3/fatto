{
  description = "Fatto (TaskWarrior Android Client)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils?ref=11707dc2f618dd54ca8739b309ec4fc024de578b";
    rust-overlay = {
      url = "github:oxalica/rust-overlay?ref=4d6fee71fea68418a48992409b47f1183d0dd111";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      rust-overlay,
      ...
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        overlays = [ (import rust-overlay) ];
        pkgs = import nixpkgs {
          inherit system overlays;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        androidCompositionMinimal = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
        };

        androidCompositionBase = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
          includeNDK = true;
          ndkVersions = [ "26.1.10909125" ];
        };

        androidCompositionFull = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
          abiVersions = [ "x86_64" ];
          includeSystemImages = true;
          includeNDK = true;
          ndkVersions = [ "26.1.10909125" ];
          includeEmulator = true;
          systemImageTypes = [ "google_apis_playstore" ];
        };

        androidSdkMinimal = androidCompositionMinimal.androidsdk;
        androidSdkBase = androidCompositionBase.androidsdk;
        androidSdkFull = androidCompositionFull.androidsdk;

        rustToolchain = pkgs.rust-bin.stable.latest.minimal.override {
          extensions = [
            "rust-src"
            "rust-analyzer"
            "clippy"
            "rustfmt"
          ];
          targets = [
            "aarch64-linux-android"
            "x86_64-linux-android"
          ];
        };

      in
      let
        mkFattoShell =
          {
            name,
            sdk ? null,
            includeRust ? true,
            includeJdk ? true,
            includeSdk ? true,
            hasEmulator ? false,
            withGui ? false,
            useAapt2Override ? true,
            extraInputs ? [ ],
          }:
          pkgs.mkShell (
            {
              inherit name;
              nativeBuildInputs =
                with pkgs;
                [
                  just
                  gnumake
                  cmake
                  pkg-config
                ]
                ++ pkgs.lib.optional includeRust rustToolchain
                ++ pkgs.lib.optional includeRust pkgs.cargo-ndk
                ++ pkgs.lib.optional includeJdk jdk17
                ++ pkgs.lib.optional includeSdk sdk
                ++ extraInputs;

              buildInputs = pkgs.lib.optionals withGui (
                with pkgs;
                [
                  libGL
                  libpulseaudio
                  stdenv.cc.cc.lib
                  vulkan-loader
                  libX11
                  libXext
                  libXcursor
                  libXi
                  libXrender
                  libXtst
                ]
              );

              LD_LIBRARY_PATH = pkgs.lib.optionalString withGui (
                pkgs.lib.makeLibraryPath (
                  with pkgs;
                  [
                    libGL
                    libpulseaudio
                    stdenv.cc.cc.lib
                    vulkan-loader
                    libX11
                    libXext
                    libXcursor
                    libXi
                    libXrender
                    libXtst
                  ]
                )
              );

              shellHook = ''
                 export PATH="${pkgs.lib.optionalString includeJdk "$JAVA_HOME/bin:"}${pkgs.lib.optionalString includeSdk "$ANDROID_HOME/platform-tools:${pkgs.lib.optionalString hasEmulator "$ANDROID_HOME/emulator:"}$ANDROID_HOME/build-tools/34.0.0:"}$PATH"
                 export GRADLE_USER_HOME="$(git rev-parse --show-toplevel)/.gradle-home"
                 mkdir -p "$GRADLE_USER_HOME"
                  ${pkgs.lib.optionalString (includeSdk && useAapt2Override) ''
                  echo "android.aapt2FromMavenOverride=${sdk}/libexec/android-sdk/build-tools/34.0.0/aapt2" > "$GRADLE_USER_HOME/gradle.properties"
                ''}
              '';
            }
            // pkgs.lib.optionalAttrs includeJdk { JAVA_HOME = pkgs.jdk17.home; }
            // pkgs.lib.optionalAttrs includeSdk {
              ANDROID_HOME = "${sdk}/libexec/android-sdk";
              ANDROID_SDK_ROOT = "${sdk}/libexec/android-sdk";
              ANDROID_NDK_ROOT = "${sdk}/libexec/android-sdk/ndk/26.1.10909125";
              ANDROID_NDK_HOME = "${sdk}/libexec/android-sdk/ndk/26.1.10909125";
              NDK_HOME = "${sdk}/libexec/android-sdk/ndk/26.1.10909125";
            }
          );
      in
      {
        devShells.default = mkFattoShell {
          name = "fatto-dev";
          sdk = androidSdkFull;
          hasEmulator = true;
          withGui = true;
          extraInputs = [ pkgs.gemini-cli ];
        };

        devShells.fatto-ci = mkFattoShell {
          name = "fatto-ci";
          sdk = androidSdkBase;
        };

        devShells.fatto-ci-rust = mkFattoShell {
          name = "fatto-ci-rust";
          includeSdk = false;
          includeJdk = false;
        };

        devShells.fatto-ci-kotlin = mkFattoShell {
          name = "fatto-ci-kotlin";
          sdk = androidSdkMinimal;
          includeRust = false;
          useAapt2Override = false;
        };

        devShells.fatto-ci-android = mkFattoShell {
          name = "fatto-ci-android";
          sdk = androidSdkFull;
          hasEmulator = true;
        };
      }
    );
}
