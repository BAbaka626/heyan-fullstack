FROM node:20-bookworm-slim

RUN apt-get update \
  && apt-get install -y --no-install-recommends antiword python3 make g++ \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app/server

COPY server/package*.json ./
RUN npm ci --omit=dev

COPY server ./ 
COPY *.doc *.docx *.xlsx /app/

RUN chmod +x /app/server/scripts/docker-entrypoint.sh

EXPOSE 3100

CMD ["./scripts/docker-entrypoint.sh"]
