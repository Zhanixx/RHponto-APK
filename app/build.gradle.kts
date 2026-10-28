plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Cada build do GitHub ganha um número maior, para o atualizador do app reconhecer a versão nova.
val numeroBuild = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()

android {
    namespace = "com.pontoalmoco.rhid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pontoalmoco.rhid"
        minSdk = 26
        targetSdk = 35
        versionCode = 100 + numeroBuild
        versionName = "2.2.$numeroBuild"
    }

    // Chave fixa: todas as versões saem com a mesma assinatura, então uma instala por cima da outra.
    signingConfigs {
        create("fixa") {
            storeFile = file("ponto.jks")
            storePassword = "ErIM7yffKRG8x_t2_sLXV3_Q"
            keyAlias = "ponto"
            keyPassword = "ErIM7yffKRG8x_t2_sLXV3_Q"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixa") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
