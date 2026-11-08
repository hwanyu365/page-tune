# page-tune — 디자인 시스템 (Material 3 테마·색 토큰·타이포·간격·메인 탭)

## 1. 개요

| 항목 | 값 |
| --- | --- |
| 문서명 | spec_design |
| 작성자 | dohwan.ahn |
| 작성일 / 최종 수정일 | 2026-11-08 / 2026-11-08 |
| 버전 | 0.1 |
| 기준 커밋 ID | `2bdb848` |

- **기능명:** 디자인 시스템 — 모든 화면이 공유하는 테마·색 토큰·글자 스케일·간격·컴포넌트와 메인 화면 탭 구조.
- **목적 (Why):**
  - 같은 역할의 요소가 화면마다 다른 위젯 계열로 그려진다 → 다이얼로그는 플랫폼 `android.app.AlertDialog` 와 AppCompat `AlertDialog` 가 섞여 있고, 카드는 `CardView`, 툴바는 AppCompat `Toolbar` 다 [as-built]
  - 색 리소스가 역할이 아니라 값으로 이름 붙어 있다(`colorAccentLight`, `colorGrayLight`, `colorWhite`) → 새 화면이 어떤 색을 써야 하는지 정할 근거가 없다 [as-built]
  - 글자 크기가 레이아웃·코드마다 11~16sp 로 직접 지정돼 있다 → 규칙 목록 보조 글자는 11sp 다 [as-built]
  - 열람 화면 오버레이(빠른 메뉴 툴바·Tune 상태)는 50% 검정 배경 위 흰 글자다 → 흰 페이지 위 합성 배경 `#808080` 에서 대비 3.95:1 로 4.5:1 에 못 미친다 [as-built]
  - 메인 탭이 deprecated `ViewPager`·`FragmentPagerAdapter` 를 쓴다 [as-built]

## 2. 목표 및 범위

### In Scope

- GH-52: Material 3 다크 테마, 색 토큰, 타이포 스케일, 간격 스케일, 컴포넌트 교체, 메인 탭 ViewPager2

### Out of Scope

