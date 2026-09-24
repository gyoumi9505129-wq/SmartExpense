# SmartExpense 시스템 복구 매뉴얼

**복구 버전 ID:** `stable-20260924`  
**작성일:** 2026-09-24  
**대상:** Android 앱 (`SmartExpense`) + 웹 (`SmartExpenseWeb`) + Firebase (`smartexpense-55679`)

이 문서만 따라도 **이 시점의 코드·설정·배포 상태**로 되돌릴 수 있도록 작성했습니다.  
데이터(Firestore 회원·장부 등)까지 완전 복구하려면 **§6 클라우드 데이터 백업**을 미리 해 두어야 합니다.

---

## 0. 한눈에 보기

| 구분 | 위치 |
|------|------|
| 복구 키트 루트 | `c:\AiDev\SmartExpense-Recovery\stable-20260924\` |
| 버전 메타 | `VERSION.json` |
| 소스 ZIP | `archives\SmartExpense*.zip` |
| Git Bundle (권장) | `archives\*.bundle` |
| Firebase 규칙/인덱스 사본 | `firebase\` |
| 웹 env 템플릿 | `env-templates\` |
| 앱 작업 폴더 | `c:\AiDev\SmartExpense` (태그 `stable-20260924`) |
| 웹 작업 폴더 | `c:\AiDev\SmartExpenseWeb` (태그 `stable-20260924`) |
| 운영 Hosting | https://smartexpense-55679.web.app |
| Firebase 프로젝트 | `smartexpense-55679` |

**권장 복구 순서:**  
① 시크릿/환경파일 준비 → ② 코드(태그 또는 bundle/zip) → ③ Firebase 규칙·인덱스 → ④ 웹 `.env` → ⑤ 앱 `local.properties`·서명키 → ⑥ 빌드·배포 → ⑦ (필요 시) Firestore 데이터 복원 → ⑧ 스모크 테스트

---

## 1. 이 복구 버전이 포함하는 것 / 포함하지 않는 것

### 포함 (코드·설정)

- 웹: Next.js 소스, `firebase.json`, `.firebaserc`, `.env.local.example`
- 앱: Kotlin 소스, `google-services.json`, `firestore.rules`, `firestore.indexes.json`
- Git 태그 `stable-20260924` + commit 해시 (`VERSION.json` 참고)
- ZIP / Git Bundle 아카이브

### 포함하지 않음 (별도 보관 필수)

아래가 없으면 **100% 운영 복구가 불가능**합니다. 지금 당장 안전한 곳(암호화 USB, 비밀번호 관리자, 개인 클라우드 비공개 폴더)에 복사해 두세요.

| 항목 | 원본 위치(예시) | 용도 |
|------|-----------------|------|
| 웹 `.env.local` | `SmartExpenseWeb\.env.local` | 로컬 개발 Firebase/GIS |
| 웹 `.env.production` | `SmartExpenseWeb\.env.production` | Hosting 빌드용 |
| Android `local.properties` | `SmartExpense\local.properties` | SDK 경로 (PC마다 다름, 재생성 가능) |
| 릴리스 키스토어 `*.jks` / `*.keystore` | (본인 보관) | Play/실기기 동일 서명 |
| `keystore.properties` | (본인 보관) | 서명 비밀번호 |
| Google Cloud OAuth 클라이언트 비밀 | Cloud Console | Web Client ID는 공개값이나 Console 설정 자체는 계정 소유 |
| Firestore 데이터 스냅샷 | §6 | 회원·장부·회비 등 실데이터 |

> 복구 키트의 `env-templates\` 에는 예시/백업용 env가 있을 수 있습니다.  
> **git 원격·공개 저장소에는 절대 올리지 마세요.**

---

## 2. 상황별 복구 시나리오

### A. 작업 폴더가 그대로 있고, 최근 실험만 되돌리기 (가장 빠름)

```powershell
# 웹
cd c:\AiDev\SmartExpenseWeb
git status
git checkout stable-20260924

# 앱
cd c:\AiDev\SmartExpense
git status
git checkout stable-20260924
```

그 후 §7 웹 배포, §8 앱 빌드.

### B. 폴더를 삭제했거나 git이 깨졌을 때 — Git Bundle로 복원 (권장)

```powershell
$kit = "c:\AiDev\SmartExpense-Recovery\stable-20260924\archives"

# 웹
cd c:\AiDev
Remove-Item SmartExpenseWeb -Recurse -Force -ErrorAction SilentlyContinue
git clone "$kit\SmartExpenseWeb-stable-20260924.bundle" SmartExpenseWeb
cd SmartExpenseWeb
git checkout stable-20260924

