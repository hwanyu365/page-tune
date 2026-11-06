# page-tune — 앱 아이콘·개인정보 처리방침·스토어 문구

## 1. 개요

| 항목 | 값 |
| --- | --- |
| 문서명 | spec_store |
| 작성자 | dohwan.ahn |
| 작성일 / 최종 수정일 | 2026-11-07 / 2026-11-07 |
| 기준 커밋 ID | `89c0ddb` |

### 목적

- Play Console 등록에 필요한 자산 중 저장소에 둘 수 있는 것을 만든다.
  - 앱 아이콘: 현재는 1024px PNG 한 장이라 적응형 아이콘·테마 아이콘을 지원하지 못한다.
  - 개인정보 처리방침: Play 는 URL 을 요구한다 → GitHub Pages 로 호스팅한다.
  - 스토어 문구: 포지셔닝(공통 제약)을 지키는 초안을 저장소에서 리뷰한다.

## 2. 목표 및 범위

### In Scope

- 벡터 적응형 아이콘(foreground·background·monochrome)과 API 24~25 용 PNG 대체.
- `docs/privacy.md` (한국어·영어).
- `app/src/main/play/listings/{en-US,ko-KR}/` 의 제목·짧은 설명·전체 설명.

### Out of Scope

- 스토어 그래픽(feature graphic·스크린샷·512px 아이콘) → 제작 방법을 정한 뒤 별도로 다룬다.
- GitHub Pages 활성화, Play Console 의 Data safety·콘텐츠 등급 입력 → 저장소 소유자가 수행한다.
- listing 업로드(`publishListing`) → 저장소 소유자가 내용을 승인한 뒤 수동으로 실행한다.
- 앱 기능 변경.

## 3. 요구사항

### 사용자 스토리

- **US#1** 사용자로서 런처에서 앱을 알아보고, 시스템 테마 아이콘에서도 어울리기를 원한다.
- **US#2** 사용자로서 앱이 내 정보를 어떻게 다루는지 스토어에서 링크로 확인하기를 원한다.
- **US#3** 저장소 소유자로서 스토어 문구를 PR 로 리뷰하고 버전 관리하기를 원한다.

### 기능 요구사항

- **FR#1** 앱 아이콘은 적응형 아이콘이다. background 는 아이콘 전용 색 리소스 `ic_launcher_background`(`#171717`), foreground 는 브랜드 색 `#ff4103` 의 벡터이고, `monochrome` 레이어를 가진다. `[요구사항]`
  - foreground·monochrome 의 획은 끝(선 굵기 반 포함)까지 108dp 캔버스 중심 (54,54) 에서 반지름 33dp 이내다 → 어떤 마스크 모양에서도 잘리지 않는다.
  - 배경을 앱 테마색(`colorBackground`)과 분리한다 → 테마색이 바뀌어도 아이콘과 API 24~25 PNG 가 어긋나지 않는다.
