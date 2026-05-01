# Memory Graph Android

연락처 기반 인간관계 그래프 앱. LLM을 사용하지 않습니다.

## What It Does

- `READ_CONTACTS`로 연락처를 한 명씩 또는 전체 가져오기
- 사람을 노드로 시각화 (친밀도가 높을수록 노드가 커지고 색이 따뜻해짐)
- **연결 모드**: 노드를 손가락으로 끌어 다른 노드에 놓으면 두 사람 사이의 친밀도가 +1
- 친밀도(1~10)에 따라 엣지 굵기와 색상 변화
- 사람 노드 탭 → 이름/그룹/태그/메모 편집, 관계 강도 ± 조절, 라벨 지정
- **MD 내보내기**: 모든 사람과 관계를 마크다운으로 저장 (에이전트 입력용)
- SQLite 저장:
  - `persons(id, name, phone, group_name, notes, tags)`
  - `relations(id, person_a, person_b, strength, label, updated_at)` — 무방향, 정규화된 (a < b) 키

## Build

```bash
/tmp/gradle-8.7/bin/gradle assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Run

```bash
/home/taehyeon/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Memory Graph**를 열고:

1. 우상단 `⚙️ 관리 → 샘플 데이터 추가`로 빠르게 체험하거나
2. `👤 연락처에서` 또는 `📇 전체 가져오기`로 실제 연락처를 불러옵니다
3. 우상단 🔗 버튼을 눌러 **연결 모드**를 켜고, 노드를 끌어 다른 노드 위에 놓으면 친밀도 +1
4. 노드 탭 → 메모/태그 입력 (에이전트가 읽을 단서)
5. 우상단 `⤓ MD`로 SAF 파일 다이얼로그를 띄워 `.md` 저장

## Markdown 출력 형식

```markdown
# 인간관계 그래프
- 생성: 2026-05-01 14:32
- 사람 4명, 관계 4개

## 사람
### 민수
- id: 1
- 그룹: 친구
- 전화: 010-1111-2222
- 친밀도 합산: 4
- 태그: 대학, 동기
- 관계:
  - 지연 — 강도 3
  - 엄마 — 강도 1
- 메모:
  > 대학 동기, 자취방 근처
...
```