# 앱
cd c:\AiDev
Remove-Item SmartExpense -Recurse -Force -ErrorAction SilentlyContinue
git clone "$kit\SmartExpense-stable-20260924.bundle" SmartExpense
cd SmartExpense
git checkout stable-20260924
```

### C. Git 없이 ZIP만으로 복원

```powershell
$kit = "c:\AiDev\SmartExpense-Recovery\stable-20260924\archives"
cd c:\AiDev
Expand-Archive "$kit\SmartExpenseWeb-stable-20260924.zip" -DestinationPath SmartExpenseWeb -Force
Expand-Archive "$kit\SmartExpense-stable-20260924.zip" -DestinationPath SmartExpense -Force
```

ZIP에는 `.git` 이 없으므로, 이후 버전 관리가 필요하면 §B를 우선하세요.

### D. 다른 PC로 이전

1. `c:\AiDev\SmartExpense-Recovery\stable-20260924\` 폴더 전체를 복사  
2. 시크릿 목록(§1)도 함께 복사  
3. 새 PC에서 §B 또는 §C로 코드 복원  
4. Android Studio SDK 설치 후 `local.properties` 재생성  
5. §4~§8 진행  

---

## 3. 복구 버전 무결성 확인

```powershell
cd c:\AiDev\SmartExpenseWeb
git rev-parse HEAD
git describe --tags
# 기대: 1245d6b9ee0e7da3ce5b37e94d16daeb0e7aef68
# 기대 태그: stable-20260924

cd c:\AiDev\SmartExpense
git rev-parse HEAD
git describe --tags
# 기대: b755d52af3e9df9222092462bac61b0a490ca6cf
# 기대 태그: stable-20260924
```

`VERSION.json` 의 commit 과 일치해야 합니다.

---

## 4. 웹 환경 파일 복구

1. `env-templates\web.env.local.example` 을 참고해  
   `c:\AiDev\SmartExpenseWeb\.env.local` 생성  
2. 운영 배포용으로 `.env.production` 을 원본 백업에서 복원  
   (없으면 example 값을 복사한 뒤 Hosting/OAuth에 맞게 확인)

필수 변수 (example 기준):

- `NEXT_PUBLIC_FIREBASE_*` (apiKey, authDomain, projectId, …)
- `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID`

설치·검증:

```powershell
cd c:\AiDev\SmartExpenseWeb
npm ci
npx tsc --noEmit
```

---

## 5. Firebase 프로젝트·규칙·Hosting 복구

프로젝트 ID: **`smartexpense-55679`**

### 5.1 CLI 로그인

```powershell
firebase login
firebase use smartexpense-55679
```

### 5.2 Firestore 규칙·인덱스 재배포

앱 저장소의 규칙이 SSOT입니다. 복구 키트에도 사본이 있습니다.

```powershell
cd c:\AiDev\SmartExpense
firebase deploy --only firestore:rules,firestore:indexes
```

또는 키트 사본:

```powershell
cd c:\AiDev\SmartExpense-Recovery\stable-20260924\firebase
# firestore.rules / firestore.indexes.json 을 앱 루트에 복사한 뒤 위 명령 실행
```

### 5.3 Authentication / OAuth (콘솔 확인)

Firebase Console → Authentication:

- Google 로그인 사용 설정 ON  
- 승인된 도메인에 `smartexpense-55679.web.app`, `smartexpense-55679.firebaseapp.com` 포함  

Google Cloud Console → OAuth 2.0 웹 클라이언트:

- 승인된 JavaScript 원본에 `https://smartexpense-55679.web.app`  
- Client ID가 `.env` 의 `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` 와 일치  

### 5.4 Hosting 재배포 (웹)

```powershell
cd c:\AiDev\SmartExpenseWeb
# .env.production 확인 후
npm run deploy
# = next build && firebase deploy --only hosting
```

배포 후 https://smartexpense-55679.web.app 접속.

---

## 6. 클라우드 데이터(Firestore) 백업·복원

**코드만 되돌려도 “앱/웹 프로그램”은 복구됩니다.**  
**한우리 장부·회원 등 실데이터가 날아간 경우**는 Firestore 백업이 있어야 100%입니다.

### 6.1 지금 해둘 정기 백업 (권장)

Google Cloud에서 Firestore 내보내기 (Blaze 플랜·GCS 버킷 필요):

```powershell
# gcloud 설치·로그인 후
gcloud config set project smartexpense-55679

# 예: 버킷 생성(최초 1회)
# gsutil mb -p smartexpense-55679 -l asia-northeast3 gs://smartexpense-55679-firestore-backups

gcloud firestore export gs://smartexpense-55679-firestore-backups/stable-20260924
```

또는 Firebase Console → Firestore → **Import/Export**.

백업 경로를 `VERSION.json` 옆 `DATA_BACKUP_NOTES.txt` 에 적어 두세요.

### 6.2 데이터 복원

```powershell
gcloud firestore import gs://smartexpense-55679-firestore-backups/stable-20260924
```

