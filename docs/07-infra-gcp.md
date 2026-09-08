# Infraestructura, entornos y despliegue

## Entornos

| Entorno | Dónde | Base de datos | Correo |
|---|---|---|---|
| Local | Docker Compose en Windows | PostgreSQL 16 en contenedor | Mailpit: no sale nada |
| Dev en línea | GCP, proyecto `tecnosport-dev`, capa gratuita | Neon, plan gratuito | Sender, entrega real |
| Producción | GCP, proyecto `tecnosport-prod` | Cloud SQL PostgreSQL 16 | por decidir |

Staging cuando haya tráfico que justifique el costo. Terraform lo deja como un
módulo parametrizado, así que agregarlo será cambiar una variable.

## Local

`docker-compose.yml` en la raíz levanta PostgreSQL 16, Mailpit para ver los
correos sin enviarlos, y Adminer. El backend corre con Gradle y el frontend con
npm, fuera de Docker: en Windows la recarga en caliente dentro de un contenedor
es lenta.

Variables en `.env.local`, que está en `.gitignore`. Hay un `.env.example`
versionado con todas las claves y valores falsos.

Para probar la captura 360 desde un teléfono real hace falta HTTPS, porque la
cámara y los sensores no funcionan sobre HTTP fuera de `localhost`. Se resuelve
con un túnel o con un certificado local. Está documentado en el README de
`apps/web`.

**Imágenes en dev: bucket real de Cloud Storage, no un emulador.** Proyecto
GCP separado (`tecnosport-dev`, nunca `tecnosport-prod`) con un bucket en la
capa gratuita (5 GB-mes, clase Standard, regiones `us-central1`/`us-east1`/
`us-west1`) y una cuenta de servicio propia con `roles/storage.objectAdmin`
para firmar URLs — decisión del proyecto: dev usa solo servicios de GCP sin
costo, los servicios pagos se activan al pasar a producción. CORS configurado
para el origen de `apps/web` en local (`http://localhost:4200`).

Todo eso lo crea `node infra/dev/bucket-imagenes.mjs`, idempotente, con la llave
de la cuenta de servicio en la ruta de `GOOGLE_APPLICATION_CREDENTIALS`. Sin esa
llave el backend arranca igual —el bean `Storage` se construye sin credenciales—
pero firmar falla: `POST /api/v1/admin/sets-rotacion/{id}/subidas` responde 500
con `Signing key was not provided and could not be derived`, y con él se cae todo
lo que sigue del asistente de captura.

## Producción en GCP

| Servicio | Para qué |
|---|---|
| Cloud Run `tecnosport-api` | Backend Spring Boot |
| Cloud Run `tecnosport-web` | Frontend Angular con SSR |
| Cloud SQL PostgreSQL 16 | Base de datos, sin IP pública, por conector |
| Artifact Registry | Imágenes de contenedor |
| Cloud Storage y CDN | Imágenes de producto, sets de rotación y estáticos |
| Secret Manager | Llaves de Wompi, credenciales y secreto de webhook de Skydropx, secreto JWT, credenciales SMTP |
| Cloud Load Balancing | Dominio, TLS y enrutamiento: `/api` a la API, el resto a la web |
| Cloud Logging y Monitoring | Registros, métricas, alertas de 5xx y de latencia |
| Cloud Scheduler | Liberar reservas vencidas, conciliar pagos, conciliar seguimiento de envíos, generar sitemap |

Cloud Run con mínimo de instancias en 1 para la API: el arranque en frío de una
JVM se siente. CRaC o imagen nativa solo si el costo aprieta, no de entrada.

Las imágenes se suben directo a Cloud Storage con URL firmada desde el panel y
desde el asistente de captura. No pasan por el backend. El bucket de imágenes es
público de lectura a través del CDN, y de escritura solo con URL firmada.

## DNS: cuidado con lo que ya existe

`tecnosport.co` está en GoDaddy, y **ya no hay que suponer** de qué depende el
correo: esto es lo que la zona sirve hoy (consultado el 8 de septiembre de 2026
contra `8.8.8.8`).

```
MX     0   smtp.secureserver.net
MX    10   mailstore1.secureserver.net
TXT        v=spf1 include:secureserver.net -all
_dmarc TXT v=DMARC1; p=quarantine; adkim=r; aspf=r; rua=mailto:dmarc_rua@onsecureserver.net;
```

Tres lecturas que cambian cómo se toca esto:

- **El correo del negocio depende de esos dos MX.** Borrarlos o reemplazarlos al
  apuntar el dominio a GCP deja al negocio sin correo, y nadie se entera hasta
  que un cliente reclama.
