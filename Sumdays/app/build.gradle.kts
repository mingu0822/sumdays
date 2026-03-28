plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.sumdays"
    compileSdk = 36
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        unitTests.all {
            it.jvmArgs("-noverify", "-Xmx2048m")
        }
    }
    defaultConfig {
        applicationId = "com.example.first"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation("androidx.work:work-testing:2.9.0")
    implementation(libs.androidx.runner)
    implementation(libs.androidx.junit.ktx)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("io.mockk:mockk-agent:1.13.11")  // final 클래스 목킹용
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3") // 코루틴 테스트용
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("com.google.code.gson:gson:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("com.google.android.material:material:1.9.0")
    implementation(libs.androidx.compose.ui.text)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.security:security-crypto:1.0.0")
    // Retrofit: 네트워크 통신 라이브러리
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson Converter: JSON을 Kotlin 데이터 클래스로 변환
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // OkHttp Logging Interceptor (선택사항): 통신 로그를 확인하여 디버깅에 유용
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    // SDK 26 미만에서 java time과 같은 날짜 및 시간 클래스를 사용할 수 있도록 해줌
    implementation("com.jakewharton.threetenabp:threetenabp:1.1.1")
    // 코루틴 사용할 수 있게 해줌
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Room 관련 의존성 추가
    val roomVersion = "2.6.1" // Room의 최신 안정화 버전으로 교체
    // Room 라이브러리
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    // Kotlin용 Kapt 어노테이션 프로세서
    kapt("androidx.room:room-compiler:$roomVersion")
    // 선택 사항: 코루틴 지원
    implementation("androidx.room:room-ktx:$roomVersion")
    // ViewModel 확장 함수 by viewModels() 사용을 위한 의존성
    implementation("androidx.activity:activity-ktx:1.8.0")
    // LiveData와 Flow를 LiveData로 변환하는 asLiveData()를 위한 의존성
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    // ⭐ MPAndroidChart 의존성 추가
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // ⭐ 1. Mockito 코루틴 지원 라이브러리 (필수)
    // Kotlin에서 Mockito를 더 쉽게 사용하기 위한 확장 라이브러리
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1") // 최신 버전 사용 권장

    // ⭐ 2. 코루틴 테스트 유틸리티 (필수)
    // Dispatchers.setMain, runTest, TestCoroutineScheduler 등을 사용하기 위함
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1") // Coroutine 버전과 일치

    // ⭐ 3. AndroidX 아키텍처 컴포넌트 테스트 (LiveData 테스트를 위해 필수)
    // InstantTaskExecutorRule과 같은 유틸리티 포함
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.hamcrest:hamcrest-library:2.2")
    testImplementation("androidx.work:work-testing:2.9.0")
    testImplementation("com.google.truth:truth:1.1.5")
    // assertThat 사용을 위해 필요
    testImplementation("androidx.work:work-testing:2.9.0")
}

configurations.all {
    exclude(group = "org.junit.jupiter")
    exclude(group = "org.junit.platform")
}
