<!-- 3a4f541a-ccf4-4e2b-ad45-7bbc13ca5f2d a838579c-a89d-4611-9b02-07cabda283e0 -->
# MusicXML to VexFlow 완전 지원 구현 계획

## 분석 결과

### MusicXML 파일에서 발견된 주요 요소들

1. **기본 요소** (현재 지원됨)

- 노트, 쉼표, duration, pitch, accidental
- 조표, 박자표, 음자리표
- 화음 (chord)

2. **연주 기호** (부분 지원)

- ✅ staccato: 지원됨
- ❌ accent: 모델은 있으나 렌더링 미지원
- ❌ tenuto: 모델은 있으나 미지원
- ❌ marcato: 모델은 있으나 미지원
- ❌ staccatissimo: 모델은 있으나 미지원

3. **음표 연결** (부분 지원)

- ✅ beam: 기본 지원
- ❌ tuplet (time-modification): 미지원 (measure 7, 19 등에서 사용)

4. **방향 요소** (부분 지원)

- ✅ dynamics: 데이터 구조는 있으나 렌더링 확인 필요
- ❌ wedge (crescendo/diminuendo): 미지원 (measure 8, 20, 22 등)
- ❌ metronome: 미지원 (measure X0)

5. **기타 요소**

- ❌ credit (제목, 작곡가, 페이지 번호): 미지원
- ✅ barline (repeat): 데이터 구조는 있으나 렌더링 확인 필요
- ✅ slur, tie: 데이터 구조는 있으나 렌더링 확인 필요

## 구현 계획

### Phase 1: Articulations 확장

**파일**: `MusicXmlAdapter.kt`, `MainActivity.kt`

1. `extractArticulations()` 확장

- accent, tenuto, marcato, staccatissimo 추가
- MusicXML 모델의 `Accent.kt`, `Tenuto.kt`, `Marcato.kt`, `Staccatissimo.kt` 활용

2. VexFlow 렌더링 추가

- `MainActivity.kt`의 articulation 처리 로직 확장
- VexFlow `Articulation` 클래스 사용 (이미 구현됨)

### Phase 2: Tuplets 지원

**파일**: `MusicXmlAdapter.kt`, `MusicXmlToVexFlowConverter.kt`, `MainActivity.kt`

1. 데이터 모델 확장

- `VexFlowNote`에 `tupletRatio: Pair<Int, Int>?` 추가 (actual-notes, normal-notes)
- `time-modification` 파싱 추가

2. VexFlow 렌더링

- VexFlow `Tuplet` 클래스 사용
- `MainActivity.kt`에서 tuplet 그룹 감지 및 렌더링

### Phase 3: Wedge (Crescendo/Diminuendo) 지원

**파일**: `MusicXmlAdapter.kt`, `MusicXmlToVexFlowConverter.kt`, `MainActivity.kt`

1. 데이터 모델 추가

- `MeasureData`에 `wedges: List<WedgeData>` 추가
- `WedgeData` 클래스 생성 (type, startNote, endNote, placement)

2. VexFlow 렌더링

- VexFlow `Crescendo`/`Diminuendo` 클래스 확인 및 사용
- 또는 `TextDynamics`로 대체 가능

### Phase 4: Metronome 지원

**파일**: `MusicXmlAdapter.kt`, `MusicXmlToVexFlowConverter.kt`, `MainActivity.kt`

1. 데이터 모델 추가

- `MeasureData`에 `metronome: MetronomeData?` 추가
- `MetronomeData` 클래스 생성 (beatUnit, perMinute)

2. VexFlow 렌더링

- VexFlow `TextNote` 또는 `Annotation`으로 텍스트 표시
- 예: "♩ = 100"

### Phase 5: Dynamics 렌더링 개선

**파일**: `MainActivity.kt`

1. 현재 구현 확인

- `DynamicsData`는 이미 있으나 렌더링 확인 필요

2. VexFlow 렌더링

- `TextDynamics` 또는 `Annotation` 사용
- placement (above/below) 반영

### Phase 6: Credit/Text 정보 렌더링

**파일**: `MusicXmlAdapter.kt`, `MainActivity.kt`

1. 데이터 모델 추가

- `ScoreData` 클래스 생성 (title, composer, partName 등)
- `MusicXmlAdapter`에서 credit 정보 추출

2. VexFlow 렌더링

- `TextNote` 또는 SVG 텍스트로 표시
- 페이지 상단/하단에 배치

### Phase 7: Slur/Tie 렌더링 확인 및 개선

**파일**: `MainActivity.kt`

1. 현재 구현 확인

- `StaveTie` 사용 여부 확인
- `StaveSlur` 사용 여부 확인

2. 개선 사항

- 다중 슬러/타이 지원
- orientation (over/under) 반영

### Phase 8: Barline (Repeat) 렌더링

**파일**: `MainActivity.kt`

1. 현재 구현 확인

- `RepeatBarData`는 있으나 렌더링 확인 필요

2. VexFlow 렌더링

- `Barline` 클래스 사용
- repeat 기호 표시

## 기존 로직 정리

플랜 실행 시 기존 로직이 방해되는 경우 삭제 가능:

- 불완전한 구현 코드 (TODO 주석이 있는 부분)
- 중복된 렌더링 로직
- 사용되지 않는 데이터 구조
- 테스트용 임시 코드

## 우선순위

1. **High**: Articulations 확장, Tuplets, Dynamics 렌더링
2. **Medium**: Wedge, Metronome, Slur/Tie 개선
3. **Low**: Credit/Text, Barline

## 참고 자료

- VexFlow Wiki: https://github.com/0xfe/vexflow/wiki
- VexFlow Tutorial: Tuplets, Beams, Ties 섹션 참조
- MusicXML 4.0 DTD: 요소 구조 확인

### To-dos

- [ ] Articulations 확장: accent, tenuto, marcato, staccatissimo 파싱 및 렌더링 추가
- [ ] Tuplets 지원: time-modification 파싱 및 VexFlow Tuplet 렌더링 구현
- [ ] Wedge (crescendo/diminuendo) 지원: 데이터 모델 추가 및 VexFlow 렌더링
- [ ] Metronome 지원: direction/metronome 파싱 및 텍스트 표시
- [ ] Dynamics 렌더링 개선: 현재 구현 확인 및 VexFlow TextDynamics 적용
- [ ] Credit/Text 정보 렌더링: 제목, 작곡가 등 텍스트 표시
- [ ] Slur/Tie 렌더링 확인 및 개선: 다중 슬러/타이 및 orientation 지원
- [ ] Barline (Repeat) 렌더링: 반복 기호 표시