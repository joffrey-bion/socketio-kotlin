plugins {
    id("com.gradle.enterprise") version "3.15.1"
}

rootProject.name = "socketio-kotlin"

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
    }
}
