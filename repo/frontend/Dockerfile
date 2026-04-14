FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install --legacy-peer-deps
COPY . .
RUN npm run build

FROM node:20-alpine AS test
WORKDIR /app
RUN apk add --no-cache chromium
ENV CHROME_BIN=/usr/bin/chromium
COPY package.json ./
RUN npm install --legacy-peer-deps
COPY . .

FROM mcr.microsoft.com/playwright:v1.52.0-jammy AS e2e
WORKDIR /app
COPY package.json ./
RUN npm install --legacy-peer-deps
COPY . .

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist/city-bus-platform/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
