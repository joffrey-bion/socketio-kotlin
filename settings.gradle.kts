plugins {
    id("com.gradle.develocity") version "4.2.1"
}

rootProject.name = "socketio-kotlin"

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        uploadInBackground = false // background upload is bad for CI, and not critical for local runs
    }
}
