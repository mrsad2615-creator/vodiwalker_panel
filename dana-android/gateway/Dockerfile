FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --no-create-home --uid 10001 mrsad && mkdir /data && chown mrsad /data
COPY mrsad_gateway.py .
USER 10001
EXPOSE 8080
CMD ["uvicorn", "mrsad_gateway:create_app", "--factory", "--host", "0.0.0.0", "--port", "8080", "--workers", "1", "--limit-concurrency", "32", "--no-access-log", "--no-proxy-headers"]
