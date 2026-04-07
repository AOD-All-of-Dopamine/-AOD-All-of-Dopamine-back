# 스케일링 및 성능 최적화 (Scalability & Performance) 전략

## 1. 개요
백엔드 단에서 발생하는 크롤링과 API 조회 부하를 버티기 위해 파생된 성능 최적화(DB, 메모리, 시스템 수준) 노하우 모음입니다. 차후 다른 도메인이 붙더라도 해당 기준에 부합해야 합니다.

## 2. 데이터베이스 최적화 기법 (DB Optimization)
1. **N+1 문제와 다중 필터링 속도 가속화 (GIN Index)**
   - "액션 AND 판타지"와 같이 여러 조건이 맞물리는 탐색 쿼리 시, DB에서 풀 스캔이 나던 문제를 방지하기 위해 PostgreSQL 고유 기능인 GIN 인덱스와 Array Contains(`@>`) 연산자를 활용합니다. (응답시간 10초 -> 0.3초)
2. **JPA `MapsId` 충돌 회피 (`Persistable<Long>`)**
   - 부모 엔티티(Content)의 ID를 자식(Webnovel 등)이 상속받을 때, Jpa가 신규 생성임에도 불구하고 이미 ID가 있다는 이유로 `merge()`(Update)를 날려서 DB가 터지는 오류가 있습니다.
   - 모든 상속 자식 엔티티는 `Persistable<Long>`를 구현하고 `@Transient private boolean isNew = true;` 변수를 두어, 강제로 `persist()`(Insert)가 호출되게 합니다.

## 3. 메모리 누수 방지 (Memory Anti-Leak)
1. **Slice 기반 페이징**
   - 10만 건 이상의 데이터를 메모리에 한 번에 올리는 `Pageable.unpaged()`를 사용할 경우 OutOfMemoryError가 발생합니다. 필수 데이터 로드는 항상 `Slice`와 같이 chunk(청크) 단위로 분리해서 가져옵니다.
2. **Selenium WebDriver 재사용**
   - 크롬 창을 계속 새로 띄우면 100번에 메모리 8GB가 증발합니다. 크롬 프로세스는 `ThreadLocal` 단위로 최대 30회까지 재활용(Reuse) 하고 자체적으로 파기해야 합니다.

## 4. 인프라스트럭처 한계 돌파 (DevOps)
1. **Docker 컨테이너 안 좀비 프로세스 방지**
   - 리눅스의 1번 부모 프로세스(PID 1)가 Java일 경우, Docker 안에서는 죽은 크롬 자식 프로세스들을 거두어들이지 못해 서버가 하루마다 재시작해야 했습니다.
   - Dockerfile ENTRYPOINT에 `tini`라는 경량 시스템 데몬 프로세스를 띄워 고아 크롬 프로세스 자식들을 청소(Reap & Adopt)하도록 설정하여 해결했습니다.
