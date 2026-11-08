# page-tune 스펙

| 항목 | 값 |
| --- | --- |
| 문서명 | page-tune specification |
| 작성자 | dohwan.ahn |
| 작성일 / 최종 수정일 | 2026-10-18 / 2026-11-08 |

- **프로젝트:** 모바일 웹 페이지의 광고·타이틀바 같은 불필요한 요소를 숨기고 색상·시작 스크롤 위치·이전/다음 이동을 사이트별 Tune 으로 저장해 적용하는 Android 브라우저 앱.
- **목적 (Why):**
  - 모바일에서 커뮤니티 사이트를 볼 때 광고·타이틀바가 화면을 차지하고, 이전/다음 글 이동 버튼이 작아 누르기 어렵다.

## 유스케이스

| UC | 목표 | 우선순위 | 근거 | 스펙 |
| --- | --- | --- | --- | --- |
| UC1 | 사이트에서 요소를 골라 숨기기·색상·클릭 매핑·시작 위치를 Tune 으로 저장한다 | P1 | Tune 이 없으면 앱이 일반 브라우저와 같다 | `spec_tune_edit.md` |
| UC2 | 웹페이지를 Tune 과 묶어 등록하고, 열 때마다 Tune 을 적용해 본다 | P1 | UC1 의 결과를 실제로 쓰는 경로다 | `spec_page_view.md` |
| UC3 | 앱 버전·오픈소스 라이선스·개인정보 처리방침을 앱 안에서 확인한다 | P3 | 없어도 Tune 을 만들고 쓰는 데 지장이 없다 | `spec_settings.md` |

- UC 가 아닌 공통 스펙: 디자인 시스템 `spec_design.md`, 빌드·배포 `spec_release.md`, 스토어 자산 `spec_store.md`

## 작업 기준 색인

| 작업 | 스펙 위치 |
| --- | --- |
| 툴체인·targetSdk 를 올린다 | `spec_release.md` §2, §6 설계 결정 |
| 버전 규칙·서명·Play 배포를 고친다 | `spec_release.md` §4, §6 |
| 화면이 시스템 바·키보드와 겹친다 | `spec_release.md` FR#7 |
| 요소 선택·셀렉터 생성·불안정 id 판정을 고친다 | `spec_tune_edit.md` §4.3, §6.1, §6.2 |
| Tune 편집 화면(규칙 추가·저장·이탈 확인)을 고친다 | `spec_tune_edit.md` §3.2, §6.4 |
| 열람 중 숨기기·색·클릭 연결이 적용되지 않는다 | `spec_page_view.md` §6.1, §6.2 |
| 열람 화면 뒤로가기·빠른 메뉴·Tune 켜기/끄기를 고친다 | `spec_page_view.md` §3.2, §6.4, §6.5 |
| 웹페이지 목록·등록 다이얼로그를 고친다 | `spec_page_view.md` FR#1~FR#5 |
| 테마·색 토큰·글자 크기·간격·메인 탭을 고친다 | `spec_design.md` §4, FR#7 |
| 목록 카드·아바타·선택 모드·삭제 되돌리기를 고친다 | `spec_page_view.md` FR#1, FR#3, FR#24~FR#26, §6.7, §6.8 / Tunes 탭은 `spec_tune_edit.md` FR#1, FR#3, FR#30 |
| 시작 안내를 고친다 | `spec_page_view.md` FR#27 |
| Tune 편집 단계 안내·요소 라벨·규칙 강조·색 선택·로드 오류를 고친다 | `spec_tune_edit.md` FR#24~FR#28, §6.11~§6.14 |
| 이전/다음 버튼 모양·자동 숨김을 고친다 | `spec_page_view.md` FR#9, FR#28, §6.9 |
| 열람·편집 WebView 의 오류 화면·인증서 오류·외부 링크·다운로드를 고친다 | `spec_page_view.md` FR#29~FR#33, §6.10~§6.12 (보안 경계) |
| 설정 화면을 고친다 | `spec_settings.md` |
| 앱 아이콘·개인정보 처리방침·스토어 문구·그래픽을 고친다 | `spec_store.md` FR#1~FR#11, §4 |
| 권한·저장·전송 경로를 바꾼다 | `spec_store.md` FR#5 (`docs/privacy.md` 함께 수정) |
| 화면 문구를 추가·수정한다 | 아래 "UI 문구" |

## 공통 제약

여러 기능에 걸친 제약과 기능 사이 모순은 여기에 모은다.

