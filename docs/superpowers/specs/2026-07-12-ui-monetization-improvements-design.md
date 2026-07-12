# LED Screen v1.3 — UI 폴리싱 + 수익화 개선 설계

- 날짜: 2026-07-12
- 대상: `com.odom.ledscreen` (versionCode 4 / versionName 1.2 → **versionCode 5 / versionName 1.3**)
- 접근: 단일 릴리스로 수익화 개선과 UI/기능 개선을 함께 배포 (광고 증가를 기능 가치로 상쇄해 평점 방어)
- 범위: 기존 View/XML 구조 유지 + 폴리싱 (Compose 마이그레이션 없음, 화면 재설계 없음)

## 배경 (현재 상태)

- 기능: 텍스트 입력 → 배경색/글자색 선택 → 크기/좌우 흐름/깜빡임 설정 → 전체화면 가로 전광판 표시
- 구조: `MainActivity`(설정) + `ResultActivity`(전광판) + `ColorSelectorDialog`
- 수익화: 메인 하단 AdMob 배너 1개. XML에 `adSize="BANNER"`(320×50 고정)로 지정되어 코드의 적응형 사이즈 계산(`MainActivity.adSize`)이 실제로 사용되지 않음
- 마퀴: XML translate 애니메이션(`marquee_rtl`/`marquee_ltr`) — duration 고정, 반복 시 끊김
- 전광판 화면: deprecated `SYSTEM_UI_FLAG_FULLSCREEN` 사용, 화면 꺼짐 방지 없음, 밝기 제어 없음

## 1. 수익화 + 인앱리뷰

### 1.1 전면광고 (InterstitialAd)

**트리거 로직** — 카운트는 설정 변경으로 쌓고, 표시는 화면 전환 시점으로 분리:

- "설정 변경" 카운트 대상: 색상 선택 확정(배경/글자), 방향 토글(좌/우), 깜빡임 토글, 속도 변경, 프리셋 적용
- 카운트 제외: 폰트 크기 +/− (연타성 버튼), 텍스트 입력
- 누적 5회 도달 후 **다음 START 버튼 진입 시점**에 전면광고 표시 → 광고 닫으면 `ResultActivity` 진입, 카운터 리셋

**가드 조건** (하나라도 해당하면 광고 없이 바로 진입):

- 앱 최초 실행 세션에서는 미표시 (SharedPreferences 첫 실행 플래그)
- 직전 전면광고 표시 후 2분 미경과
- 광고 미로드/로드 실패 (대기·블로킹 절대 금지)
- 해당 진입에서 인앱리뷰 요청이 예정된 경우 (1.4와 충돌 방지)

**로딩**: 앱 시작 시 미리 로드, 표시 후 즉시 다음 광고 재로드.

### 1.2 종료 다이얼로그 + 광고

- `MainActivity` 뒤로가기(OnBackPressedCallback) → 종료 확인 다이얼로그
- 구성: 미리 로드해둔 **MREC(300×250) 배너** + [종료] / [취소] 버튼 (버튼은 광고와 명확히 분리 배치 — 오클릭 방지, AdMob 정책 준수)
- MREC는 앱 시작 시 미리 로드. 로드 실패 시 광고 영역 없이 다이얼로그만 표시
- [종료] → `finish()`, [취소] → 다이얼로그 닫기

### 1.3 배너 개선 (적응형 앵커 배너)

- XML의 고정 `AdView` 제거 → 컨테이너(FrameLayout)만 두고 코드에서 `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize` 기반으로 AdView 생성/장착
- deprecated `windowManager.defaultDisplay` → `WindowMetrics` 기반 폭 계산으로 교체

### 1.4 인앱 리뷰 (Google Play In-App Review API)

- 트리거: 전광판 화면(`ResultActivity`) 사용 횟수를 누적, **3회 이상 사용 후 메인으로 돌아온 시점**에 `requestReviewFlow` → `launchReviewFlow`
- 표시 여부는 Play가 최종 결정 (조용히 실패 가능, 별도 fallback UI 없음)
- 요청 성공 여부와 무관하게 1회 시도 후 재요청 안 함 (플래그 저장)
- 리뷰 요청 예정 진입에서는 전면광고 스킵

