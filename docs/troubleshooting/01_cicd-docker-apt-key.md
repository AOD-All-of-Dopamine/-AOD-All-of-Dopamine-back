# 01. CI/CD Docker 빌드 실패 — `apt-key: not found`

- **날짜**: 2026-06-05
- **영향**: GitHub Actions 빌드 단계에서 api·crawler Docker 이미지 빌드 실패 → 배포 중단
- **상태**: ✅ 수정·배포됨 (commit `07de3fb`)

## 증상
```
/bin/sh: 1: apt-key: not found
ERROR: ... exit code: 127
#20 [build 11/11] RUN ./gradlew ... bootJar  →  CANCELED
```
Gradle 빌드(#20)가 CANCELED로 찍혀 Gradle 문제처럼 보였지만, **실제 실패는 런타임 스테이지의 Chrome 설치 단계**였다. (BuildKit이 병렬 스테이지 중 하나가 실패하자 나머지를 취소한 것.)

## 원인
런타임 베이스 이미지 `eclipse-temurin:17-jre`가 **Ubuntu 24.04(Noble)** 기반으로 바뀌었고, Noble에서는 **`apt-key`가 제거**되었다. Chrome 저장소 키를 `apt-key add -`로 등록하던 명령이 "command not found"(127)로 죽음.

## 이전 (Before)
`-AOD-All-of-Dopamine-{api,crawler}/Dockerfile`:
```dockerfile
&& wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
&& echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
```

## 이후 (After)
```dockerfile
&& wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
&& echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
```

## 왜 이렇게 고쳤나
- `apt-key`는 deprecated(20.04)→removed(24.04). 대체 표준은 **`signed-by` 키링 방식**: 키를 `/usr/share/keyrings/`에 dearmor로 저장하고 sources 항목에서 `signed-by=`로 명시.
- `>>` → `>`로 바꿔 캐시 없이 재빌드해도 sources 줄이 중복 누적되지 않게 함.

## 개선 효과
- 빌드 통과(exit 0) → 배포 재개.

## 후속/참고
- api 이미지에도 Chrome이 설치되는데, Selenium 크롤링은 crawler에서만 돌므로 **api Dockerfile의 Chrome 블록은 제거 가능**(이미지 용량/빌드 시간 절감). 미적용.
