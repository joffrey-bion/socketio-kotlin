plugins {
    id("com.gradle.enterprise") version "3.16.2"
}

rootProject.name = "socketio-kotlin"

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
    }
}
