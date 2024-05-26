plugins {
    id("com.gradle.develocity") version "3.17.4"
}

rootProject.name = "socketio-kotlin"

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        uploadInBackground = false // background upload is bad for CI, and not critical for local runs
    }
}
