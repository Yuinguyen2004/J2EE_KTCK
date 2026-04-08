FROM node:22-alpine AS build

WORKDIR /app

COPY client/package.json client/package-lock.json ./

RUN npm ci

COPY client/ ./

RUN npm run build

FROM nginx:1.27-alpine

ENV BACKEND_UPSTREAM=http://server:8080

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/runtime-config.js.template /opt/runtime-config.js.template
COPY nginx/30-render-runtime-config.sh /docker-entrypoint.d/30-render-runtime-config.sh
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