- **FR#2** API 24~25 는 적응형 아이콘을 쓰지 못하므로 `mipmap-*dpi` 의 `ic_launcher.png`·`ic_launcher_round.png` 를 쓴다. 같은 도형이다. `[요구사항]`
- **FR#3** 매니페스트의 `icon`·`roundIcon` 은 mipmap 리소스를 가리킨다. 기존 `drawable/ic_launcher.png` 는 쓰는 곳이 없으므로 제거한다. `[요구사항]`
- **FR#4** `docs/privacy.md` 는 한국어·영어 본문을 함께 가지며 아래를 담는다. `[요구사항]`
  - 개발자가 수집·전송하는 데이터가 없다.
  - 저장되는 데이터(웹페이지 제목·주소, Tune)는 기기 안에만 있다.
  - 인터넷 권한은 사용자가 연 웹페이지를 불러오는 데만 쓴다.
  - 백업·공유·삭제 방법.
  - 문의처는 GitHub 이슈 URL 이다. 개인 이메일을 적지 않는다.
  - 시행일은 한국어·영어가 같고, Pages 게시일에 맞춰 갱신한다 → 게시 전 값은 예정일이다 (#38 에서 게시 확인 시 확정).
- **FR#5** 개인정보 처리방침과 스토어 문구의 서술은 코드·매니페스트의 실제 권한·데이터 흐름과 일치한다. 권한·저장·전송 경로가 바뀌는 변경은 이 문서를 함께 고친다. `[요구사항]`
- **FR#6** listing 은 GPP 3.x 디렉터리 형식(`app/src/main/play/listings/{en-US,ko-KR}/`)에 `title.txt`(30자 이하)·`short-description.txt`(80자 이하)·`full-description.txt`(4000자 이하)를 둔다. `[요구사항]`
- **FR#7** listing 문구는 "맞춤 보기 도구"로 쓴다. 광고 차단을 주 문구로 쓰지 않고, 특정 사이트·커뮤니티 이름을 넣지 않는다. 동작은 앱 안에서 연 페이지에만 적용됨을 밝힌다. `[요구사항]`
- **FR#8** 태그 배포 workflow 는 listing 을 게시하지 않는다. `[요구사항]`

### 비기능 요구사항

- **NFR#1** 의존성을 추가하지 않는다. `[요구사항]`
- **NFR#2** 아이콘 정의에 비밀값·외부 에셋이 없다. 도형은 벡터 path 하나다. `[요구사항]`

## 4. 데이터 및 API 스펙

### 개인정보 처리방침 근거

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 권한 | `INTERNET` 하나 | 매니페스트 |
| 저장 | Room DB `web_plugin_database` — 웹페이지(제목·주소·연결된 Tune), Tune(이름·테스트 주소), 규칙 | `db/` |
| 웹 데이터 | WebView 의 쿠키·캐시·DOM 저장소는 Android WebView 가 기기에 보관한다 | `util/WebViewConfig.kt` |
| 외부 전송 | 사용자가 연 페이지의 요청 외 없음. 분석·광고·크래시 SDK 가 의존성에 없다 | `app/build.gradle` dependencies |
| 공유 | 빠른 메뉴의 주소 공유는 시스템 공유 시트로 사용자가 고른 앱에만 주소를 넘긴다 | `WebBrowserActivity` |
| 백업 | `allowBackup="true"` 이고 `dataExtractionRules`·`fullBackupContent` 가 없다 → 기본 규칙에 따라 Android 자동 백업이 앱 데이터 디렉터리(Room DB, WebView 쿠키·사이트 저장소 포함, 캐시 제외)를 사용자 Google 계정에 보관할 수 있다. 개발자는 접근하지 않는다 | 매니페스트 |

### 게시 경로

- GitHub Pages(main 브랜치 `/docs`)로 `https://hwanyu365.github.io/page-tune/privacy` 에 게시한다. 활성화는 저장소 소유자가 한다.

## 6. 동작 로직 및 정책

### 아이콘 [FR#1][FR#2]

- 도형은 시작 위치에서 솟아 오른 선이 꼭짓점(A 의 윗부분)을 지나 물결로 이어지는 하나의 획이다 → 기존 아이콘의 A 와 파형 모티프를 단순화한 것이다.
- 획 끝과 꺾임은 둥글게 한다. 작은 크기에서도 선이 뭉개지지 않는 굵기(5.5/108)로 한다.
- monochrome 은 같은 path 를 단색으로 둔다. 색은 시스템이 입힌다.
- 레거시 PNG 는 같은 path 를 렌더한 결과다. 배경 위에 도형을 그리고 사각형(모서리 둥글게)·원 마스크를 적용한다.

### 설계 결정

| 항목 | 선택 | 기각 | 근거 |
| --- | --- | --- | --- |
| API 24~25 대체 | mipmap PNG | 벡터 drawable 을 `icon` 에 직접 지정 | 런처가 벡터 아이콘을 안정적으로 그리지 못한다 |
| listing 업로드 | 파일만 두고 수동 게시 | workflow 에 `publishListing` 추가 | 사람이 확인하지 않은 문구가 스토어에 나가는 경로를 만들지 않는다 (`spec_release.md` 의 internal 트랙 결정과 같은 이유) |
| 연락처 | GitHub 이슈 | 개인 이메일 | 저장소 정책상 이메일을 노출하지 않는다 |

## 7. 테스트 및 검증 기준

| TC | 대상 | 시나리오 | 비고 |
| --- | --- | --- | --- |
| `TC#1` | FR#6 | 모든 listing 파일이 존재하고 글자 수 제한 이하 | 단위 (`StoreListingTest`) |
| `TC#2` | FR#7 | listing 에 "ad block"·"광고 차단" 이 없다 | 단위 |
| `TC#3` | FR#4 | `docs/privacy.md` 에 한국어·영어 제목, GitHub 이슈 URL 이 있고 이메일 주소가 없다. 한국어·영어 시행일이 같다 | 단위 |
| `TC#4` | FR#1~FR#3 | `assembleDebug` 성공, 병합 매니페스트의 `icon`·`roundIcon` 이 mipmap | 로컬 |
| `TC#10` | FR#1 | 획 끝 포함 최대 반지름 ≤ 33dp (foreground·monochrome) | 단위 (`IconSafeZoneTest`) |
| `TC#11` | FR#1 | foreground 와 monochrome path 가 같다 | 단위 |
| `TC#12` | FR#1 | 적응형 background 가 `@color/ic_launcher_background`(`#171717`) | 단위 |
| `TC#5` | FR#1 | API 36 에뮬레이터 런처에서 원형·사각 마스크와 테마 아이콘(monochrome)에서 잘림 없음 | 수동. 소유자 시안 확인 후 merge |
| `TC#6` | FR#2 | API 24 에뮬레이터 런처에서 PNG 아이콘 표시 | 수동 |
| `TC#13` | FR#5 | 매니페스트 `uses-permission` 이 `INTERNET` 뿐, `allowBackup="true"`, 백업 제외 규칙 없음, 방침에 Google 계정 백업 서술이 있고 "기기 밖으로 전송되지 않아요"류 단정이 없다 | 단위 (`StoreListingTest`) |
| `TC#14` | FR#5, FR#7 | 전체 설명에 "기기에만 저장"류 표현이 없다 | 단위 |
| `TC#7` | FR#5 | 병합 매니페스트에 라이브러리가 더하는 권한이 있으면 방침에 필요한지 판단 | 로컬. TC#13 은 앱 매니페스트만 본다 |
| `TC#8` | FR#8 | `./gradlew publishReleaseBundle --dry-run` 에 `publishReleaseListing` 이 없다 | 로컬 |
| `TC#9` | §4 | Pages 활성화 후 게시 URL 이 열린다 | 소유자 설정 후 확인 |

## 부록. 변경 이력

- GH-23: 최초 작성
