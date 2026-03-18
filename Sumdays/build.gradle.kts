// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false

    // ktlint 플러그인 선언만 (하위 모듈에서 apply)
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

// 모든 하위 모듈(app 등)에 ktlint 적용 + 공통 설정
// root gradle
subprojects {
    plugins.apply("org.jlleitschuh.gradle.ktlint")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension>("ktlint") {
        android.set(true)
        ignoreFailures.set(false)

        // 보고서 포맷
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }

        // 검사 대상/제외 경로 (수정 필요)
        filter {
            include("**/src/main/kotlin/**/*.kt") // src/main/kotlin 경로의 kt 파일 포함
            include("**/src/main/java/**/*.kt") // src/main/java 경로의 kt 파일 포함
            exclude("**/build/**") // 빌드 경로는 항상 제외
            exclude("**/src/test/**") // 테스트 코드는 제외 (선택 사항)
            exclude("**/src/androidTest/**") // 안드로이드 테스트 코드는 제외 (선택 사항)
        }
    }
}
