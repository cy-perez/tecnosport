# infra/dev

Lo mínimo de GCP que el entorno de desarrollo necesita para funcionar de verdad.

Producción se define con Terraform y no se toca a mano — esa regla, la del
`README.md` de arriba, no cambia. Esto es otra cosa: un proyecto de GCP separado
(`tecnosport-dev`), en la capa gratuita, que existe solo para que la subida de
imágenes se pueda probar contra Cloud Storage real. Montarlo con Terraform
exigiría primero un bucket de estado remoto para el propio Terraform, y ese
arranque en frío no se paga solo por un bucket y una cuenta de servicio.

## El bucket de imágenes

```
node infra/dev/bucket-imagenes.mjs
```

Antes: `gcloud auth login`, y un proyecto de desarrollo con facturación
habilitada (Cloud Storage la exige aunque el consumo caiga dentro de los 5 GB-mes
gratuitos).

Crea, sin duplicar nada si ya existe:

- el bucket `tecnosport-dev-imagenes`, Standard con acceso uniforme, en una
  región de la capa gratuita — la región solo aplica al crearlo: un bucket que ya
  existe no se puede mover;
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
- la cuenta de servicio `tecnosport-dev-imagenes` —el mismo nombre del bucket, a
  propósito— con `objectAdmin` **solo sobre este bucket**, y su llave JSON en la
  ruta de `GOOGLE_APPLICATION_CREDENTIALS`.

Si esa ruta apunta fuera del perfil de Windows actual, el script para en vez de
crear el directorio. La primera versión no lo hacía y terminó escribiendo una
llave dentro del perfil de otro usuario de la máquina.

La llave privada es lo que de verdad hace falta: sin ella el SDK arranca igual,
pero `signUrl` falla con `Signing key was not provided and could not be derived`
y `POST /api/v1/admin/sets-rotacion/{id}/subidas` responde 500.

## Para probar la captura desde un teléfono

La cámara y los sensores exigen HTTPS fuera de `localhost`, así que el asistente
se sirve por un túnel. Ese origen también tiene que estar en el CORS del bucket,
o el `PUT` firmado muere en el preflight:

```
GCS_ORIGENES_CORS=http://localhost:4200,https://tu-tunel.example node infra/dev/bucket-imagenes.mjs
```

Correrlo de nuevo solo reemplaza la configuración de CORS; el bucket, la cuenta y
la llave quedan como están.