- 기존 사용자 데이터(Room DB)는 앱 업데이트 후에도 보존한다 → 스키마 변경은 마이그레이션과 `app/schemas/` 스냅샷을 함께 둔다 (`spec_release.md` NFR#1)
- 앱 테마는 다크 고정이고 브랜드 색(`#ff4103`, 배경 `#171717` 계열)을 유지한다. 색 리소스를 새로 만들거나 이름을 바꿀 때는 Material 3 토큰 이름(`primary`, `onPrimary`, `surface`, `onSurface`, `outline` 등)을 쓴다 [요구사항]
  - 토큰 값·대비·글자·간격 스케일은 `spec_design.md` §4 한 곳에 둔다. M3 에 역할이 없는 `brandOnSurface`·`scrim` 투명도·`avatar1~8` 의 근거도 거기 있다.
- 열람·편집 두 WebView 는 링크 분류·intent 구성·로드 오류 분류·인증서 오류 처리를 구현 하나로 공유한다 (`spec_page_view.md` §6.10~§6.12) → 같은 링크·오류가 화면마다 다르게 처리되지 않는다 [요구사항]
- SharedPreferences 에 두는 값은 시작 안내 완료 여부 하나다 (`spec_page_view.md` FR#27). 값을 추가하면 `docs/privacy.md` 를 함께 고친다 (`spec_store.md` FR#5) [요구사항]
- 스토어·앱 문구는 "맞춤 보기 도구"로 쓰고 광고 차단을 주 문구로 쓰지 않는다 → Play 정책상 다른 앱 광고 방해·허락 없는 웹사이트 래퍼로 읽히지 않게 한다 [요구사항]

### UI 문구

- 사용자에게 보이는 문자열은 모두 문자열 리소스에 둔다. 기본(`values`)은 영어, `values-ko` 는 한국어다 [요구사항]
  - 근거: 영어를 기본으로 두어야 번역이 없는 언어에서도 화면이 비지 않는다.
- 개발자 용어(selector, inspector, GNB, gesture, setup, plugin)를 화면에 쓰지 않는다. 셀렉터 문자열 자체를 보여줄 때만 예외다 [요구사항]
- 같은 개념은 아래 표의 한 단어로만 부른다. 코드 식별자는 바꾸지 않는다 [요구사항]
- 한국어 문체: 버튼은 명사형 또는 동사 원형, 안내문은 해요체, 토스트는 마침표 없음 [요구사항]
- 개수와 수 일치가 필요한 문장은 plurals 를 쓴다. "명사 라벨 + 숫자" 형식(e.g. 숨기기 2)은 수 일치가 없으므로 일반 문자열로 둔다 [요구사항]
- 인자가 둘 이상이면 위치 지정자(`%1$s`)를 쓴다 → 언어마다 어순이 다르다 [요구사항]
- Tune 은 한국어 화면에서도 원어 "Tune" 으로 쓴다 [요구사항]

| 개념 | 코드 식별자 | 영어 | 한국어 | 쓰지 않는 말 |
| --- | --- | --- | --- | --- |
| Tune | `Plugin`, `plugin` | Tune | Tune | Plugin |
| 웹페이지 | `WebPage` | Web page | 웹페이지 | Page, Webpage, Web Page |
| 규칙 (Tune 안의 1건) | `PluginAction` | Rule | 규칙 | Setup, Action |
| 숨기기 | `hidden` | Hide | 숨기기 | Hidden |
| 색상 (하위: 배경색·글자색) | `color` | Color (Background color / Text color) | 색상 (배경색 / 글자색) | |
| 이전/다음 이동 (클릭 연결 규칙 종류) | `click` | Prev/Next navigation | 이전/다음 이동 | Click Event |
| 이전/다음 버튼 (열람 화면의 버튼, 트리거) | `gnb_prev`, `gnb_next` | Prev/Next button | 이전/다음 버튼 | Floating GNB |
| 두 번 탭 (트리거) | `gesture_top`, `gesture_bottom` | Double tap | 위쪽/아래쪽 영역 두 번 탭 | Gesture |
| 요소 선택 | inspect / pick mode | Pick element | 요소 선택 | Pick Mode, Inspector |
| 시작 위치 | `scroll` | Start position | 시작 위치 | |
| 일치 (셀렉터가 찾은 요소 수) | — | Matches | 일치 | |
| 다시 선택 필요 | — | Select again | 다시 선택 필요 | |
| 요소를 찾을 수 없음 | — | Element not found | 요소를 찾을 수 없음 | Broken Selector |
| 빠른 메뉴 | — | Quick menu | 빠른 메뉴 | |
| 실행취소 (삭제 되돌리기) | — | Undo | 실행취소 | Revert |
| 시작 안내 | — | Getting started | 시작 안내 | Onboarding, Tutorial, 튜토리얼 |
| 설정 | — | Settings | 설정 | |
| 다시 시도 | — | Try again | 다시 시도 | Retry, 재시도 |
| 자세히 (셀렉터 펼치기) | — | Details | 자세히 | |

## 제외 범위

- 다른 앱 안의 광고 차단 → 앱 자체 WebView 에서 연 페이지에만 적용한다
- TalkBack 등 화면 낭독기 동작 보장 → 소유자 결정으로 지원하지 않는다. 아이콘 버튼의 접근성 라벨은 유지하되 낭독 확인은 하지 않는다
