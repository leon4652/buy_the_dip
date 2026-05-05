# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

사용자는 한국어로 질의하며, 모든 응답은 한국어로 작성한다. 코드, 명령어, 기술 용어, 식별자는 영어를 유지하되 설명·주석·UI 라벨은 한국어로 한다.

## Project Overview

**바이더딥 (Buy The Dip)** — 개인용 주식 눌림 감시 대시보드. 사용자가 관심 종목을 등록하면 일봉 데이터 기반으로 눌림점수(0–100)와 상태를 계산해 보여준다. 매수 추천이나 자동 매매 서비스가 아니다.

**Current state:** 문서만 존재. 소스 코드 없음. 루트에 빈 `be/`, `fe/` 디렉토리가 있으나 **설계 문서 기준 정식 이름은 `backend/`, `frontend/`** 이므로 프로젝트 생성 시 이 이름으로 통일한다.

## Source-of-Truth Document

**`1. 프로젝트 설계 및 아이디어.md` (v2) 가 단일 기준 문서이다.** 문서 0과 2는 v2와 동기화되어 있다. 향후 결정사항이 갈릴 때는 v2를 먼저 갱신하고 문서 0, 2 및 CLAUDE.md를 그에 맞춘다. 핵심 합의 사항:

- API 식별자: **id 기반** (`{stockId}`, `{watchlistId}`) — symbol 기반 아님
- `watchlist`는 정규화 (`stock_id` FK 한 개만 — symbol/name 중복 저장 금지)
- `daily_price` / `pullback_score`도 `stock_id` FK 사용
- 점수 < 35점도 강제 추세 훼손 조건이 없으면 `PRE_OBSERVATION`(관찰 전)
- 서비스 표시명: **바이더딥**

## Architecture

Backend/Frontend 분리. Next.js 단일 앱이나 FastAPI Backend로 통합하지 않는다.

```
User → React Frontend (localhost:5173)
         ↓ REST API (JSON)
       Spring Boot Backend (localhost:8080)
         ↓
       PostgreSQL
         ↓
       External Stock Data API (MVP 이후)
```

## Build & Run Commands

### Backend (Spring Boot + Gradle)

```bash
cd backend
./gradlew bootRun                                              # 개발 서버 실행 (8080)
./gradlew build                                                # 전체 빌드
./gradlew test                                                 # 전체 테스트
./gradlew test --tests "com.buythedip.backend.pullback.*"      # 단일 패키지 테스트
./gradlew test --tests "*PullbackScoreCalculatorTest"          # 단일 클래스 테스트
```

### Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev      # 개발 서버 (5173)
npm run build    # 프로덕션 빌드
npm run lint     # ESLint
```

### Environment

```
# frontend/.env.local
VITE_API_BASE_URL=http://localhost:8080
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA, Gradle, JUnit |
| Frontend | React, TypeScript, Vite, Tailwind CSS, Recharts 또는 Lightweight Charts |
| Database | PostgreSQL (로컬 또는 Supabase — Supabase 고유 기능에 의존하지 않음) |

**초기 MVP에 추가하지 않는다:** Spring Security, Redis, Kafka, WebSocket, OAuth, Spring Batch, 마이크로서비스 구조.

## Backend Package Structure

`com.buythedip.backend` 루트, feature-based 패키지:

```
common/         config, exception, response
watchlist/      controller, service, dto, entity, repository
stock/          controller, service, dto, entity, repository, client
price/          service, dto, entity, repository
indicator/      service, dto
pullback/       controller, service, dto, entity, repository, domain
```

**계층 책임:**
- `Controller`: HTTP 요청·응답 매핑만. 비즈니스 로직 금지
- `Service`: 유스케이스, 트랜잭션
- `Domain Logic` (`pullback/domain`, `indicator/`): 모든 계산·상태 분류 — 테스트 가능하게 순수 함수로 분리
- `Repository`: DB 접근만
- `client`: 외부 API 호출
- Entity를 API 응답으로 직접 반환하지 않는다 — 항상 DTO 변환

## Frontend Structure

```
frontend/src/
  api/          Backend API 호출 함수
  components/   재사용 UI 컴포넌트
  pages/        화면 단위 컴포넌트 (대시보드, 종목 상세)
  routes/       라우팅 설정
  types/        TypeScript 타입
  utils/        화면 보조 함수
  styles/       전역 스타일
```

**핵심 제약:** 눌림점수, RSI, 이동평균, 상태 분류는 Backend에서만 계산한다. Frontend는 API 응답을 표시한다.

## Database Schema

| Table | Key Columns | Unique |
|-------|-------------|--------|
| `stock` | symbol, name, market, currency, exchange, data_source | `(market, symbol)` |
| `watchlist` | stock_id (FK) | `(stock_id)` |
| `daily_price` | stock_id (FK), trade_date, open, high, low, close, volume | `(stock_id, trade_date)` |
| `pullback_score` | stock_id (FK), trade_date, score_total, score_trend, score_drawdown, score_ma_distance, score_volume, score_rebound, status, summary, calculated_at | `(stock_id, trade_date)` |

## Pullback Score (Backend 전용 계산)

100점 만점, 5개 컴포넌트:

| Component | Max | 비고 |
|-----------|-----|------|
| 추세 유지 (trend) | 35 | close>MA20, MA20>MA60, MA60 기울기 양수, 최근 저점 상승 |
| 조정 깊이 (drawdown) | 25 | 최근 60거래일 고점 대비 하락률 (양수 표기). 3~8% 구간 만점 |
| 이동평균 근접 (ma distance) | 15 | MA20 ±3% (8점) + MA60 ±5% (7점) |
| 거래량 안정 (volume) | 15 | 20일 평균 거래량 기준. 급증 임계값 = 평균의 **1.8배 이상** |
| 반등 확인 (rebound) | 10 | 전일고가 돌파, MA5 회복, RSI 상승, 양봉 |