> Import는 프로젝트를 덮어씁니다. 운영 중이면 서비스 중단 창을 잡고 진행하세요.

### 6.3 앱 내 백업(참고)

Android 앱에는 Drive/엑셀·로컬 DB 백업 기능이 있습니다.  
클라우드와 로컬이 어긋날 때는 **클라우드(올리기/내리기) 정책**을 설정 화면에서 확인한 뒤 복구하세요.  
실데이터 SSOT는 Firestore입니다.

---

## 7. 웹 완전 복구 체크리스트

- [ ] `git checkout stable-20260924` 또는 bundle/zip 복원  
- [ ] `git rev-parse HEAD` = `1245d6b9ee0e7da3ce5b37e94d16daeb0e7aef68`  
- [ ] `.env.local` / `.env.production` 복원  
- [ ] `npm ci` 성공  
- [ ] `npx tsc --noEmit` 성공  
- [ ] `npm run deploy` 성공  
- [ ] 브라우저에서 Google 로그인  
- [ ] 내 모임 → 한우리 → 장부/회비/회원/경조/결산 탭 표시  
- [ ] 모바일에서 카드 UI·총 미납액 표시 확인  

---

## 8. Android 앱 완전 복구 체크리스트

- [ ] `git checkout stable-20260924` 또는 bundle/zip 복원  
- [ ] `git rev-parse HEAD` = `b755d52af3e9df9222092462bac61b0a490ca6cf`  
- [ ] `app\google-services.json` 존재 (저장소에 포함됨)  
- [ ] Android Studio에서 프로젝트 열기 → SDK sync  
- [ ] `local.properties` 자동 생성 또는 SDK 경로 설정  
- [ ] (스토어/동일 서명 필요 시) 키스토어·`keystore.properties` 복원  
- [ ] Debug 빌드 설치: `.\gradlew.bat :app:assembleDebug`  
- [ ] Google 로그인 → 내 모임 → 한우리 진입  
- [ ] 회원·회비·장부·경조·결산 탭 동작  
- [ ] 설정에서 클라우드 동기화 상태 확인  

Release 빌드:

```powershell
cd c:\AiDev\SmartExpense
.\gradlew.bat :app:assembleRelease
```

---

## 9. 앱↔웹 연동 검증 (복구 후 필수)

동일 Firebase 프로젝트이므로 한쪽만 복구해도 데이터는 공유됩니다.

1. 웹에서 장부 1건 추가 → 앱 새로고침 후 동일 건 확인  
2. 앱에서 회비 납부 → 웹 회비·장부 반영 확인  
3. Google 계정 표시명 변경 후 재로그인 → 프로필 이름 동기화 확인  

---

## 10. 새 안정 버전을 또 만들 때

다음에 안정화 포인트를 찍을 때:

```powershell
# 예: stable-20261001
$ver = "stable-YYYYMMDD"

# 1) 양쪽 커밋 후
git tag -a $ver -m "Stable recovery point ..."

# 2) bundle
git bundle create "...\archives\...-$ver.bundle" --all

# 3) VERSION.json 갱신 + RECOVERY.md 상단 versionId 수정
# 4) Firestore export 를 같은 ID로 한 번 더
```

복구 키트 폴더를 **통째로 외장 디스크에 복사**해 두면 PC 장애에도 대비됩니다.

---

## 11. 자주 하는 실수

| 증상 | 원인 | 조치 |
|------|------|------|
| 웹 로그인 `origin_mismatch` | OAuth JS 원본 미등록 | Cloud Console에 web.app 원본 추가 |
| 웹 빌드는 되는데 로그인 안 됨 | `.env.production` Client ID 불일치 | env와 Console Client ID 대조 |
| 앱 빌드만 되고 Firebase 연결 실패 | `google-services.json` 누락/다른 프로젝트 | 이 태그의 파일로 교체 |
| 코드는 예전인데 데이터만 이상 | Firestore를 되돌리지 않음 | §6 import 또는 앱 클라우드 내리기 |
| `git checkout` 실패 | 태그 없는 복사본(ZIP만) | Bundle로 다시 clone |
| Play 업데이트 거부 | 다른 키스토어로 서명 | 원본 jks 복원 |

---

## 12. 연락·콘솔 바로가기

- Firebase Console: https://console.firebase.google.com/project/smartexpense-55679/overview  
- Hosting: https://smartexpense-55679.web.app  
- 복구 키트: `c:\AiDev\SmartExpense-Recovery\stable-20260924\`  
- 이 문서 사본:  
  - `c:\AiDev\SmartExpense-Recovery\RECOVERY.md`  
  - `c:\AiDev\SmartExpense\docs\RECOVERY.md`  
  - `c:\AiDev\SmartExpenseWeb\docs\RECOVERY.md`  

---

**끝.** 위 체크리스트를 모두 통과하면 `stable-20260924` 기준 시스템으로 복구된 것입니다.
