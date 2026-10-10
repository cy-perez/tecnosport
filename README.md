# TecnoSport

Tienda en línea de TecnoSport: ropa casual y deportiva, calzado, bolsos y
tecnología. Venta al detal con pago en línea y contraentrega, envío a todo
Colombia.
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

### Ingesta de proveedores por WhatsApp

Los proveedores de bolsos y ropa publican su catálogo en un chat; la tienda lo
lee de la exportación (`docs/adr/0067`). Para probarlo en local:

1. Crear el bucket privado, una sola vez: `node infra/local/bucket-imagenes.mjs`
   crea también `tecnosport-local-proveedores`.
2. Poner `ANTHROPIC_API_KEY` en `.env.local` si se quiere la extracción real.
   Sin ella todo entra en revisión con la alerta de confianza baja, y el flujo se
   prueba igual.
3. En el panel, **Proveedores › Nuevo proveedor**. El campo que importa es el
   nombre en el chat exportado: tal como el teléfono guardó al contacto o, si no
   está guardado, el nombre que esa persona se puso (sin la `~` que WhatsApp le
   antepone). Si en el chat sale solo el número, basta con el teléfono.
4. En el teléfono: abrir el chat › Más › Exportar chat › **Incluir archivos**.
   Sirve igual un grupo o el canal de avisos de una comunidad, que es donde
   publican los proveedores de verdad: solo se leen los mensajes del remitente
   registrado y los de los demás miembros se ignoran. Si el mismo proveedor
   publica lo mismo en dos comunidades, con exportar una basta. En iPhone llega
   un `.zip`; en Android llegan el `.txt` y las fotos sueltos, y hay que
   comprimirlos en un zip con el `.txt` en la raíz.
5. En el panel, **Ingestas**: elegir el proveedor, subir el zip. La tabla se
   actualiza sola hasta que el lote termina; subir el mismo zip dos veces es
   seguro.
6. **Borradores**: revisar, corregir lo que la extracción sacó mal, y aprobar
   con marca, categoría, precio de venta y unidades por variante. El producto
   queda publicado.

Para ver el job que oculta lo que el proveedor deja de anunciar sin esperar
tres días, en `.env.local`:

```
PROVEEDORES_VENTANA_DISPONIBILIDAD=PT1M
PROVEEDORES_JOB_EXPIRACION_INTERVALO=PT1M
PROVEEDORES_JOB_EXPIRACION_RETRASO_INICIAL=PT10S
```

## Comandos frecuentes

| Comando | Qué hace |
|---|---|
| `npm run verificar` | Lint, pruebas y build de todo el monorepo |
| `npm run dev --workspace=apps/web` | Frontend en modo desarrollo |
| `npm test --workspace=apps/web` | Vitest |
| `gradlew.bat build` (en `apps/api`) | Compila, prueba y valida la arquitectura |
| `gradlew.bat spotlessApply` | Formatea el código Java |
| `npm run contratos` | Regenera el cliente TypeScript desde `packages/contratos/openapi.json` |
| `npm run contratos-al-dia` | ¿El cliente guardado corresponde a ese OpenAPI? (informa, no escribe) |
| `docker compose up -d` | PostgreSQL, Mailpit y Adminer |

## Documentación

`docs/README.md` es el índice. Antes de tocar código, lee `docs/00-producto.md`
y `docs/01-arquitectura.md`.

## Licencias de terceros

Las tipografías de `packages/marca/fuentes` son SIL OFL 1.1 y sus licencias se
distribuyen con los archivos. Es condición de la licencia.
