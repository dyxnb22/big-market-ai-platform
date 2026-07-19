FROM nginx:1.27.5-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10
ARG APP_VERSION=dev
COPY big-market-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY big-market-web/ /usr/share/nginx/html/
RUN sed -i 's|return "http://127.0.0.1:8080/api/v1";|return "/api/v1";|' /usr/share/nginx/html/config.js \
    && if [ "$APP_VERSION" = "dev" ]; then APP_VERSION="$(date +%s)"; fi \
    && find /usr/share/nginx/html -name '*.html' -type f -exec sed -i "s|?v=__APP_VERSION__|?v=${APP_VERSION}|g" {} \;
EXPOSE 80
