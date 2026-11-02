# Frontend Angular compilado y servido por Caddy (que además hace de proxy /api → gateway y obtiene HTTPS).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM caddy:2-alpine
COPY --from=build /app/dist/rutagt-web/browser /srv
COPY Caddyfile /etc/caddy/Caddyfile
