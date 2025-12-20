# UI 색상 가이드

UI 생성 시 참조할 텍스트 색상 및 스타일 가이드입니다.

## 텍스트 색상 시스템

### Primary Text
- **색상 코드**: `#222222`
- **용도**: 가장 중요한 제목, 숫자
- **사용 예시**: 
  - 메인 화면 제목
  - 중요한 통계나 숫자 표시
  - 주요 섹션 헤더

### Secondary Text
- **색상 코드**: `#444444`
- **용도**: 일반 설명, 부제목
- **사용 예시**:
  - 본문 텍스트
  - 서브헤더
  - 설명 문구
  - 보조 정보

### Accent Text
- **색상 코드**: `#1A1E23`
- **용도**: 링크, 강조할 정보 (네이비 톤)
- **사용 예시**:
  - 클릭 가능한 링크
  - 강조가 필요한 정보
  - CTA(행동 유도) 버튼 텍스트
  - 중요한 알림 메시지

## UI 생성 프롬프트 예시

```
UI를 생성할 때 다음 색상 가이드를 준수하세요:

- 가장 중요한 제목이나 숫자: #222222 (Primary Text)
- 일반 설명이나 부제목: #444444 (Secondary Text)  
- 링크나 강조할 정보: #1A1E23 (Accent Text, 네이비 톤)

텍스트의 중요도와 역할에 따라 적절한 색상을 선택하여 사용하세요.
```

## Android 구현 예시

### XML 리소스 (colors.xml)
```xml
<color name="primary_text">#222222</color>
<color name="secondary_text">#444444</color>
<color name="accent_text">#1A1E23</color>
```

### Kotlin 코드
```kotlin
// Primary Text
textView.setTextColor(Color.parseColor("#222222"))

// Secondary Text
textView.setTextColor(Color.parseColor("#444444"))

// Accent Text
textView.setTextColor(Color.parseColor("#1A1E23"))
```

