# EvalAI — AI Powered Answer Sheet Evaluation System

## Architecture
- **React** — Presentation Layer
- **Java Spring Boot** — Orchestration & Management Layer
- **C++ (gRPC)** — Vision & Preprocessing Layer
- **Python (FastAPI + Celery)** — Intelligence & AI Layer

## Services
| Service | Language | Port |
|---|---|---|
| evalai-frontend | React | 3000 |
| evalai-java | Spring Boot | 8080 |
| evalai-cpp | C++ gRPC | 50051 |
| evalai-python | FastAPI | 8000 |
| evalai-redis | Redis | 6379 |
| evalai-db | PostgreSQL + pgvector | 5432 |

## Quick Start
```bash
cp .env.example .env
# fill in your values
docker-compose up --build
```

## Folder Structure
- `proto/` — shared gRPC contract
- `upload/` — shared volume for PDF and image processing
- `evalai-frontend/` — React app
- `evalai-java/` — Spring Boot backend
- `evalai-cpp/` — C++ preprocessing service
- `evalai-python/` — Python AI service