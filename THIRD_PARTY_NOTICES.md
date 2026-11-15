# Dependencies

- DexKit 2.2.0 — https://github.com/LuckyPray/DexKit ; LGPL-3.0 / Apache-2.0, as declared by its published Maven POM. Resolved by Gradle from Maven Central.
- Xposed API 82 — https://api.xposed.info/ ; compile-only framework API, not bundled into generated APK code.
- Gradle Wrapper 8.1.1 — https://gradle.org/ ; Apache-2.0.
- FlatBuffers Java 23.5.26 — https://github.com/google/flatbuffers ; Apache-2.0.
- Kotlin stdlib 1.8.21 — https://github.com/JetBrains/kotlin ; Apache-2.0.
- Rikka cxx 1.2.0 — https://github.com/RikkaApps/ndk-third-party ; consult upstream licensing for its packaged C++ runtime.

DexKit's published dependency metadata was independently read from https://repo.maven.apache.org/maven2/org/luckypray/dexkit/2.2.0/dexkit-2.2.0.pom . Android library dependencies are resolved from upstream repositories rather than redistributed as local AAR/JAR files.
