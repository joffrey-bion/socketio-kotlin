plugins {
    id("com.gradle.enterprise") version "3.17.3"
}

rootProject.name = "socketio-kotlin"

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
    }
}
