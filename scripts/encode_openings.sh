#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-}"
shift || true

if [[ -z "${MODE}" || ("${MODE}" != "crop" && "${MODE}" != "pad") ]]; then
  echo "Usage:"
  echo "  $0 crop <input1.mp4> <input2.mp4> <input3.mp4>"
  echo "  $0 pad  <input1.mp4> <input2.mp4> <input3.mp4>"
  exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg not found. Install it first (mac): brew install ffmpeg"
  exit 1
fi

OUT_DIR="app/src/main/res/raw"
mkdir -p "${OUT_DIR}"

TARGET_W=1080
TARGET_H=1920
FPS=30

FILTER_CROP="scale=${TARGET_W}:${TARGET_H}:force_original_aspect_ratio=increase,crop=${TARGET_W}:${TARGET_H}"
FILTER_PAD="scale=${TARGET_W}:${TARGET_H}:force_original_aspect_ratio=decrease,pad=${TARGET_W}:${TARGET_H}:(ow-iw)/2:(oh-ih)/2"

FILTER="${FILTER_CROP}"
if [[ "${MODE}" == "pad" ]]; then
  FILTER="${FILTER_PAD}"
fi

i=1
for inFile in "$@"; do
  outFile="${OUT_DIR}/opening_${i}.mp4"
  echo "Encoding (${MODE}) ${inFile} -> ${outFile}"
  ffmpeg -y -hide_banner -loglevel error \
    -i "${inFile}" \
    -an \
    -vf "${FILTER},fps=${FPS},format=yuv420p" \
    -c:v libx264 -preset slow -crf 18 \
    -movflags +faststart \
    "${outFile}"
  i=$((i+1))
done

echo "Done. Outputs are in ${OUT_DIR}/opening_1.mp4 .. opening_3.mp4"





