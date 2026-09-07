plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("cargoNdkBuild") {
    group = "rust"
    description = "Compiles the audiophile-core Rust library for all Android target ABIs using cargo-ndk"
    doLast {
        val targetAbis = mapOf(
            "arm64-v8a" to "aarch64-linux-android",
            "armeabi-v7a" to "armv7-linux-androideabi",
            "x86_64" to "x86_64-linux-android"
        )
        
        for ((abi, targetTriple) in targetAbis) {
            println("Building Rust native library for $abi ($targetTriple)...")
            ProcessBuilder(
                "cargo", "ndk",
                "-t", abi,
                "-o", "../app/src/main/jniLibs",
                "build", "--release"
            )
                .directory(file("core-rust"))
                .inheritIO()
                .start()
                .waitFor()
        }
    }
}
