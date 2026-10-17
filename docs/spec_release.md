# page-tune — Play 스토어 배포

## 1. 개요

- 문서명: spec_release
- 작성자: dohwan.ahn
- 작성일 / 최종 수정일: 2026-10-18 / 2026-10-18
- 기준 커밋 ID: `c37391d`

### 목적

- 현재 빌드는 Play 스토어에 제출할 수 없다.
  - targetSdk 28 → Play 는 2026-08-31 이후 신규 앱·업데이트에 API 36 이상을 요구한다.
  - AGP 3.4.1 / Kotlin 1.3.31 / Gradle 5.1.1 → compileSdk 36 을 빌드할 수 없다.
  - `jcenter()` 는 종료된 저장소 → 클린 환경(CI)에서 의존성 해석이 보장되지 않는다.
  - release 서명 설정 없음, `versionCode` 고정 → 업로드·갱신이 불가능하다.
- 릴리스를 수동으로 올리면 서명 키·버전 관리가 개발자 PC 에 묶인다 → 태그 push 로 internal 트랙까지 자동 배포한다.

## 2. 목표 및 범위

### In Scope

- 툴체인을 targetSdk/compileSdk 36 을 빌드할 수 있는 버전으로 올린다.
- `kotlin-android-extensions`(synthetic) 를 ViewBinding 으로 바꾼다.
- API 35+ 의 edge-to-edge 강제에 맞춰 insets 를 처리한다.
- release 서명을 환경변수/Gradle property 로 주입한다.
- 태그에서 `versionName`/`versionCode` 를 만든다.
- GitHub Actions 로 태그 push → 서명된 AAB → Play internal 트랙 업로드.

### Out of Scope

- Play Console 작업: 앱 생성, 첫 AAB 수동 업로드, 서비스 계정 권한, 개인정보처리방침, Data safety, 콘텐츠 등급, 스토어 등록정보 → 개발자가 직접 수행한다.
- production 트랙 자동 승격 → Console 에서 수동으로 승격한다.
- 스토어 메타데이터(스크린샷·설명)의 저장소 관리.
- 앱 기능 변경.

## 3. 요구사항

### 사용자 스토리

- **US#1** 개발자로서 버전 태그를 push 하는 것만으로 internal 트랙에 새 빌드가 올라가기를 원한다. 서명 키를 PC 에 두지 않고 릴리스하기 위해서다.

### 기능 요구사항

- **FR#1** `compileSdk`/`targetSdk` 는 36 이다. `minSdk` 는 24 를 유지한다. `[요구사항]`
- **FR#2** release 빌드는 `bundleRelease` 로 서명된 AAB 를 만든다. 서명 정보는 Gradle property 또는 환경변수에서 읽는다. `[요구사항]`
- **FR#3** 서명 정보가 없으면 release 빌드는 빠진 키 이름을 알려주며 실패한다. debug 빌드와 unit test 는 서명 정보 없이 동작한다. `[요구사항]`
- **FR#4** `versionName`/`versionCode` 는 태그 `vMAJOR.MINOR.PATCH` 에서 만든다. `versionCode = MAJOR*10000 + MINOR*100 + PATCH` 이다. `[요구사항]`
- **FR#5** 태그가 없는 로컬 빌드는 `versionName "0.0.0-dev"`, `versionCode 1` 로 빌드된다. `[요구사항]`
- **FR#6** `v*` 태그 push 시 workflow 가 unit test → `bundleRelease` → internal 트랙 업로드 순으로 실행한다. 앞 단계가 실패하면 업로드하지 않는다. `[요구사항]`
- **FR#7** 기존 화면은 API 36 기기에서 시스템 바에 가려지지 않는다. `[요구사항]`
  - 목록·편집 화면 → 시스템 바·노치·키보드 영역을 피한다.
  - 웹페이지 보기 화면 → 상태바를 숨기고 노치까지 쓰는 전체화면이므로 내비게이션 바만 피한다.
  - 시스템 바 아이콘은 밝은 색으로 고정한다 → 앱 배경이 항상 어두워서, 시스템 라이트 모드 기본값(어두운 아이콘)이면 배경에 묻힌다.

### 비기능 요구사항

- **NFR#1** 기존 사용자 데이터(Room DB v3)는 업그레이드 후에도 보존된다. 스키마는 변경하지 않는다. `[요구사항]`
- **NFR#2** keystore·비밀번호·서비스 계정 키는 저장소에 커밋하지 않는다. CI 는 GitHub Secrets 에서만 읽는다. `[요구사항]`
- **NFR#3** `usesCleartextTraffic="true"` 는 유지한다. 사용자가 임의의 http 사이트를 열 수 있어야 하기 때문이다. `[as-built]`
- **NFR#4** 의존성은 `google()` / `mavenCentral()` 에서만 가져온다. `[요구사항]`

## 4. 데이터 및 API 스펙

### 빌드 입력

