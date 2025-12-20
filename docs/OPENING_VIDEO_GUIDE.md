# 오프닝 영상(Opening) 가이드

현재 `SplashActivity`는 오프닝 영상을 **화면 꽉참(센터크롭/줌)**으로 보여줍니다.  
따라서 원본 영상이 **가로(landscape)**이거나 **해상도가 낮으면**, 확대/크롭 때문에 “너무 확대됨 / 깨짐”이 자연스럽게 발생합니다.

## 권장 사양(옵션 C: 소스 재인코딩)
- **권장 해상도**: `1080x1920` (9:16, 세로)
- **권장 프레임**: 30fps 또는 60fps
- **코덱**: H.264 (`libx264`)
- **오디오**: 필요 없으면 제거(`-an`) 권장

## 변환 방법(맥)
1) ffmpeg 설치

```bash
brew install ffmpeg
```

2) 원본 영상들을 9:16 세로로 변환

- **A안(꽉 채우기, 일부 크롭 발생)**: 배경 영상에 일반적으로 추천

```bash
./scripts/encode_openings.sh crop path/to/opening-1.mp4 path/to/opening-2.mp4 path/to/opening-3.mp4
```

- **B안(안 잘림, 여백/pad)**: 크롭이 싫다면

```bash
./scripts/encode_openings.sh pad path/to/opening-1.mp4 path/to/opening-2.mp4 path/to/opening-3.mp4
```

변환이 끝나면 결과 파일이 자동으로 아래 경로에 저장됩니다:
- `app/src/main/res/raw/opening_1.mp4`
- `app/src/main/res/raw/opening_2.mp4`
- `app/src/main/res/raw/opening_3.mp4`

## 디버그(왜 확대/깨짐이 발생하는지)
앱 실행 시 Logcat에서 `SplashVideo` 태그를 보면,
- 영상 해상도(예: 640x360)
- 화면(TextureView) 크기
- 적용 스케일 값
을 확인할 수 있습니다.