**필수 지표:** MA5, MA20, MA60, RSI14, recentHigh60, drawdownFrom60dHigh, distanceFromMa20, distanceFromMa60, averageVolume20.

### Status Classification (이 우선순위 순서로 판정)

| Enum | 한국어 라벨 | 조건 |
|------|------------|------|
| `DATA_INSUFFICIENT` | 데이터 부족 | 일봉 데이터 < 60거래일 |
| `TREND_BROKEN` | 추세 훼손 | 강제 조건 충족 (아래) |
| `BUY_REVIEW` | 매수 검토 | 총점 ≥ 80 **AND** 반등점수 ≥ 5 |
| `REBOUND_NEEDED` | 반등 확인 필요 | 총점 ≥ 80 & 반등점수 < 5, 또는 50 ≤ 총점 < 65 |
| `PULLBACK_ENTRY` | 눌림 진입 | 65 ≤ 총점 < 80 |
| `PRE_OBSERVATION` | 관찰 전 | 총점 < 50 (강제 조건 미충족 시 35 미만도 포함) |

**강제 추세 훼손 조건** (하나라도 충족 시 `TREND_BROKEN`, 점수 무관):
1. 종가 < MA60 × 0.92
2. drawdown > 25%
3. MA20 < MA60
4. 최근 5일 중 2일 이상 거래량 급증(≥1.8×) + 장대음봉
5. 직전 스윙 저점을 종가 기준으로 이탈

## REST API

```
GET    /api/health
GET    /api/watchlist
POST   /api/watchlist                            body: { stockId } 또는 { symbol, market }
DELETE /api/watchlist/{watchlistId}
GET    /api/stocks/{stockId}
GET    /api/stocks/{stockId}/prices
GET    /api/stocks/{stockId}/scores/latest
POST   /api/stocks/{stockId}/refresh
POST   /api/scores/refresh
GET    /api/stocks/search?keyword={keyword}      (선택)
```

## Terminology Rules

| 사용 | 사용 금지 |
|------|----------|
| 최신 종가 / `latestClose` | 현재가 (실시간 시세 아님) |
| drawdown은 양수로 표기 (예: 12.5) | 음수 표기 |
| 데이터 기준일 / `tradeDate` | 오늘 / 현재일 |

**투자 표현 규칙:** "매수하세요", "지금 사라", "수익 가능성이 높다" 같은 추천성 표현 금지. "매수 검토 구간", "관찰 필요", "반등 확인 필요", "추세 훼손 가능성", "보조 지표" 사용.

모든 화면에 다음 안내 문구 표시:
> 이 서비스는 관심 종목의 가격 위치와 기술적 지표를 분석하는 보조 도구입니다. 매수, 매도 추천이 아니며 최종 투자 판단은 사용자 본인에게 있습니다.

## Non-Functional Requirements

- **결정성:** 동일 입력 일봉 데이터 → 항상 동일 점수·상태. 계산은 순수 함수로 분리하고 단위 테스트로 보장.
- **신뢰성:** 외부 데이터 API 실패 시 마지막 저장 데이터로 응답하고, `tradeDate`를 화면에 명시하여 데이터 신선도를 사용자에게 노출.
- **단순성:** MVP에서 추상화·DDD 과적용 금지. 동작하는 작은 단위 우선.
- **단일 사용자 가정:** 초기 MVP는 단일 사용자 기준. watchlist에 user_id를 두지 않는다.

## MVP Phased Plan

| 단계 | 목표 |
|------|------|
| 1단계 | backend·frontend 프로젝트 생성, health API, Frontend가 health 호출 확인 |
| 2단계 | stock·watchlist 테이블, watchlist CRUD API, Frontend 대시보드 골격 |
| 3단계 | daily_price + Mock 일봉 데이터, MA/RSI/drawdown 계산, 눌림점수·상태 분류 |
| 4단계 | pullback_score 저장, 종목 상세 화면, 해석 문구, 데이터 기준일 표시 |
| MVP 이후 | 외부 데이터 API 연동, 스케줄러, 로그인, 알림, 백테스트 |

**Mock 데이터 우선:** 외부 주식 API 연동은 1~4단계 흐름이 모두 동작한 뒤에 진행한다. 인증/한도/포맷 변경 이슈로 계산 검증이 막히는 것을 방지.

## Forbidden Changes (AI 작업 금지)

1. Next.js 단일 앱 구조로 전환하지 않는다
2. FastAPI / Python 메인 Backend로 변경하지 않는다
3. 계산 로직을 Frontend로 옮기지 않는다
4. Controller에 비즈니스 로직 / 계산 로직을 넣지 않는다
5. Entity를 API 응답으로 직접 반환하지 않는다
6. 초기 MVP에 Spring Security / Redis / Kafka 도입하지 않는다
7. 자동 매매·실시간 시세·AI 추천 기능을 추가하지 않는다

## Design Documents

상세 명세는 프로젝트 루트의 한국어 문서를 참조:
- `0. for claude init guideline.md` — AI 구현 지시서 (간단 요약)
- `1. 프로젝트 설계 및 아이디어.md` — **단일 기준 문서 (v2)**
- `2. 아키텍쳐 및 응용프로그램 구성.md` — 아키텍처 보충
