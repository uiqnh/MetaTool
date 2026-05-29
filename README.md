# MetaTool

MetaTool은 이미지에 포함된 메타데이터를 분석하고, 사용자가 선택한 메타데이터를 제거할 수 있는 Android 애플리케이션입니다.  
사진에 포함된 GPS 위치 정보, 촬영 날짜, 기기 정보 등을 확인하고 제거하여 이미지 공유 전 개인정보 노출 위험을 줄일 수 있습니다.

## 주요 기능

- 갤러리 이미지 업로드 및 미리보기
- 이미지 EXIF 메타데이터 분석
- 메타데이터 위험도별 분류
- GPS 위치 정보 Google Map 시각화
- 선택한 메타데이터 제거
- 저장 품질 선택
- 원본 이미지 삭제 옵션
- SQLite 기반 개인정보 보호 리포트 제공
- 통계 초기화 기능

## 기술 스택

| 구분 | 내용 |
|---|---|
| Language | Java |
| Platform | Android |
| Map | Google Maps API |
| Metadata | Android ExifInterface |
| Database | SQLite |
| Build | Gradle Kotlin DSL |

## 프로젝트 구조

```text
app/src/main
├── AndroidManifest.xml
├── java/com/cookandroid/metatool
│   ├── MainActivity.java
│   ├── AnalyzeActivity.java
│   ├── StatsActivity.java
│   ├── PrivacyStatsDBHelper.java
│   └── NonScrollListView.java
└── res
    ├── layout
    │   ├── activity_main.xml
    │   ├── activity_analyze.xml
    │   └── activity_stats.xml
    ├── drawable
    ├── mipmap
    ├── values
    └── xml
```

## 주요 화면

| 화면 | 역할 |
|---|---|
| `MainActivity` | 이미지를 업로드하고 미리보기로 표시하는 메인 화면 |
| `AnalyzeActivity` | 이미지 메타데이터를 분석하고, 선택한 메타데이터를 제거하는 화면 |
| `StatsActivity` | SQLite에 저장된 통계를 조회하여 개인정보 보호 리포트를 표시하는 화면 |

## 주요 보조 클래스

| 클래스 | 역할 |
|---|---|
| `PrivacyStatsDBHelper` | SQLite 데이터베이스 생성, 통계 저장, 통계 초기화를 담당 |
| `NonScrollListView` | ScrollView 안에서 ListView의 스크롤 중첩 문제를 해결하기 위한 커스텀 ListView |

## Google Maps API 키 설정

이 프로젝트는 Google Maps API를 사용합니다.  
실행 전 `AndroidManifest.xml`의 아래 값을 본인의 Google Maps API 키로 변경해야 합니다.

```xml
<meta-data
    android:name="com.google.android.maps.v2.API_KEY"
    android:value="GOOGLE_MAPS_API_KEY" />
```

`GOOGLE_MAPS_API_KEY` 부분을 본인의 API 키로 교체하면 됩니다.

## 실행 방법

1. Android Studio에서 프로젝트를 엽니다.
2. `AndroidManifest.xml`에 Google Maps API 키를 입력합니다.
3. Gradle Sync를 실행합니다.
4. 에뮬레이터 또는 실제 Android 기기에서 앱을 실행합니다.
5. 이미지를 업로드한 뒤 메타데이터 분석 및 제거 기능을 사용합니다.

## 필요 권한

| 권한 | 사용 목적 |
|---|---|
| `ACCESS_MEDIA_LOCATION` | 이미지의 EXIF GPS 위치 정보 접근 |
| `READ_EXTERNAL_STORAGE` | Android 12 이하에서 이미지 파일 읽기 |
| `READ_MEDIA_IMAGES` | Android 13 이상에서 이미지 파일 읽기 |
| `INTERNET` | Google Maps 지도 데이터 로딩 |

## 데이터베이스

앱은 SQLite를 사용하여 개인정보 보호 리포트 통계를 저장합니다.

| 컬럼 | 설명 |
|---|---|
| `total_protected_images` | 메타데이터 제거 후 저장한 이미지 수 |
| `total_removed_metadata` | 제거한 메타데이터 전체 개수 |
| `high_risk_protected` | 고위험 메타데이터 제거 횟수 |
| `medium_risk_protected` | 중위험 메타데이터 제거 횟수 |
| `low_risk_protected` | 저위험 메타데이터 제거 횟수 |