- 라이트 테마·시스템 설정 연동, dynamic color → 소유자 결정(#51)으로 다크 고정·브랜드 색 유지
- Compose → 소유자 결정(#51)으로 View 유지
- 앱 전용 글꼴 → 시스템 글꼴을 쓴다
- `Spinner` 를 Material exposed dropdown 으로 바꾸는 것 (D#6)
- 화면 구성 변경 → 목록 카드(`spec_page_view.md` FR#1), 편집 단계 안내(`spec_tune_edit.md` FR#24), 이전/다음 버튼(`spec_page_view.md` FR#9), 설정(`spec_settings.md`)이 각자 정의한다
- 스토어 스크린샷 다시 찍기 → `spec_store.md` §6 규칙대로 UX 2차 반영 뒤 별도로 한다

## 3. 요구사항

### 3.1 사용자 스토리

- `US#1` 사용자로서, 화면마다 같은 역할의 버튼·카드·입력칸이 같은 모양이기를 원한다. 어디를 눌러야 할지 바로 알기 위해서다 [요구사항] GH-52
- `US#2` 사용자로서, 메인 화면에서 옆으로 밀어 웹페이지·Tune 목록을 오가고 싶다 [요구사항] GH-52

### 3.2 기능 요구사항

- `FR#1` 모든 화면은 `Theme.Material3.Dark` 계열을 부모로 둔 앱 테마 하나와 그 하위 테마만 쓴다 [요구사항] GH-52
  - 열람 화면의 전체화면 테마도 앱 테마를 상속한다 → 현재처럼 `Theme.AppCompat` 를 따로 부모로 두지 않는다.
  - 시스템 다크/라이트 설정과 무관하게 다크로 그린다. dynamic color 를 적용하지 않는다 (D#2).
- `FR#2` 색은 §4.1 토큰으로만 참조한다 [요구사항] GH-52
  - 레이아웃·drawable·Kotlin 은 색 값을 직접 쓰지 않고 테마 속성(`?attr/colorPrimary` 등) 또는 토큰 이름의 색 리소스를 참조한다.
  - 값으로 이름 붙인 기존 색 리소스는 남기지 않는다.
  - 예외: 앱 아이콘 전용 `ic_launcher_background` (`spec_store.md` FR#1), 벡터 아이콘 path 의 기본 채움색(틴트로 덮인다), 페이지 안에 주입하는 스크립트의 색(요소 선택 강조 테두리 등), 색상 규칙 프리셋(`spec_tune_edit.md` FR#27) → 앱 화면 색이 아니다.
- `FR#3` 화면 글자는 §4.2 스케일의 역할 하나로 지정한다 [요구사항] GH-52
  - 레이아웃과 코드는 글자 크기를 직접 지정하지 않고 `textAppearance` 로 역할을 고른다.
  - 최소 크기는 12sp 이고, 본문·안내 문구는 14sp 이상이다.
- `FR#4` 여백·간격은 §4.3 스케일 값만 쓴다 [요구사항] GH-52
- `FR#5` §4.4 표의 컴포넌트로 교체한다 [요구사항] GH-52
  - 편집 화면·웹페이지 다이얼로그 입력칸의 자동완성 제외 동작은 유지한다 → `TextInputLayout` 안의 편집칸도 `NoAutofillEditText` 를 쓴다.
- `FR#6` 열람 화면 위에 뜨는 오버레이(빠른 메뉴 툴바, Tune 상태 표시, 이전/다음 버튼)는 `scrim` 토큰 배경 위에 `onSurface` 글자·아이콘을 쓴다 [요구사항] GH-52
  - 근거: 오버레이 뒤 페이지 색을 앱이 정할 수 없다 → 가장 밝은 흰 페이지 위 합성색을 기준으로 대비를 맞춘다 (D#5).

  ```
  Given 흰 배경 페이지를 열람 중이다
  When  빠른 메뉴를 열고 Tune 상태 표시가 뜬다
  Then  오버레이 글자의 대비가 4.5:1 이상이다
  ```

- `FR#7` 메인 화면 탭은 `ViewPager2` + `FragmentStateAdapter` 이고 탭 표시는 `TabLayoutMediator` 로 연결한다 [요구사항] GH-52
  - 탭을 누르거나 옆으로 밀어 전환한다.
  - 툴바 메뉴(삭제 등)는 보이는 탭에만 작용한다.
  - 탭이 보이게 될 때 그 탭의 목록을 다시 읽는다 → 다른 탭이나 다른 화면에서 바꾼 내용이 반영된다.
  - 화면 회전·프로세스 재생성 뒤 보던 탭이 유지된다.
  - 탭을 전환하면 떠나는 탭의 삭제 선택 모드와 되돌리기 창을 닫는다 (`spec_page_view.md` FR#25).

  ```
  Given 웹페이지 탭에서 삭제 선택 모드로 2개를 선택했다
  When  Tunes 탭으로 민다
  Then  웹페이지 탭의 선택 모드가 끝나고, 툴바 삭제는 Tune 목록의 선택 모드를 연다
  ```

### 3.3 비기능 요구사항

- `NFR#1` §4.1 의 글자·배경 조합은 4.5:1, 비텍스트 조합은 3:1 이상이다 (WCAG 2.1 1.4.3, 1.4.11, 상대 휘도 식) [요구사항] GH-52
  - 새 조합을 쓰면 §4.1 대비 표에 비율과 함께 추가한다.
- `NFR#2` 모든 화면이 영어·한국어, 시스템 글꼴 100%·200% 에서 잘리거나 겹치지 않는다 [요구사항] GH-52
- `NFR#3` 런타임 의존성의 아티팩트 집합을 바꾸지 않는다 [요구사항] GH-52
  - `androidx.viewpager2:viewpager2` 는 Material 1.14.0 이 이미 `compile` 범위로 끌어온다(1.0.0) [as-built] → 직접 쓰므로 같은 버전으로 명시 선언만 한다 (D#4).
- `NFR#4` 기존 단위·수동 TC 가 그대로 통과한다. 단 리소스 이름에 기대는 테스트는 §7 TC#12 를 따른다 [요구사항] GH-52

## 4. 데이터 및 API 스펙

### 4.1 색 토큰

토큰 이름은 색 리소스 이름이고, 같은 이름의 Material 3 테마 속성(`colorPrimary`, `colorOnPrimary` …)에 연결한다.

| 토큰 | 값 | 용도 |
| --- | --- | --- |
| `primary` | `#ff4103` | 주 행동 버튼·FAB 채움, 선택 탭 표시선, 진행 막대 |
| `onPrimary` | `#171717` | `primary` 위 글자·아이콘 |
| `brandOnSurface` | `#ff7347` | 표면 위 강조 글자·아이콘 (텍스트 버튼, 선택 탭 글자, 칩 글자, 링크) — D#1 |
| `background`, `surface` | `#171717` | 화면 배경 |
| `surfaceContainerLow` | `#1e1e1e` | 툴바·탭 바 |
| `surfaceContainer` | `#242424` | 카드 |
| `surfaceContainerHigh` | `#2a2a2a` | 다이얼로그, 편집 하단 시트 |
| `onSurface` | `#E0E0E0` | 본문 글자·아이콘 |
| `onSurfaceVariant` | `#9E9E9E` | 보조 글자 (주소, 설명, 일치 수) |
| `outline` | `#8a8a8a` | 입력칸 테두리, 외곽선 버튼 |
| `outlineVariant` | `#2a2a2a` | 구분선 (장식) |
| `error` / `onError` | `#ffb4ab` / `#690005` | 입력 오류, 오류 화면 아이콘 |
| `scrim` | `#171717`, 불투명도 85% | 열람 화면 오버레이 배경 (FR#6) |
| `avatar1`~`avatar8` | `#ff7347` `#7cb7ff` `#6fd3a8` `#f5c451` `#c79bff` `#ff8fb1` `#5fd0e0` `#b5d96b` | 도메인 아바타 배경 (`spec_page_view.md` §6.7). 글자는 `onPrimary` |

대비 (계산값, 소수 둘째 자리)

| 앞 | 뒤 | 비율 | 기준 |
| --- | --- | --- | --- |
| `onPrimary` | `primary` | 5.13 | 4.5 |
| `onSurface` | `surface` / `surfaceContainerLow` / `surfaceContainer` / `surfaceContainerHigh` | 13.58 / 12.63 / 11.76 / 10.87 | 4.5 |
| `onSurfaceVariant` | 같은 순서 | 6.69 / 6.22 / 5.79 / 5.36 | 4.5 |
| `brandOnSurface` | 같은 순서 | 6.65 / 6.19 / 5.76 / 5.33 | 4.5 |
| `outline` | 같은 순서 | 5.19 / 4.83 / 4.50 / 4.16 | 3 (비텍스트) |
| `error` | 같은 순서 | 10.56 / 9.82 / 9.14 / 8.45 | 4.5 |
| `primary` (아이콘·막대) | `surface` / `surfaceContainerHigh` | 5.13 / 4.11 | 3 (비텍스트) |
| `onSurface` | `scrim` 을 흰 배경에 합성한 `#3a3a3a` | 8.62 | 4.5 |
| `onPrimary` | `avatar1`~`avatar8` | 최소 6.65 (`avatar1`) | 4.5 |

- `primary` 를 표면 위 글자색으로 쓰지 않는다 → `primary` / `surfaceContainer` = 4.44:1 로 4.5:1 미달 (D#1).

### 4.2 타이포 스케일

Material 3 `TextAppearance.Material3.*` 의 아래 역할만 쓴다.

| 역할 | 크기 | 용도 |
| --- | --- | --- |
| `headlineSmall` | 24sp | 시작 안내 제목, 빈 상태 제목 |
| `titleLarge` | 22sp | 툴바 제목 |
| `titleMedium` | 16sp, medium | 카드 제목, 다이얼로그 제목 |
| `bodyLarge` | 16sp | 입력값 |
| `bodyMedium` | 14sp | 본문, 안내, 카드 2행 |
| `labelLarge` | 14sp, medium | 버튼, 탭 |
| `bodySmall` | 12sp | 보조 정보 (일치 수, 셀렉터 자세히, 칩) |

### 4.3 간격 스케일

| 값 | 용도 |
| --- | --- |
| 4dp | 아이콘과 글자 사이, 칩 내부 |
| 8dp | 카드 사이, 같은 묶음 안 요소 사이 |
| 12dp | 카드 안 행 사이 |
| 16dp | 화면 좌우 여백, 카드·다이얼로그 안쪽 여백 |
| 24dp | 섹션 사이 |

- 터치 영역 48dp(`spec_tune_edit.md` NFR#3)는 간격이 아니라 크기 기준이라 이 표와 별개다.

### 4.4 컴포넌트

| 현재 [as-built] | 교체 | 비고 |
| --- | --- | --- |
| `androidx.appcompat.widget.Toolbar` | `MaterialToolbar` | |
| `androidx.cardview.widget.CardView` | `MaterialCardView` | `surfaceContainer`, 모서리 12dp, 그림자 없음 |
| `Button` | `MaterialButton` 역할별 스타일 | 주 행동(저장·추가) filled, 보조 outlined, 취소·닫기 text |
| 단독 `EditText`·`NoAutofillEditText` | `TextInputLayout`(outlined) + 편집칸 | 칸 이름·오류는 `TextInputLayout` 의 hint·error 로 |
| `android.app.AlertDialog`, `androidx.appcompat.app.AlertDialog` | `MaterialAlertDialogBuilder` | 색 선택·라이선스·삭제·이탈 확인 다이얼로그 포함 |
| `CheckBox`, `RadioButton` | 테마가 Material 위젯으로 부풀린다 | 태그를 바꾸지 않아도 된다 |
| `Spinner` | 유지 | D#6 |

## 5. 화면 및 UI/UX

- 적용 대상: 메인(두 탭, 빈 상태, 웹페이지 다이얼로그), Tune 편집(하단 시트 포함), 색 선택 다이얼로그, 열람 화면 오버레이, 설정, 시작 안내.

## 6. 동작 로직 및 정책

### 6.1 설계 결정

| 결정 | 선택 | 기각한 대안 | 근거 |
| --- | --- | --- | --- |
| `D#1` 표면 위 강조 글자색 [FR#2] [NFR#1] | 앱 토큰 `brandOnSurface`(`#ff7347`)를 두고 텍스트 버튼·탭·칩 스타일에 `materialThemeOverlay` 로 연결 | `primary` 를 `#ff7347` 로 바꿈, `#ff4103` 글자 허용 | M3 텍스트 버튼은 `primary` 를 글자색으로 쓴다 → `#ff4103` 은 카드·다이얼로그 위에서 4.5:1 미달. `primary` 자체를 바꾸면 브랜드 색 유지 결정(#51)과 충돌한다. M3 에 "표면 위 브랜드 글자" 역할 토큰이 없어 공통 제약의 M3 이름 규칙에서 이 하나만 앱 이름을 쓴다. 값은 `spec_tune_edit.md` D#5 와 같다 |
| `D#2` 다크 고정·dynamic color 미적용 [FR#1] | 다크 고정 | DayNight, Material You | 소유자 결정(#51). dynamic color 는 브랜드 색을 기기 배경화면 색으로 바꾼다 |
| `D#3` 컴포넌트 교체 단위 [FR#5] | 테마 전환과 컴포넌트 교체를 한 이슈(GH-52)에서 | 화면별로 나눠 교체 | Material 3 테마로 바꾸는 순간 남은 AppCompat·플랫폼 위젯이 다른 색 체계로 그려진다 → 중간 상태가 오히려 불일치를 키운다 |
| `D#4` ViewPager2 의존성 [FR#7] [NFR#3] | Material 이 끌어오는 1.0.0 을 명시 선언 | 전이 의존성에 기댐, 최신 버전 선언 | 직접 쓰는 라이브러리를 선언해 Material 버전이 바뀌어도 컴파일이 깨지지 않게 한다. 같은 버전이라 APK 아티팩트 집합이 바뀌지 않는다 |
| `D#5` 오버레이 배경 [FR#6] | `#171717` 85% | 50% 검정 유지, 불투명 배경 | 50% 검정은 흰 페이지 위 3.95:1. 85% 는 흰 페이지 위 합성색 `#3a3a3a` 에서 `onSurface` 8.62:1, 페이지가 비쳐 보여 오버레이임을 알 수 있다 |
| `D#6` 드롭다운 [§4.4] | `Spinner` 유지 | exposed dropdown(`TextInputLayout` + `AutoCompleteTextView`) | 이슈 범위의 교체 목록(UX-2)에 없다. 트리거 드롭다운은 소형 화면 높이 보장(`spec_tune_edit.md` TC#12)이 걸려 있어 함께 바꾸면 회귀 범위가 커진다 |

## 7. 테스트 및 검증 기준

| TC | 시나리오 | 대상 | 비고 |
| --- | --- | --- | --- |
| `TC#1` | 매니페스트의 모든 activity 테마와 application 테마가 앱 테마 계열이고, 앱 테마의 최상위 부모가 `Theme.Material3.Dark` 로 시작한다. `Theme.AppCompat` 부모가 0건 | [FR#1] | 단위 (리소스 XML 파싱) |
| `TC#2` | `values/colors.xml` 에 §4.1 토큰이 모두 같은 값으로 있고, 그 밖의 색 리소스는 `ic_launcher_background` 뿐이다. `layout`·`menu`·`drawable`(launcher·벡터 아이콘 제외) 에 `#` 색 리터럴 0건 | [FR#2] | 단위 |
| `TC#3` | `colors.xml` 값으로 §4.1 대비 표의 모든 조합을 WCAG 상대 휘도 식으로 계산 → 각 기준 이상. `scrim` 은 흰색 위 합성 후 계산 | [NFR#1] [FR#6] | 단위. mutation: 구현 PR 에서 `onSurfaceVariant` 를 `#5c5c5c` 로 바꿔 실패하는지 확인 |
| `TC#4` | `layout` 에 `android:textSize` 0건, Kotlin 에 `textSize =` 0건 | [FR#3] | 단위 (레이아웃) + `grep` (Kotlin) |
| `TC#5` | `layout` 의 `layout_margin*`·`padding*` 값이 0dp 또는 §4.3 값 | [FR#4] | 단위 |
| `TC#6` | `layout` 에 `androidx.appcompat.widget.Toolbar`·`androidx.cardview.widget.CardView`·단독 편집칸(부모가 `TextInputLayout` 이 아닌 `EditText`/`NoAutofillEditText`) 0건. Kotlin 에 `android.app.AlertDialog`·`androidx.appcompat.app.AlertDialog` import 0건 | [FR#5] | 단위 (레이아웃) + `grep` (Kotlin) |
| `TC#7` | 자동완성 서비스를 켠 기기에서 편집 화면·웹페이지 다이얼로그 입력칸에 자동완성 제안이 뜨지 않음. Layout Inspector 에서 버튼이 `MaterialButton`, 체크박스가 `MaterialCheckBox` | [FR#5] | 수동, 에뮬레이터 |
| `TC#8` | 흰 배경 페이지에서 빠른 메뉴·Tune 상태·이전/다음 버튼을 띄워 글자·아이콘이 읽힘 | [FR#6] | 수동. 비율은 TC#3 |
| `TC#9` | 탭 누르기·밀기로 전환, 각 탭 툴바 삭제가 그 탭에만 작용, Tunes 탭에서 Tune 을 지운 뒤 웹페이지 탭으로 밀면 연결 표시 갱신, 회전 후 보던 탭 유지, 선택 모드 중 탭 전환 → 선택 모드·되돌리기 창 닫힘 | [FR#7] | 수동, 에뮬레이터 |
| `TC#10` | 시각 회귀: §5 의 화면 × 영어·한국어 × 글꼴 100%·200% 를 변경 전후로 찍어 잘림·겹침이 없고 역할별 모양이 같음 | [NFR#2] [US#1] | 수동, 에뮬레이터. 확인한 화면 목록을 PR 본문에 남긴다 |
| `TC#11` | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 의 아티팩트 집합이 변경 전과 같음, `app/build.gradle` 에 compose 0건 | [NFR#3] | 로컬. 결과를 PR 본문에 남긴다 |
| `TC#12` | 검증 명령 통과. `ResourceRulesTest` 의 `spec_tune_edit.md` TC#22 테스트는 리소스 이름만 `onPrimary`·`brandOnSurface` 로 바꾸고 값 단언(`#171717`, `#ff7347`)은 그대로 둔다 | [NFR#4] | 자동 (검증 명령). 이름 변경은 Red 테스트 수정이므로 저장소 소유자 승인 후 진행한다 |

### 통합·실기기 확인

- TC#10 은 API 24 와 API 36 에뮬레이터에서 한다 → Material 3 위젯의 일부 그림자·리플은 API 별로 다르게 그려진다.

## 부록. 변경 이력

- GH-51: 최초 작성 (GH-52 범위)
