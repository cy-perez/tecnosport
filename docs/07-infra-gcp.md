# Infraestructura, entornos y despliegue

## Entornos

| Entorno | Dónde | Base de datos | Correo |
|---|---|---|---|
| Local | Docker Compose en Windows | PostgreSQL 16 en contenedor | Mailpit: no sale nada |
| Dev en línea | GCP, proyecto `tecnosport-dev`, capa gratuita | Neon, plan gratuito | Resend, entrega real |
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

Lo que sí cambió es el texto publicado: la política de datos ya no imprime el
marcador, describe la categoría ("el proveedor de correo transaccional que
usemos"). El día que se decida el de producción hay que **nombrarlo ahí** y subir
la versión del documento — es la mitad del trabajo que nadie apunta. `ADR-0025`.

**Dev usa Resend** (`resend.com`, plan gratuito: 3.000 correos al mes con tope de
100 al día, 3 dominios y 30 días de registros). No hay nada que programar:
`spring.mail` ya sale de variables y `starttls.enable` ya está en `true`, así que
es cambiar la configuración.

```
SMTP_HOST=smtp.resend.com
SMTP_PUERTO=587            # STARTTLS, que es lo que el application.yml ya activa
SMTP_AUTH=true
SMTP_USUARIO=resend        # literal: el usuario siempre es "resend"
SMTP_CLAVE=<la API key>    # la clave es la API key, no una contraseña de cuenta
CORREO_REMITENTE=no-responder@dev.tecnosport.co
```

**El remitente de dev va en un subdominio.** Conviene ser exacto sobre por qué,
porque la razón obvia no es la correcta: los registros de envío de Resend cuelgan
de `send.<dominio verificado>`, así que verificar la raíz **no** obligaría a
tocar el SPF del ápice ni los MX del correo corporativo. Funcionaría.

El motivo es el radio de daño. Con la raíz verificada, el ambiente de dev
mandaría como `@tecnosport.co` y cada rebote, cada queja de spam y cada prueba
mal dirigida se acumularían sobre la reputación del dominio con el que el negocio
escribe a sus clientes y a sus proveedores. Dev es donde se rompen cosas a
propósito. Por eso va aparte —Resend **recomienda subdominio por su cuenta**, por
esta misma razón— y por eso el día que se decida el proveedor de producción se
podrá elegir sin arrastrar el historial de las pruebas.

Los registros que pide Resend cuelgan todos del dominio verificado, y por eso el
subdominio importa el doble: **uno de ellos es un MX**. Sobre
`dev.tecnosport.co`, la forma es esta —los valores exactos los da su panel y
dependen de la región—:

```
MX   send.dev.tecnosport.co              feedback-smtp.<región>.amazonses.com  (prioridad 10)
TXT  send.dev.tecnosport.co              v=spf1 include:amazonses.com ~all
TXT  resend._domainkey.dev.tecnosport.co p=<la llave DKIM que muestre el panel>
```

Nada de eso toca la raíz: los dos MX de `secureserver.net` que reciben el correo
del negocio siguen intactos, y el MX de Resend vive tres niveles más abajo. Ese
MX es para rebotes y quejas, no para recibir correo del negocio.

Dos cosas que hay que tener presentes al usarlo:

- **Resend entrega de verdad.** Mailpit existe en local justo para que nada
  salga; en dev sí sale. Se prueba solo con direcciones propias: un correo de
  verificación a la dirección equivocada es un correo real a una persona real.
- **El tope diario son 100 correos.** Suficiente para probar registro y
  recuperación de clave; no para una prueba de carga que mande correos.

Para producción la decisión sigue abierta a propósito. No porque Resend no
sirva —es transaccional de primera intención, que es exactamente lo que hace
falta—, sino porque elegir el proveedor de producción es una decisión de negocio
con otras variables: volumen real, precio al crecer, soporte y qué pasa el día
que un correo de confirmación de pedido no llega. Heredarla de lo que se eligió
para dev sería tomarla por inercia.

## Imágenes de contenedor

Un `Dockerfile` por aplicación, los dos **construidos desde la raíz del repositorio**:

```
docker build -f apps/api/Dockerfile -t tecnosport-api .
docker build -f apps/web/Dockerfile -t tecnosport-web .
```

El contexto tiene que ser la raíz en el caso de la web —es un monorepo con workspaces de npm y la
aplicación depende de `packages/contratos`— y se hace igual con la API por coherencia: un solo
contexto para las dos.

Las dos son de dos etapas y ninguna lleva herramientas de construcción en la imagen final. La API
compila con JDK 21 y corre sobre JRE, con usuario propio y `-XX:MaxRAMPercentage=75` en vez de un
`-Xmx` fijo: el límite de memoria lo pone Cloud Run por despliegue y una JVM que no lo lee o se
queda corta o la matan por exceso. La web **no lleva `node_modules`**: el constructor de Angular
empaqueta las dependencias del servidor dentro del bundle — comprobado, los únicos imports que
quedan fuera son módulos nativos de Node.

### Lo que el despliegue tiene que definir, o el sitio no funciona

Esto salió de correr las imágenes de verdad, no de leerlas. Cada una costó un síntoma distinto y
ninguno dice en voz alta cuál es la causa:

| Variable | Sin ella | Por qué |
|---|---|---|
| `NG_ALLOWED_HOSTS` | **400 a cada petición** | `security.allowedHosts` de `angular.json` viaja dentro del bundle del servidor y hoy solo admite `tecnosport.co` y `www`. En un dominio `*.run.app`, `@angular/ssr` rechaza todo. Acepta comodín (`*.run.app`). No se pone un valor permisivo por omisión en la imagen porque la comprobación existe por una razón real (SSRF) |
| `API_URL_PUBLICA` | **la petición se cuelga y nunca responde** | Sin ella, el SSR resuelve `baseUrl()` a `localhost:8080`, que dentro del contenedor **es el propio servidor web**: cada render se pide a sí mismo, y ese render vuelve a pedirse. Recursión, sin ningún error en el registro |
| `APP_URL_PUBLICA` | canónicos y `hreflang` apuntando al puerto local | Ya estaba documentada; aquí se confirma que el contenedor la necesita |
| `NG_TRUST_PROXY_HEADERS` | aviso en consola en cada petición | Detrás de Cloud Run todo llega con `x-forwarded-*`; sin declararlas, `@angular/ssr` las descarta y avisa |

### El SSR se pedía sus propias traducciones, y ya no

`TranslocoHttpLoader` pide los JSON de i18n por HTTP con una ruta **relativa**, y en el servidor
eso se resuelve contra la cabecera `Host` de la petición que se está renderizando: cada render
salía a la red para pedirse a sí mismo los textos.

Se vio al correr la imagen con el puerto de fuera distinto del de dentro: el contenedor intentaba
alcanzarse en un puerto donde no escucha, la carga fallaba y la página salía **con las claves de
Transloco crudas en vez de los textos** (`catalogo.seo.ficha.titulo_con_nombre` en el `<title>`),
sin un solo error en el registro. En Cloud Run habría funcionado, pero pagando una ida y vuelta
por la red pública en cada render y con ese mismo fallo mudo esperando al día que el servicio
quede detrás de autenticación.

Arreglado con un cargador propio del servidor que lee los JSON del disco, donde ya están dentro de
la imagen (`docs/05-i18n.md`). Verificado en el mismo escenario que lo destapó: con los puertos
distintos, la ficha vuelve a servirse traducida en los dos idiomas y no se registra ni un respaldo
por HTTP.

### Despliegue del ambiente de desarrollo

`.github/workflows/desplegar-dev.yml`. Construye las dos imágenes, las sube a Artifact Registry y
mueve las revisiones de Cloud Run. Decisiones que quedaron dentro:

- **Sin llaves JSON**: federación de identidad. GitHub presenta un token firmado de esa ejecución
  y GCP lo cambia por credenciales de minutos, acotadas al repositorio por la condición del
  proveedor. Los cuatro valores públicos que necesita (proyecto, proveedor, cuenta y registro)
  están como *variables* del repositorio, no como secretos: no lo son.
- **La etiqueta de la imagen es el SHA del commit**, no `latest`. Así una revisión de Cloud Run
  dice de qué código salió, y revertir es apuntar a una imagen concreta en vez de adivinar.
- **La API se despliega antes que la web**, que la consulta al renderizar.
- **Comprobación de salud y reversión.** Que Cloud Run acepte la revisión no significa que el
  sitio sirva: el contenedor puede levantar y la aplicación responder 500 a todo. Si `/api/v1/salud`
  o `/es` no dan 200 —con reintentos, porque la primera petición paga el arranque en frío de la
  JVM—, el tráfico vuelve a la revisión anterior.
- **Un despliegue a la vez y sin cancelar el que va**: matar un `gcloud run deploy` a mitad deja el
  servicio en un estado que nadie pidió. Los otros flujos sí se cancelan entre sí; este espera.

**Todavía se dispara solo a demanda**, y es deliberado: sin base de datos la API no arranca, así
que engancharlo a `main` hoy produciría un despliegue rojo en cada merge. Pasa a `push` cuando
exista el proyecto de Neon y los secretos tengan valor.

**Lo que este flujo no hace todavía: las migraciones como paso propio.** Hoy Flyway corre al
arrancar la aplicación, como en local. Para dev es tolerable —Flyway toma un bloqueo, así que tres
instancias arrancando a la vez no se pisan— pero **para producción no**, y está escrito arriba por
qué. El paso separado se agrega junto con Neon, que es cuando se puede probar de verdad.

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

NG_ALLOWED_HOSTS        (solo el servicio web; ver "Imágenes de contenedor")
NG_TRUST_PROXY_HEADERS  (solo el servicio web)

DB_HOST, DB_PUERTO, DB_NOMBRE, DB_USUARIO, DB_CLAVE

WOMPI_LLAVE_PUBLICA     (no hay llave privada: esta integración no la usa — el checkout se abre
                         con la pública y el estado de una transacción se consulta con ella misma,
                         `Authorization: Bearer <llave pública>`. La privada sirve para operar
                         transacciones desde el servidor, que es lo que este diseño evita para no
                         ampliar el alcance de PCI)
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
