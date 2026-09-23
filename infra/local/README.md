# infra/local

Lo que esta máquina necesita en GCP para desarrollar, y que no es parte de ningún
ambiente desplegado.

Hoy es una cosa sola: **el bucket de imágenes de local**, con su cuenta de servicio
y su llave. Vive aquí y no en `infra/dev` porque no es dev — es esta máquina — y
tampoco en Terraform porque no se despliega: es el entorno de quien programa, y
cada quien crea el suyo.

## El bucket de imágenes de local

```
node infra/local/bucket-imagenes.mjs
```

Antes: `gcloud auth login`, un proyecto de desarrollo con facturación habilitada
(Cloud Storage la exige aunque el consumo caiga dentro de los 5 GB-mes gratuitos),
y **`.env.local` apuntando al bucket de local** — si `GCS_BUCKET_IMAGENES` dice
`tecnosport-dev-imagenes`, el script se niega y explica por qué. Ver abajo.

Crea, sin duplicar nada si ya existe:

- el bucket `tecnosport-local-imagenes`, Standard con acceso uniforme, en
  `us-east1` — una región de la capa gratuita, la misma del bucket de dev, y solo
  aplica al crearlo: un bucket que ya existe no se puede mover;
- versionado de objetos, con una regla de ciclo de vida que borra las versiones
  no vigentes a los 30 días — es la red de la que depende el borrado de un set:
  `EliminarSetRotacion` borra los objetos del bucket, y sin versionado eso sería
  irreversible. Sin la regla de ciclo de vida, el espacio no se reclamaría nunca,
  que era justo el problema. Los 30 días son un valor de arranque, marcado como
  `TODO(negocio)` en el script;
- lectura pública (`allUsers` como `objectViewer`), porque la ficha de producto
  sirve las imágenes por URL directa — la escritura sigue siendo solo con URL
  firmada;
- CORS para `http://localhost:4200`;
- la cuenta de servicio `tecnosport-local-imagenes` —el mismo nombre del bucket, a
  propósito— con `objectAdmin` **solo sobre este bucket**, y su llave JSON en la
  ruta de `GOOGLE_APPLICATION_CREDENTIALS`.

Si esa ruta apunta fuera del perfil de Windows actual, el script para en vez de
crear el directorio. La primera versión no lo hacía y terminó escribiendo una
llave dentro del perfil de otro usuario de la máquina.

La llave privada es lo que de verdad hace falta: sin ella el SDK arranca igual,
pero `signUrl` falla con `Signing key was not provided and could not be derived`
y `POST /api/v1/admin/sets-rotacion/{id}/subidas` responde 500.

## Un bucket por ambiente, y por qué el script se niega

`ADR-0058`. Hasta el 23 de septiembre de 2026 había **un** bucket
—`tecnosport-dev-imagenes`— y lo compartían esta máquina y el ambiente desplegado,
porque el valor por omisión de `application.yml` era el del ambiente desplegado: un
`bootRun` sin variables escribía allá. Llegó a tener 648 objetos, 348 de local y
300 de dev, y el informe de huérfanos tuvo que aprender a distinguirlos para no
proponer borrar las imágenes vivas del otro lado.

Este script administra **solo** el de local: crear, CORS, ciclo de vida, lectura
pública y cuenta de servicio. El del ambiente desplegado lo declara Terraform en
`infra/envs/dev`. Por eso el script rechaza `tecnosport-dev-imagenes` y
`tecnosport-prod-imagenes` en vez de obedecer: reconfigurarle el CORS al ambiente
desplegado desde la máquina de alguien es precisamente lo que no debe poder pasar
sin querer.

La cuenta de servicio de local **no tiene ningún permiso sobre el bucket de dev**,
y eso es la mitad que importa de la separación: dos nombres con una sola llave que
puede escribir en los dos sitios no separan nada.

## Para probar la captura desde un teléfono

La cámara y los sensores exigen HTTPS fuera de `localhost`, así que el asistente
se sirve por un túnel. Ese origen también tiene que estar en el CORS del bucket,
o el `PUT` firmado muere en el preflight:

```
GCS_ORIGENES_CORS=http://localhost:4200,https://tu-tunel.example node infra/local/bucket-imagenes.mjs
```

Correrlo de nuevo solo reemplaza la configuración de CORS; el bucket, la cuenta y
la llave quedan como están.
