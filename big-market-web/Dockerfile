FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY . /usr/share/nginx/html/
RUN sed -i 's|http://127.0.0.1:8098/api/v1|/api/v1|g' /usr/share/nginx/html/app.js \
    && sed -i 's|http://127.0.0.1:8098/api/v1|/api/v1|g' /usr/share/nginx/html/login.js
EXPOSE 80
