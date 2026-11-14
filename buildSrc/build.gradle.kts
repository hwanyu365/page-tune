plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// 테스트가 app/build.gradle 을 직접 읽는다. 그 파일은 태스크 입력이 아니라 그것만 바뀌면 테스트가 up-to-date 로 건너뛰어진다.
tasks.test {
    outputs.upToDateWhen { false }
}