| 키 (Gradle property / 환경변수) | 용도 | 필수 |
| --- | --- | --- |
| `RELEASE_STORE_FILE` | keystore 경로 | release |
| `RELEASE_STORE_PASSWORD` | keystore 비밀번호 | release |
| `RELEASE_KEY_ALIAS` | key alias | release |
| `RELEASE_KEY_PASSWORD` | key 비밀번호 | release |
| `VERSION_TAG` | `vX.Y.Z`, 없으면 FR#5 | 선택 |

### GitHub Secrets

| Secret | 내용 |
| --- | --- |
| `KEYSTORE_BASE64` | 업로드 keystore 의 base64 |
| `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | 위 서명 입력 |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Developer API 서비스 계정 키 |

## 6. 동작 로직 및 정책

### 버전 산출 [FR#4][FR#5]

- 태그 형식이 `v\d+\.\d+\.\d+` 가 아니면 빌드를 실패시킨다 → 잘못된 태그로 versionCode 가 역행하는 업로드를 막는다.
- MINOR·PATCH 는 0~99 → 넘으면 빌드 실패 (자릿수 충돌 방지).

### 서명 누락 처리 [FR#3]

- AGP 는 서명 값이 비어 있으면 패키징 단계에서 메시지 없는 NPE 로 실패한다 → 원인을 알 수 없다.
- 서명이 필요한 태스크(AAB 서명, APK 패키징) 직전에 서명 키 4개를 검사하고, 빠진 키 이름을 담아 실패시킨다.
- 설정 단계에서 검사하지 않는다 → debug 빌드·테스트까지 서명 정보를 요구하게 된다.

### 설계 결정

| 항목 | 선택 | 기각 | 근거 |
| --- | --- | --- | --- |
| 업로드 도구 | Gradle Play Publisher | fastlane, upload-google-play Action | Gradle 외 런타임(Ruby)을 들이지 않는다. 빌드와 업로드가 한 도구에 있다 |
| versionCode | 태그에서 산출 | CI run number | 로컬에서도 같은 태그면 같은 값이 나온다. workflow 재실행·저장소 이전에 영향받지 않는다 |
| synthetic 대체 | ViewBinding | `findViewById` | 타입·null 안전성을 synthetic 과 같은 수준으로 유지한다 |
| Room 코드 생성 | kapt 유지 | KSP 전환 | 이번 범위는 배포 가능화다. 생성 방식 변경은 NFR#1 위험만 늘린다 |
| 자동 배포 트랙 | internal | production | 사람 확인 없이 사용자에게 나가는 경로를 만들지 않는다 |
| AGP 라인 | 8.x 최신 (8.13) | 9.x | 9.x 는 built-in Kotlin 전환과 kapt 대체를 함께 요구한다. 8.13 도 compileSdk 36 을 지원하므로 배포 가능화에 필요 없는 마이그레이션을 피한다 |
| Play 업로드 인증 | `ANDROID_PUBLISHER_CREDENTIALS` 환경변수 | 키 파일 경로 지정 | 서비스 계정 키를 디스크·저장소에 두지 않는다 [NFR#2] |

- GPP 4.x 는 AGP 9 이상만 지원한다 → AGP 를 9 로 올릴 때 GPP 도 4.x 로 함께 올려야 한다.
- Gradle 데몬 힙은 4GB 로 지정한다 → 기본 힙에서는 release lint(lintVital) 가 OutOfMemoryError 로 실패한다.

## 7. 테스트 및 검증 기준

| TC | 대상 | 시나리오 | 비고 |
| --- | --- | --- | --- |
| TC#1 | FR#4 | `v1.2.3` → versionName `1.2.3`, versionCode `10203` | unit |
| TC#2 | FR#4 | `1.2.3`, `v1.2`, `v1.100.0` → 실패 | unit |
| TC#3 | FR#5 | 태그 없음 → `0.0.0-dev` / `1` | unit |
| TC#4 | FR#1, NFR#4 | 빌드 산출물을 지운 상태에서 `./gradlew testDebugUnitTest assembleDebug` 성공 | 로컬. CI 는 태그 push 시 같은 단계를 거친다 |
| TC#5 | FR#2 | 서명 정보를 주고 `bundleRelease` → `jarsigner -verify` 통과 | 로컬 |
| TC#6 | FR#3 | 서명 정보 없이 `bundleRelease`/`assembleRelease` → 빠진 키 이름과 함께 실패, `assembleDebug` 는 성공 | 로컬 |
| TC#7 | FR#7 | API 36 에뮬레이터에서 전 화면 진입 → 상태바·내비게이션바에 툴바·버튼이 가려지지 않음 | 수동 |
| TC#8 | NFR#1 | 기존 빌드로 Tune·페이지 저장 → 새 빌드로 덮어 설치 → 데이터 유지 | 수동, 에뮬레이터 |
| TC#9 | 기존 기능 | 에뮬레이터에서 Tune 생성·요소 hide·색상 변경·스크롤 복원·복제 동작 | 수동 |
| TC#10 | FR#6 | 태그 push → internal 트랙에 해당 versionCode 등록 | Play Console 준비 후. 개발자 확인 |
| TC#11 | NFR#2 | 저장소에 `*.jks`/`*.keystore`/서비스 계정 JSON 이 추적되지 않음 | `git ls-files` |
