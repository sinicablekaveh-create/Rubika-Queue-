plugins { id("com.android.application") }

android {
    namespace = "com.sinicable.remotesender"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sinicable.remotesender"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
