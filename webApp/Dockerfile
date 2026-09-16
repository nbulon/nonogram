FROM nginx:alpine3.24
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/