- **El SPF es único y termina en `-all`**, que es rechazo duro. Un dominio no
  puede tener dos registros SPF: si se añade el de otro proveedor al lado, los
  dos quedan inválidos y el correo legítimo empieza a fallar. Cualquier
  proveedor nuevo se autentica en **un subdominio propio**, con su TXT aparte, o
  se *edita* este registro para incluirlo — nunca se agrega un segundo.
- **El DMARC no lleva `sp=`**, así que los subdominios heredan `p=quarantine`.
  Un `dev.tecnosport.co` que mande sin SPF ni DKIM propios no se pierde en el
  aire: se va a la carpeta de no deseado, que es peor porque parece que
  funciona. La alineación es relajada (`adkim=r`, `aspf=r`), así que un
  subdominio bien autenticado alinea sin tocar nada de la raíz.

Al apuntar el dominio a GCP **no se tocan los registros MX ni los TXT de SPF,
DKIM y DMARC**. Se cambian solo A, AAAA de la raíz y el CNAME de www.

Plan: crear el balanceador, verificar con un subdominio de prueba, y solo
entonces mover la raíz. Bajar el TTL a 300 segundos un día antes.

## Correo saliente

`docs/12-legales-de-envio.md` marca `[[PROVEEDOR DE CORREO TRANSACCIONAL]]` como
dato de negocio pendiente. **Sigue pendiente para producción**, y se decidió
solo el de dev, que es otra pregunta: en dev basta con que los correos salgan
para poder recorrer el registro y la recuperación de clave.

**Dev usa Sender** (plan gratuito, 15.000 correos al mes, transaccional por SMTP
en todos los planes). No hay nada que programar: `spring.mail` ya sale de
variables y `starttls.enable` ya está en `true`, así que es cambiar
`SMTP_HOST`, `SMTP_PUERTO`, `SMTP_AUTH`, `SMTP_USUARIO`, `SMTP_CLAVE` y
`CORREO_REMITENTE`.

**El remitente de dev es `no-responder@dev.tecnosport.co`, y el subdominio no es
un detalle**: autenticar el proveedor sobre la raíz obligaría a meter mano en el
SPF de `tecnosport.co`, que es único y termina en `-all` (ver la sección de DNS).
Un error ahí lo paga el correo del negocio. El subdominio lleva su propio SPF y
su propio DKIM, los MX de la raíz no se tocan, y de paso la reputación de dev
queda separada de la que tendrá producción.

Dos cosas que hay que tener presentes al usarlo:

- **Sender entrega de verdad.** Mailpit existe en local justo para que nada
  salga; en dev sí sale. Se prueba solo con direcciones propias: un correo de
  verificación a la dirección equivocada es un correo real a una persona real.
- **El plan gratuito retiene los registros un día.** Para depurar una entrega de
  anteayer no habrá nada que mirar.

Para producción, la decisión queda abierta a propósito: Sender es una plataforma
de marketing —su plan gratuito cuenta "suscriptores"— y mezclar el correo
comercial con el transaccional bajo la misma reputación es algo que conviene
separar deliberadamente, no por inercia de lo que se eligió para dev.

## Infraestructura como código

Terraform desde el inicio, con estado remoto en un bucket de GCS con versionado y
bloqueo. Un módulo por pieza. Nada creado a mano en la consola: si existe en
producción y no está en Terraform, no existe. Estructura en `infra/README.md`.

## CI/CD con GitHub Actions

- **En cada pull request:** compilar, lint, pruebas de backend y frontend,
  ArchUnit, verificación de claves de i18n y `terraform plan`. Sin eso en verde no
  se mezcla.
- **Al mezclar a `main`:** construir imágenes, publicar en Artifact Registry,
  aplicar migraciones Flyway como paso propio, desplegar Cloud Run, verificar
  salud y revertir a la revisión anterior si falla.
- **Detección por ruta:** un cambio que solo toca `apps/web` no reconstruye el
  backend. Las pruebas de contrato corren siempre.
- **Autenticación con Workload Identity Federation.** Nunca una llave JSON de
  cuenta de servicio guardada como secreto de GitHub.

Las migraciones corren como paso separado antes del despliegue, no al arrancar la
aplicación: con varias instancias, dos arranques simultáneos migrando la misma
base es una carrera perdida.

## Configuración

Todo parametrizable, nada literal en el código.

