# EvalAI — AI Powered Answer Sheet Evaluation System

<div align="center">
  <h3>Automated, Unbiased, AI-Driven Exam Evaluation Platform</h3>
  <p>A polyglot microservices system for OCR, semantic scoring, and feedback generation</p>
</div>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0.0-blue" />
  <img src="https://img.shields.io/badge/license-MIT-green" />
  <img src="https://img.shields.io/badge/Java-SpringBoot-orange" />
  <img src="https://img.shields.io/badge/C++-OpenCV-blue" />
  <img src="https://img.shields.io/badge/Python-FastAPI-green" />
  <img src="https://img.shields.io/badge/PostgreSQL-pgvector-336791" />
  <img src="https://img.shields.io/badge/AI-LLM%20+%20Embeddings-purple" />
</p>

---

## 📌 Table of Contents

- [Project Overview](#project-overview)
- [Key Features](#key-features)
- [Core Pipelines](#core-pipelines)
- [Tech Stack](#tech-stack)
- [System Architecture](#system-architecture)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Future Scope](#future-scope)
- [License](#license)

---

## 📖 Project Overview

EvalAI is an **AI-powered evaluation system** designed to automate the correction of handwritten answer sheets.

It eliminates:

- ❌ Manual evaluation delays  
- ❌ Inconsistent marking  
- ❌ Faculty bias  
- ❌ Lack of feedback  

### ✅ What it does:

- Upload student answer sheets
- Extract handwritten text using OCR
- Evaluate answers using semantic similarity
- Generate detailed AI feedback
- Provide grievance handling system

---

## 🚀 Key Features

| Feature | Description |
|--------|------------|
| 📄 Batch Upload | Upload multiple student answer sheets |
| 🔍 OCR Pipeline | Extract handwritten text using AI |
| 🧠 Semantic Evaluation | Uses embeddings + cosine similarity |
| 📊 AI Feedback | Strengths, weaknesses, suggestions |
| ⚖️ Grievance System | Faculty override & review |
| 🚫 Anonymization | Removes student identity before evaluation |
| 📡 Real-Time Updates | SSE-based progress tracking |

---

## ⚙️ Core Pipelines

### 1️⃣ OCR & Extraction Pipeline
- PDF → Images
- C++ preprocessing (denoise, deskew, anonymize)
- OCR extraction (PaddleOCR / Gemini)
- Text segmentation

### 2️⃣ Evaluation Pipeline
- Generate embeddings (384-dim vectors)
- Compare with model answers
- Compute cosine similarity
- Assign marks

### 3️⃣ Feedback Pipeline
- AI-generated:
  - Strengths
  - Weaknesses
  - Suggestions
  - Missed concepts

### 4️⃣ Grievance Pipeline
- System-generated failures
- Student-raised disputes
- Faculty override system

---

## 🧠 Evaluation Logic
- Similarity >= 0.85 → Full Marks
- Similarity >= 0.5 → Partial Marks
- Similarity < 0.5 → 0 Marks


---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|------|-----------|--------|
| Frontend | React + Vite + Tailwind | UI dashboards |
| Backend | Spring Boot (Java) | Orchestration & APIs |
| Preprocessing | C++ + OpenCV | Image processing |
| AI Engine | Python + FastAPI | OCR, embeddings, scoring |
| Database | PostgreSQL + pgvector | Storage + vector search |
| Queue | Celery + Redis | Async processing |
| Communication | REST + gRPC | Inter-service communication |
| DevOps | Docker Compose | Container orchestration |

---

## 🏗️ System Architecture
<img src="./images/HLD EvalAI.png" >

---

## 🚀 Getting Started

### 🔹 Clone Repository

```bash
git clone https://github.com/your-username/evalai.git
cd evalai
```

### Development Setup
- Docker Setup (Recommended)
```bash
    docker-compose up --build
```

### Local Setup
- Backend (Java)
```bash
    cd evalai-java
    ./mvnw spring-boot:run
```

- Python Service
```bash
    cd evalai-python
    pip install -r requirements.txt
    uvicorn app.main:app --reload
```

- C++ Service
```bash
    cd evalai-cpp
    mkdir build && cd build
    cmake ..
    make
    ./server
```

- Frontend
```bash
    cd evalai-frontend
    npm install
    npm run dev
```

## 🔑 Environment Variables
```text
    DB_HOST=localhost
    JWT_SECRET=your_secret
    REDIS_HOST=localhost
    OCR_MODE=PADDLE
    GEMINI_API_KEY=your_key
```

## 📂 Project Structure
```html
    evalai/
        ├── evalai-frontend/
        ├── evalai-java/
        ├── evalai-cpp/
        ├── evalai-python/
        ├── proto/
        ├── upload/
        ├── docker-compose.yml
```

## 🔮 Future Scope
- Multi-language OCR
- Mobile application
- Analytics dashboard
- Diagram evaluation
- LMS integration