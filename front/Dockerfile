# Web portal: built by Nuxt into static files, served by Nginx (which also routes the API calls).
#   docker build -f front/Dockerfile .      (context: the repository root, for the Nginx configuration)
FROM node:24.21.0-alpine AS build
WORKDIR /app
COPY front/package.json front/package-lock.json ./
ENV CYPRESS_INSTALL_BINARY=0 PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
RUN npm ci --ignore-scripts && npx nuxt prepare
COPY front/ .
RUN npx nuxt generate

FROM nginx:1.31.7-alpine
COPY --from=build /app/.output/public /usr/share/nginx/html
COPY infra/nginx/conf.d/fleetpilot.conf /etc/nginx/conf.d/default.conf
