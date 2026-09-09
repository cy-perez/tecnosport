# Imagen del frontend con SSR. Se construye desde la raíz del repositorio:
#   docker build -f apps/web/Dockerfile -t tecnosport-web .
# El contexto tiene que ser la raíz: el proyecto es un monorepo con workspaces de npm y la
# aplicación depende de packages/marca y packages/contratos.

FROM node:22-slim AS constructor
WORKDIR /fuente
# Primero solo los manifiestos: mientras no cambien, `npm ci` se reaprovecha de la caché aunque
# cambie el código.
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/
# packages/marca no lleva manifiesto: está declarado como workspace en el package.json de la raíz
# pero no es un paquete de npm, es el kit de marca generado. Entra con el resto del código, que es
# lo que el paso `prebuild` (copiar-marca.mjs) necesita.
COPY packages/contratos/package.json packages/contratos/
RUN npm ci
COPY . .
RUN npm run build --workspace=apps/web

FROM node:22-slim AS ejecucion
WORKDIR /app
ENV NODE_ENV=production
# Solo el resultado del build, sin node_modules: el constructor de Angular empaqueta las
# dependencias del servidor dentro del bundle — comprobado, los únicos imports que quedan fuera
# son módulos nativos de Node (`path`, `process`, `url`).
COPY --from=constructor /fuente/apps/web/dist/tecnosport-web ./dist/tecnosport-web

# La imagen de Node ya trae un usuario sin privilegios.
USER node

# Cloud Run inyecta PORT; server.ts ya lo lee y cae a 4000 fuera de la nube.
ENV PORT=8080
EXPOSE 8080

# **El despliegue tiene que definir `NG_ALLOWED_HOSTS`**, o el sitio responde 400 a cada
# petición. No es un detalle del contenedor: `security.allowedHosts` de angular.json viaja dentro
# del bundle del servidor y `@angular/ssr` rechaza cualquier `Host` que no esté en la lista —hoy
# solo tecnosport.co y www—, así que en Cloud Run, donde el servicio se alcanza por un dominio
# `*.run.app`, no respondería nada. El motivo de la comprobación es real (SSRF), por eso aquí no
# se pone un valor por omisión permisivo: se declara en cada despliegue, y en local se pasa a
# mano (`-e NG_ALLOWED_HOSTS=localhost`). Acepta comodín de subdominio: `*.run.app`.
#
# Y `NG_TRUST_PROXY_HEADERS`, porque detrás de Cloud Run toda petición llega con cabeceras
# `x-forwarded-*`: sin declararlas, `@angular/ssr` las descarta y avisa por consola en cada
# petición.

CMD ["node", "dist/tecnosport-web/server/server.mjs"]
