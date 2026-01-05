#!/bin/bash
# 성능 측정 API 테스트 스크립트 (Bash)

BASE_URL="http://localhost:8080"

echo "🔬 성능 측정 테스트 시작"
echo ""

# 1. 비교 테스트 (가장 간단)
echo "📊 비교 테스트 실행 중..."
curl -X POST "$BASE_URL/api/performance/test/compare?beforeBatchSize=100&afterBatchSize=500&iterations=5" \
  -H "Content-Type: application/json" \
  -w "\n" | jq .

echo ""
echo "✅ 테스트 완료!"
echo ""
echo "💡 팁: 콘솔 로그에서 더 자세한 결과를 확인하세요."
