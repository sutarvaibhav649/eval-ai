# evalai-python/Dockerfile.api
FROM python:3.11-slim

WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    libgl1 \
    libglib2.0-0 \
    curl \
    && rm -rf /var/lib/apt/lists/*

RUN pip install uv

# Dependency layer
COPY pyproject.toml uv.lock* ./
RUN uv sync --no-dev

# App code
COPY app ./app
COPY .env .

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]