# Two stages, the same reasoning as backend/Dockerfile: the build stage needs Node and the
# entire npm dependency tree (dev dependencies included, for the TypeScript compiler and Vite
# itself); none of that belongs in the image that actually serves the app. The output of
# `vite build` is static HTML/CSS/JS with no server-side runtime at all, so the runtime stage
# doesn't need Node either - a plain static file server is both smaller and correct.

FROM node:22-alpine AS build
WORKDIR /build
# package.json + package-lock.json copied and installed before the rest of the source, for the
# same Docker-layer-caching reason as backend/Dockerfile's pom.xml-first step: this layer is
# only invalidated when a dependency actually changes, not on every source edit.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# VITE_API_BASE_URL is baked into the static bundle at build time (Vite inlines import.meta.env
# values during the build, not at container start) - see docker-compose.yml for the value used
# there. A real deployment fronted by a CDN would rebuild the image per environment for this
# reason, the same way any compile-time-configured static site does.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
