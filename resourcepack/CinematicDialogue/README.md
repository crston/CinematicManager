# CinematicManager Dialogue HUD Resource Pack

BetterHud **플러그인**이 아니라, BetterHud와 같은 **리소스팩 HUD 방식**입니다.

## 원리
1. 보스바 게이지 텍스처 = 완전 투명 (캔버스만 사용)
2. 대화창 그림 = 비트맵 폰트 글리프 `\uE000`
3. 음수 스페이스로 커서를 되감아 **그림 안에 텍스트**를 겹침
4. 플러그인이 보스바 제목에 이 HUD 문자열을 넣음

## 위치
- `resourcepack/CinematicDialogue/`
- `resourcepack/CinematicDialogue.zip`
- `CinematicDialogue-ResourcePack.zip` (루트)
- 플러그인 시작 시 `plugins/CinematicManager/resourcepack/` 에도 복사

## 적용
1. zip을 서버 `resource-pack=` 또는 클라이언트 리소스팩에 넣기
2. `config.yml`: `dialogue.display-mode: hud`
