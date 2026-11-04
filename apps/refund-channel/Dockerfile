FROM python:3.12-alpine
WORKDIR /app
RUN adduser -D -u 10001 channel && mkdir /data && chown channel:channel /data
COPY server.py .
USER channel
EXPOSE 8090
HEALTHCHECK --interval=5s --timeout=3s --retries=10 CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/health',timeout=2)"
CMD ["python", "-u", "server.py"]
