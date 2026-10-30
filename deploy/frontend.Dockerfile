FROM node:20-alpine AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# 断言构建产物存在：COPY --from 的源路径不存在时 BuildKit 不报错、只产出空层，
# 后端 Dockerfile 就因此漏过了一次（见 backend.Dockerfile 注释）。
RUN npm run build && test -f /src/dist/index.html

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
# 同上：静态文件没进来就让构建失败，而不是发一个返回 403 的镜像
RUN test -f /usr/share/nginx/html/index.html
EXPOSE 80