### 1.5 SDK/기술 업데이트

- `play-services-ads` 22.2.0 → 24.x 최신
- `com.google.android.play:review-ktx` 추가
- **UMP(User Messaging Platform) 동의 SDK** 적용 — 앱 시작 시 동의 정보 갱신, 필요 시(EEA 등) 동의 폼 표시 후 광고 초기화. AdMob 필수 요건
- 광고 상태(카운터, 첫 실행, 마지막 광고 시각, 리뷰 플래그)는 SharedPreferences로 관리하는 단일 헬퍼 클래스(`AdsManager` 등)로 분리 — `MainActivity` 비대화 방지

## 2. UI 폴리싱 + 기능

### 2.1 다크 LED 테마

- 메인 화면: 검정 배경 + 네온 톤 강조색(테마 색상 리소스 정리)으로 "전광판 앱" 감성 통일
- 토글 버튼(좌/우 방향, 깜빡임)의 선택 상태를 네온 하이라이트로 명확히 표시 (현재 selected 상태가 시각적으로 거의 구분 안 됨)
- Material 버튼 스타일 정리 (START는 강조 스타일)

### 2.2 속도 조절

- 메인 화면에 3단계(느림/보통/빠름) 속도 선택 UI 추가, `ResultActivity`로 Intent extra 전달
- `ResultActivity` 마퀴를 XML translate 애니메이션 → **ObjectAnimator 기반 코드 마퀴**로 교체
  - 속도 = 텍스트 폭 기준 px/sec 환산으로 duration 계산 (긴 텍스트도 체감 속도 일정)
  - 화면 밖 → 반대편 재진입의 **끊김 없는 무한 반복**
- 메인 미리보기의 마퀴도 동일 로직 사용 (속도/효과가 실제와 동일하게 반영)

### 2.3 전광판 화면 기본기

- `FLAG_KEEP_SCREEN_ON` — 화면 꺼짐 방지
- `window.attributes.screenBrightness = 1f` — 표시 중 밝기 최대 (종료 시 원복)
- deprecated 플래그 제거 → `WindowInsetsControllerCompat` 기반 몰입 모드(상태바/내비바 숨김, 스와이프 시 일시 표시)
- 화면 탭 → 종료(finish). 진입 직후 짧은 안내 토스트 1회("탭하면 돌아갑니다")

### 2.4 프리셋/효과 (최소 구성)

- **색상 프리셋 칩 5~6개**: 잘 어울리는 배경+글자색 조합 원터치 적용 (예: 검정+네온그린, 검정+레드, 파랑+화이트 등)
- **무지개 글자 효과 1종**: 글자색 선택지에 "무지개" 추가 — `LinearGradient` 셰이더로 구현, 전광판 화면에도 동일 적용
- Intent extra 확장: 무지개 여부 플래그 추가 (기존 색상 extra와 병행)

## 3. 에러 처리 원칙

- 광고는 어떤 경우에도 앱 흐름을 막지 않는다: 로드 실패/미로드 시 해당 지점을 조용히 통과
- UMP 동의 흐름 실패 시에도 앱 기능은 정상 동작 (광고만 제한될 수 있음)
- 오프라인에서 전광판 핵심 기능은 전부 동작해야 함

## 4. 검증 계획

- AdMob **테스트 광고 ID**로 전면광고 트리거(5회 규칙, 첫 세션 제외, 2분 간격), 종료 다이얼로그, 적응형 배너 전 시나리오 수동 검증
- 인앱리뷰는 내부 테스트 트랙에서 확인 (개발 빌드에서는 표시 안 될 수 있음)
- 마퀴 속도 3단계 × 방향 2종 × 깜빡임 × 무지개 조합 스모크 테스트
- 릴리스 빌드에서 실제 광고 ID 확인 후 versionCode 5 / versionName 1.3으로 배포

## 5. 범위 제외 (명시)

- Compose 마이그레이션, 화면 전면 재설계
- 보상형 광고, 인앱결제(광고 제거)
- 폰트 선택, 이미지/이모지 전광판 등 신규 대형 기능
