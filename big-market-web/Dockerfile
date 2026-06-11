FROM nginx:alpine
COPY big-market-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY big-market-web/ /usr/share/nginx/html/
RUN sed -i 's|return "http://127.0.0.1:8080/api/v1";|return "/api/v1";|' /usr/share/nginx/html/config.js
EXPOSE 80