```
APP_URL_PUBLICA
API_URL_PUBLICA

DB_HOST, DB_PUERTO, DB_NOMBRE, DB_USUARIO, DB_CLAVE

WOMPI_LLAVE_PUBLICA, WOMPI_LLAVE_PRIVADA
WOMPI_SECRETO_EVENTOS, WOMPI_SECRETO_INTEGRIDAD, WOMPI_AMBIENTE
WOMPI_CONCILIACION_INTERVALO_MINUTOS, WOMPI_CONCILIACION_ANTIGUEDAD_MINIMA_MINUTOS

TRANSFERENCIA_BANCO, TRANSFERENCIA_TIPO_CUENTA, TRANSFERENCIA_NUMERO_CUENTA,
TRANSFERENCIA_TITULAR

JWT_SECRETO, JWT_MINUTOS_ACCESO, JWT_DIAS_REFRESCO

VERIFICACION_CORREO_HORAS_VENCIMIENTO
RECUPERACION_CLAVE_MINUTOS_VENCIMIENTO

LIMITE_AUTH_IP_MAXIMO, LIMITE_AUTH_IP_MINUTOS
LIMITE_AUTH_CUENTA_MAXIMO, LIMITE_AUTH_CUENTA_MINUTOS
LIMITE_PEDIDOS_IP_MAXIMO, LIMITE_PEDIDOS_IP_MINUTOS
LIMITE_PEDIDOS_CUENTA_MAXIMO, LIMITE_PEDIDOS_CUENTA_MINUTOS

ADMIN_CORREO, ADMIN_CLAVE

GCS_BUCKET_IMAGENES, GCS_URL_PUBLICA, GCS_MINUTOS_URL_FIRMADA
GOOGLE_APPLICATION_CREDENTIALS  (solo local: ruta a la llave de la cuenta de servicio;
                                 en Cloud Run no se define, se usa la cuenta de servicio adjunta)

SMTP_HOST, SMTP_PUERTO, SMTP_AUTH, SMTP_USUARIO, SMTP_CLAVE, CORREO_REMITENTE

SKYDROPX_URL_BASE, SKYDROPX_CLIENT_ID, SKYDROPX_CLIENT_SECRET
SKYDROPX_SECRETO_WEBHOOK
SKYDROPX_COTIZACION_TIMEOUT_SEGUNDOS, SKYDROPX_COTIZACION_INTENTOS
SKYDROPX_SEGUIMIENTO_INTERVALO_MINUTOS, SKYDROPX_SEGUIMIENTO_ANTIGUEDAD_MINIMA_HORAS
ORIGEN_NOMBRE, ORIGEN_TELEFONO, ORIGEN_DIRECCION,
ORIGEN_CIUDAD_DANE, ORIGEN_CODIGO_POSTAL

CONTRAENTREGA_HABILITADA
CONTRAENTREGA_MONTO_MAXIMO
CONTRAENTREGA_CATEGORIAS_EXCLUIDAS
TRANSFERENCIA_HORAS_VENCIMIENTO

MINUTOS_RESERVA_INVENTARIO
IVA_TASA_PREDETERMINADA
ROTACION_FOTOGRAMAS_PREDETERMINADO
ROTACION_TOLERANCIA_GRADOS
```

Las de `SKYDROPX_*` son la cotización, la emisión de guía y el seguimiento
(`docs/11-pagos-y-envios.md`). Dos notas que ahorran una tarde:

- **`SKYDROPX_URL_BASE` es variable a propósito y todavía no está confirmada.**
  La documentación pública muestra `pro.skydropx.com` (producción) y
  `sb-pro.skydropx.com` (pruebas), y según la fuente aparecen también
  `api-pro.skydropx.com` y `app.skydropx.com.co`. `TODO: confirmar el host de la
  cuenta colombiana en el panel, Conexiones > API.`
- **`ORIGEN_*` es la dirección de despacho del negocio**, la que va como origen de
  cada cotización y de cada recolección. Es la misma del punto de recogida, y por
  eso no se duplica en el código: si el negocio se muda, se cambia una vez.

En Spring, `@ConfigurationProperties` tipadas y validadas al arrancar. Si falta
una variable obligatoria, la aplicación no arranca; no arranca a medias para
fallar en la primera compra.

Los secretos viven en Secret Manager y se montan como variables en Cloud Run.
Ninguno en el repositorio, ni de sandbox, ni en un comentario.

## Respaldos

Copias automáticas diarias de Cloud SQL con 30 días de retención y recuperación a
punto en el tiempo activada. Una restauración de prueba antes de abrir al
público: un respaldo que nunca se restauró no es un respaldo.

El bucket de imágenes con versionado de objetos: un borrado accidental de un set
de rotación son quince fotos que hay que volver a tomar. Esto no es solo para
accidentes: `DELETE /api/v1/admin/sets-rotacion/{id}` borra de verdad los objetos
del set —si no, el espacio no se reclama nunca— y el versionado es lo único que
hace ese borrado reversible. Y con una regla de ciclo de vida que expire las
versiones no vigentes: sin ella se acumulan, y el espacio no se reclama igual.

Un detalle que cuesta caro y no se ve: hay que borrar **por nombre, sin
generación**. El SDK de Java, si se le pasa un objeto que vino de un listado,
borra esa generación concreta —un borrado definitivo que se salta el versionado y
no deja nada que restaurar. Comprobado contra el bucket real, en los dos
sentidos.
