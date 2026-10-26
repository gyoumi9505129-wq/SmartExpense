# SmartExpense 시스템 복구 매뉴얼

**복구 버전 ID:** `stable-20261026`  
**작성일:** 2026-10-26  
**대상:** Android 앱 (`SmartExpense`) + 웹 (`SmartExpenseWeb`) + Firebase (`smartexpense-55679`)  
**용도:** 전면 개편 직전 스냅샷 — 언제든지 이 버전으로 되돌리기

이 문서만 따라도 **이 시점의 코드·설정**으로 되돌릴 수 있습니다.  
Firestore 데이터까지 완전 복구하려면 §6 / `DATA_BACKUP_NOTES.txt` 의 Firestore 백업이 필요합니다.

---

## 0. 한눈에 보기

| 구분 | 위치 |
|------|------|
| 복구 키트 루트 | `c:\AiDev\SmartExpense-Recovery\stable-20261026\` |
| 버전 메타 | `VERSION.json` |
| 소스 ZIP | `archives\SmartExpense*.zip` |
| Git Bundle (권장) | `archives\*.bundle` |
| Firebase 규칙/인덱스 사본 | `firebase\` |
| 웹 env | `env-templates\` |
| 앱 작업 폴더 | `c:\AiDev\SmartExpense` (태그 `stable-20261026`) |
| 웹 작업 폴더 | `c:\AiDev\SmartExpenseWeb` (태그 `stable-20261026`) |
| 운영 Hosting | https://smartexpense-55679.web.app |
| Firebase 프로젝트 | `smartexpense-55679` |

**기대 커밋**

| 저장소 | 태그 | commit |
|--------|------|--------|
| 앱 | `stable-20261026` | `a8fa19169720e477dbc0684ffbc3991ab7d6b4b8` |
| 웹 | `stable-20261026` | `8a87058f329cb5c09f0dfae5c9b5c93bf6c24585` |

**권장 복구 순서:**  
① 시크릿/환경파일 → ② 코드(tag/bundle/zip) → ③ Firestore 규칙·인덱스 → ④ 웹 `.env` → ⑤ 앱 `local.properties`·서명키 → ⑥ 빌드·배포 → ⑦ (필요 시) Firestore 데이터 복원

---

## 1. 포함 / 미포함

### 포함
- 앱·웹 소스 (tag / bundle / zip)
- `firestore.rules`, `firestore.indexes.json`
- 웹 `.env` 예제 + 키트 내부 BACKUP 사본

### 미포함 (별도 보관)
- Firestore 회원·장부 실데이터
- Android keystore / `keystore.properties`
- Play/서명 비밀번호

---

## 2. 코드 복구

### A. 작업 폴더가 있고 git만 되돌리기 (가장 빠름)

```powershell
cd c:\AiDev\SmartExpenseWeb
git fetch origin tag stable-20261026
git checkout stable-20261026

cd c:\AiDev\SmartExpense
git fetch origin tag stable-20261026
git checkout stable-20261026
```

개편 브랜치에서 벗어나 태그로 고정됩니다. 다시 작업할 때는 `main` 등으로 checkout.

### B. Git Bundle로 폴더 재생성

```powershell
$kit = "c:\AiDev\SmartExpense-Recovery\stable-20261026\archives"

cd c:\AiDev
Remove-Item SmartExpenseWeb -Recurse -Force -ErrorAction SilentlyContinue
git clone "$kit\SmartExpenseWeb-stable-20261026.bundle" SmartExpenseWeb
cd SmartExpenseWeb
git checkout stable-20261026

cd c:\AiDev
Remove-Item SmartExpense -Recurse -Force -ErrorAction SilentlyContinue
git clone "$kit\SmartExpense-stable-20261026.bundle" SmartExpense
cd SmartExpense
git checkout stable-20261026
```

### C. ZIP만으로 복원 (`.git` 없음)

```powershell
$kit = "c:\AiDev\SmartExpense-Recovery\stable-20261026\archives"
cd c:\AiDev
Expand-Archive "$kit\SmartExpenseWeb-stable-20261026.zip" -DestinationPath SmartExpenseWeb -Force
Expand-Archive "$kit\SmartExpense-stable-20261026.zip" -DestinationPath SmartExpense -Force
```

### D. GitHub 원격에서 태그 checkout

```powershell
cd c:\AiDev\SmartExpense
git fetch origin
git checkout stable-20261026

cd c:\AiDev\SmartExpenseWeb
git fetch origin
git checkout stable-20261026
```

---

## 3. 무결성 확인

```powershell
cd c:\AiDev\SmartExpenseWeb
git rev-parse HEAD
git describe --tags --exact-match
# 기대: 8a87058f329cb5c09f0dfae5c9b5c93bf6c24585 / stable-20261026

cd c:\AiDev\SmartExpense
git rev-parse HEAD
git describe --tags --exact-match
# 기대: a8fa19169720e477dbc0684ffbc3991ab7d6b4b8 / stable-20261026
```

`VERSION.json` 과 일치해야 합니다.

---

## 4. 웹 환경 파일

1. `env-templates\web.env.local.BACKUP-DO-NOT-COMMIT` → `c:\AiDev\SmartExpenseWeb\.env.local`
2. `env-templates\web.env.production.BACKUP-DO-NOT-COMMIT` → `.env.production`  
   (없거나 만료 시 `web.env.local.example` 참고)

```powershell
cd c:\AiDev\SmartExpenseWeb
npm ci
npx tsc --noEmit
```

---

## 5. Firebase 규칙

```powershell
cd c:\AiDev\SmartExpense
firebase login
firebase use smartexpense-55679
firebase deploy --only firestore:rules,firestore:indexes
```

키트 사본: `stable-20261026\firebase\`

---

## 6. Firestore 데이터

코드만 되돌려도 **클라우드에 이미 있는 데이터는 그대로**입니다.  
개편 중 데이터를 망가뜨린 경우에만 export/import가 필요합니다 → `DATA_BACKUP_NOTES.txt`.

---

## 7. 웹 배포 / 앱 빌드

```powershell
# 웹
cd c:\AiDev\SmartExpenseWeb
npm run build
firebase deploy --only hosting

# 앱
cd c:\AiDev\SmartExpense
.\gradlew.bat :app:assembleDebug
```

---

## 8. 스모크 테스트 (이 버전 기준)

- [ ] 시스템관리자(`gyoumi9505129@gmail.com`) 로그인 → 한우리 자동 입장 → 관리/편집 가능
- [ ] 공용(`hanuri20011001@gmail.com`) 로그인 → 승인 없이 한우리 **조회만** (샘플 아님)
- [ ] 일반 회원 미승인 → 샘플 + 승인 요청
- [ ] 일반 회원 승인 후 → 실데이터 조회
- [ ] 웹 Hosting에서도 위 권한과 동일

---

## 9. 이전 스냅샷

| ID | 용도 |
|----|------|
| `stable-20260924` | 1차 안정화 복구 포인트 |
| `stable-20261026` | **현재(개편 직전) — 권장 복구 지점** |
