-- 신작 테스트용 더미 데이터
-- 최근 30일 이내의 릴리즈 날짜로 설정

-- GAME 도메인 신작 (최근 1주일)
INSERT INTO contents (domain, master_title, original_title, release_date, poster_image_url, synopsis, created_at, updated_at)
VALUES 
('GAME', '엘든 링: 그림자의 왕', 'Elden Ring: Shadow of the Erdtree', '2024-10-15', 
 'https://cdn.cloudflare.steamstatic.com/steam/apps/1245620/header.jpg',
 '거대한 보스전과 새로운 던전이 가득한 확장팩', NOW(), NOW()),
 
('GAME', '스타필드: 부서진 우주', 'Starfield: Shattered Space', '2024-10-18',
 'https://cdn.cloudflare.steamstatic.com/steam/apps/1716740/header.jpg',
 'SF RPG의 새로운 확장 스토리', NOW(), NOW()),

('GAME', '메타포: 리판타지오', 'Metaphor: ReFantazio', '2024-10-11',
 'https://cdn.cloudflare.steamstatic.com/steam/apps/2679460/header.jpg',
 '페르소나 팀의 신작 판타지 RPG', NOW(), NOW());

-- WEBTOON 도메인 신작 (최근 2주일)
INSERT INTO contents (domain, master_title, original_title, release_date, poster_image_url, synopsis, created_at, updated_at)
VALUES 
('WEBTOON', '나 혼자만 레벨업: 신의 귀환', '나 혼자만 레벨업: 신의 귀환', '2024-10-08',
 'https://image-comic.pstatic.net/webtoon/183559/thumbnail/thumbnail_IMAG21.jpg',
 '성진우의 새로운 모험이 시작된다', NOW(), NOW()),
 
('WEBTOON', '화산귀환 2부', '화산귀환 2부', '2024-10-14',
 'https://image-comic.pstatic.net/webtoon/236342/thumbnail/thumbnail_IMAG21.jpg',
 '최강의 검마가 돌아왔다', NOW(), NOW()),
 
('WEBTOON', '전지적 독자 시점: 외전', '전지적 독자 시점: 외전', '2024-10-20',
 'https://image-comic.pstatic.net/webtoon/119874/thumbnail/thumbnail_IMAG21.jpg',
 '김독자의 새로운 이야기', NOW(), NOW());

-- WEBNOVEL 도메인 신작 (최근 1개월)
INSERT INTO contents (domain, master_title, original_title, release_date, poster_image_url, synopsis, created_at, updated_at)
VALUES 
('WEBNOVEL', '마법천자문: 귀환', '마법천자문: 귀환', '2024-09-25',
 'https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg',
 '최강의 마법사가 과거로 돌아간다', NOW(), NOW()),
 
('WEBNOVEL', '전생했더니 슬라임이었던 건에 대하여 20권', '転生したらスライムだった件', '2024-10-01',
 'https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg',
 '리무루의 새로운 모험', NOW(), NOW()),
 
('WEBNOVEL', '던전 디펜스: 재림', '던전 디펜스: 재림', '2024-10-10',
 'https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg',
 '마왕이 된 주인공의 복수극', NOW(), NOW()),
 
('WEBNOVEL', '나는 대마법사다', '나는 대마법사다', '2024-10-17',
 'https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg',
 '현대에서 마법을 펼치다', NOW(), NOW());

-- AV 도메인 신작 (최근 1개월)
INSERT INTO contents (domain, master_title, original_title, release_date, poster_image_url, synopsis, created_at, updated_at)
VALUES 
('AV', '듄: 파트 2', 'Dune: Part Two', '2024-10-05',
 'https://m.media-amazon.com/images/M/MV5BN2QyZGU4ZDctOWMzMy00NTc5LThlOGQtODhmNDI1NmY5YzAwXkEyXkFqcGdeQXVyMDM2NDM2MQ@@._V1_.jpg',
 '폴 아트레이데스의 장대한 여정이 계속된다', NOW(), NOW()),
 
('AV', '데드풀과 울버린', 'Deadpool & Wolverine', '2024-09-28',
 'https://m.media-amazon.com/images/M/MV5BNzRiMjg0MzUtNTQ1Mi00Y2Q5LWEwM2MtMzUwZDU5NmVjN2NkXkEyXkFqcGdeQXVyMTEzMTI1Mjk3._V1_.jpg',
 'MCU 최초의 데드풀과 울버린의 만남', NOW(), NOW()),
 
('AV', '베놈: 라스트 댄스', 'Venom: The Last Dance', '2024-10-12',
 'https://m.media-amazon.com/images/M/MV5BZDMyYWU4NzItZDY0MC00ODE2LTkyYTMtMzNkNDdmYmFhZDg0XkEyXkFqcGdeQXVyMTEyNzgwMDUw._V1_.jpg',
 '베놈의 마지막 모험', NOW(), NOW()),
 
('AV', '조커: 폴리 아 되', 'Joker: Folie à Deux', '2024-10-19',
 'https://m.media-amazon.com/images/M/MV5BYjZlMTA5ZGYtODVmNC00NDJmLWE2MWItZmQ3OTJlMDE2OTQ5XkEyXkFqcGdeQXVyMTUzMTg2ODkz._V1_.jpg',
 '조커와 할리 퀸의 광기 어린 로맨스', NOW(), NOW());

-- 과거 작품 (비교용 - 신작이 아닌 것들)
INSERT INTO contents (domain, master_title, original_title, release_date, poster_image_url, synopsis, created_at, updated_at)
VALUES 
('GAME', '젤다의 전설: 왕국의 눈물', 'The Legend of Zelda: Tears of the Kingdom', '2023-05-12',
 'https://cdn.cloudflare.steamstatic.com/steam/apps/1245620/header.jpg',
 '링크의 새로운 모험', NOW(), NOW()),
 
('WEBTOON', '신의 탑', '신의 탑', '2010-06-30',
 'https://image-comic.pstatic.net/webtoon/20274/thumbnail/thumbnail_IMAG21.jpg',
 '탑을 오르는 소년 밤의 이야기', NOW(), NOW()),
 
('WEBNOVEL', '달빛조각사', '달빛조각사', '2007-01-01',
 'https://image.aladin.co.kr/product/33000/12/cover500/k052932636_1.jpg',
 '최고의 가상현실 게임 소설', NOW(), NOW()),
 
('AV', '인터스텔라', 'Interstellar', '2014-11-07',
 'https://m.media-amazon.com/images/M/MV5BZjdkOTU3MDktN2IxOS00OGEyLWFmMjktY2FiMmZkNWIyODZiXkEyXkFqcGdeQXVyMTMxODk2OTU@._V1_.jpg',
 '크리스토퍼 놀란의 SF 걸작', NOW(), NOW());
