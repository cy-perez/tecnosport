# TecnoSport

Tienda en línea de TecnoSport: ropa y calzado deportivo, bolsos y celulares.
Venta al detal con pago en línea y contraentrega, envío a todo Colombia.
Dominio: tecnosport.co

Monorepo. Backend en Java 21 con Spring Boot 4.1, frontend en Angular 22.5 con
SSR, base de datos PostgreSQL 16, despliegue en Google Cloud Platform.

## Estructura

```
apps/api          Backend. Gradle multi-módulo, arquitectura por capas.
apps/web          Frontend. Angular con SSR, PWA e internacionalización.
packages/marca    Kit de marca: tokens, fuentes y logos. Generado, no se edita.
packages/contratos Cliente TypeScript generado desde el OpenAPI del backend.
infra             Terraform de la infraestructura en GCP.
tools             Scripts de apoyo, en Node.
docs              Documentación del proyecto. Empieza por docs/README.md.
.claude           Configuración de Claude Code: comandos, agentes y permisos.
```

## Requisitos

- JDK 21
- Node 22 LTS y npm 10
- Docker Desktop
- Git

El proyecto se desarrolla en Windows. Los scripts propios son de Node o de
Gradle, nunca de Bash.

## Puesta en marcha

```
git clone <repo> && cd tecnosport
copy .env.example .env.local
docker compose up -d
npm install
```

Backend, en una terminal:

```
cd apps/api
gradlew.bat bootRun
```

Frontend, en otra:

```
npm run dev --workspace=apps/web
```

- Sitio: http://localhost:4200
- API: http://localhost:8080/api/v1
- Documentación de la API: http://localhost:8080/api/docs
- Correos de prueba (Mailpit): http://localhost:8025
- Base de datos (Adminer): http://localhost:8081

## Comandos frecuentes

| Comando | Qué hace |
|---|---|
| `npm run verificar` | Lint, pruebas y build de todo el monorepo |
| `npm run dev --workspace=apps/web` | Frontend en modo desarrollo |
| `npm test --workspace=apps/web` | Vitest |
| `gradlew.bat build` (en `apps/api`) | Compila, prueba y valida la arquitectura |
| `gradlew.bat spotlessApply` | Formatea el código Java |
| `npm run contratos` | Regenera el cliente TypeScript desde el OpenAPI |
| `docker compose up -d` | PostgreSQL, Mailpit y Adminer |

## Documentación

`docs/README.md` es el índice. Antes de tocar código, lee `docs/00-producto.md`
y `docs/01-arquitectura.md`.

## Licencias de terceros

Las tipografías de `packages/marca/fuentes` son SIL OFL 1.1 y sus licencias se
distribuyen con los archivos. Es condición de la licencia.
