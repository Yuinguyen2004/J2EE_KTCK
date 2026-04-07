FROM node:22-alpine AS build

WORKDIR /app

COPY client/package.json client/package-lock.json ./

RUN npm ci

COPY client/ ./

RUN npm run build

FROM nginx:1.27-alpine

ENV BACKEND_UPSTREAM=http://server:8080

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
