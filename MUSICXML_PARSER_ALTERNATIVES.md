# MusicXML 파서 대안 라이브러리 검토

## 현재 문제점
- ProxyMusic 4.0.3은 JAXB에 의존
- JAXB는 Android에서 `java.awt.Image` 클래스를 찾을 수 없어 실패
- MusicXML 4.0 파일 파싱 불가

## 대안 라이브러리 비교

### 1. Jackson XML (추천 ⭐⭐⭐⭐⭐)
**장점:**
- Android에서 완벽하게 작동
- 활발한 커뮤니티 지원
- 어노테이션 기반 POJO 매핑
- MusicXML의 복잡한 구조를 객체로 매핑 가능
- JSON과 XML 모두 지원 (일관된 API)

**단점:**
- MusicXML 전용이 아니므로 수동 매핑 필요
- 라이브러리 크기가 약간 큼

**의존성:**
```kotlin
implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.15.2")
```

**사용 예시:**
```kotlin
val xmlMapper = XmlMapper()
val score = xmlMapper.readValue(xmlContent, ScorePartwise::class.java)
```

---

### 2. kotlinx.serialization XML (추천 ⭐⭐⭐⭐)
**장점:**
- Kotlin 네이티브 지원
- Android에서 완벽하게 작동
- 컴파일 타임 코드 생성 (성능 우수)
- 의존성 적음

**단점:**
- MusicXML 전용이 아니므로 수동 매핑 필요
- XML 지원이 상대적으로 최근 추가됨

**의존성:**
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-xml:1.6.0")
```

**사용 예시:**
```kotlin
@Serializable
data class ScorePartwise(...)

val score = Xml.decodeFromString<ScorePartwise>(xmlContent)
```

---

### 3. Android XmlPullParser (추천 ⭐⭐⭐)
**장점:**
- 추가 의존성 없음 (Android 기본 제공)
- 메모리 효율적
- 빠른 파싱 속도

**단점:**
- 수동 파싱 로직 필요 (복잡함)
- MusicXML의 복잡한 구조를 모두 처리하기 어려움
- 유지보수 비용 높음

**사용 예시:**
```kotlin
val parser = XmlPullParserFactory.newInstance().newPullParser()
parser.setInput(StringReader(xmlContent))
// 수동으로 각 요소 파싱...
```

---

### 4. Simple XML Framework (추천 ⭐⭐)
**장점:**
- Android에서 작동
- 어노테이션 기반

**단점:**
- 유지보수 상태 불명확
- MusicXML 전용이 아님
- 커뮤니티 지원 제한적

---

## 추천 방안

### 옵션 1: Jackson XML 사용 (가장 추천)
- MusicXML의 복잡한 구조를 객체로 매핑 가능
- 안정적이고 활발한 커뮤니티 지원
- ProxyMusic의 데이터 클래스를 재사용 가능

### 옵션 2: kotlinx.serialization XML
- Kotlin 프로젝트에 최적화
- 컴파일 타임 안전성
- 성능 우수

### 옵션 3: XmlPullParser 직접 구현
- 의존성 없음
- 필요한 부분만 파싱 가능
- 하지만 구현 복잡도 높음

## 다음 단계
1. Jackson XML로 구현 시도
2. MusicXML 4.0의 주요 요소만 파싱하는 간단한 버전 구현
3. 기존 ProxyMusic 데이터 클래스 재사용 또는 새로운 데이터 클래스 생성



