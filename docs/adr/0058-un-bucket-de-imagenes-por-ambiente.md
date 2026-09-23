# ADR-0058 — Un bucket de imágenes por ambiente

**Fecha:** 2026-09-23
**Estado:** aceptado y ejecutado entero el 23 de septiembre de 2026. Comprobado contra los buckets,
no contra el relato: **dev quedó en 300 objetos y local en 344**, y cada uno tiene solo lo suyo —el
cruce contra `cargados.json` no encuentra una sola key del otro ambiente en ninguno de los dos—. El
borrado de los 348 fue exacto: el bucket de dev de ahora es, key por key, el de antes menos esas 348,
sin nada que sobre ni nada que falte, y las 32 URL de imagen que publica el catálogo de dev responden
200. El paso 6 del runbook estaba escrito al revés y se corrigió antes de borrar nada — ver ese
paso.

## Contexto

Hubo un solo bucket de imágenes, `tecnosport-dev-imagenes`, y lo compartieron esta máquina y el
ambiente desplegado durante un mes. No fue una decisión: fue un valor por omisión. En
`application.yml`, `tecnosport.gcs.bucket-imagenes` caía en `tecnosport-dev-imagenes` cuando no
había variable, así que un `bootRun` en local sin `.env.local` —o con `.env.local` copiado del
ejemplo— escribía en el bucket del ambiente desplegado.

Lo que eso produjo, medido el 23 de septiembre de 2026: **648 objetos en el bucket, 348 de los 29
productos de local y 300 de los 25 de dev**, todos bajo `productos/`, ninguno bajo `rotacion/`.

El daño no fue el desorden. Fue que `npm run huerfanos` —el informe que dice qué objetos no reclama
nadie, para poder borrarlos— cruza el bucket contra **una** API. El 22 de septiembre listó 366 sin
reclamar y **348 eran las imágenes vivas del catálogo local**. Borrar esa lista no habría roto dev:
habría roto local, días después, sin relación aparente con nada. Se tapó con lo barato —que el
informe lea `catalogo/cargados.json` y separe "no lo reclama esta API" de "no lo reclama nadie"
(deuda 29)— y ahí quedó anotado que la salida definitiva era otra.

Declarar el bucket en Terraform, además, destapó un segundo defecto que llevaba ahí desde el
principio: **su CORS admitía `http://localhost:4200` y no el origen de su propia web**. Subir una
foto desde el panel desplegado, con el ratón, moría en el preflight. Nadie lo notó porque las cargas
del catálogo las hizo el cargador desde Node, que firma sin navegador.

## Decisión

**Un bucket por ambiente, y el ambiente lo decide quién corre el proceso, no un valor heredado.**

- `tecnosport-local-imagenes` para esta máquina, `tecnosport-dev-imagenes` para el ambiente
  desplegado, y `tecnosport-prod-imagenes` cuando exista producción. Los dos primeros en el proyecto
  `tecnosport-dev`; el de producción en el suyo.
- **El valor por omisión de `application.yml` es el de local**, porque describe dónde corre el
  proceso que lo lee por omisión: esta máquina. El ambiente desplegado fija sus variables desde
  Terraform y no depende de esa línea.
- **Una cuenta de servicio por bucket, con `objectAdmin` solo sobre el suyo.** La de local no tiene
  ningún permiso sobre el bucket de dev. Dos nombres con una sola llave capaz de escribir en los dos
  sitios no separan nada: el radio del error seguiría siendo el mismo.
- **Cada bucket lo administra un dueño solo.** El de local, `infra/local/bucket-imagenes.mjs`; el
  del ambiente desplegado, `infra/envs/dev`. El script se niega a actuar sobre el bucket de un
  ambiente desplegado en vez de obedecer, porque lo que hace —CORS, ciclo de vida, lectura pública,
  permisos— es configuración del ambiente, y dos dueños de la misma línea es peor que ninguno: gana
  el último que corrió y nada dice cuál fue.
- **El CORS de cada bucket es el de su propio ambiente.** El de dev, el origen de su web; el de
  local, `http://localhost:4200` más el túnel del teléfono cuando haga falta.
- **Los permisos del bucket se declaran autoritativos**, con `google_storage_bucket_iam_binding`
  y no con `_iam_member`: la lista de miembros que dice el código es la lista entera. Con permisos
  aditivos, una cuenta agregada a mano se queda para siempre y ningún `plan` la menciona — y este
  bucket tenía dos así: un `objectAdmin` de `imagenes-dev@`, una cuenta **ya borrada** de antes de
  que existiera este Terraform, y el de la cuenta con la que firmaba local, que es el que hizo
  posible que los dos ambientes se pisaran. Lo autoritativo es **por rol**, así que los roles
  heredados del proyecto (`legacy*` de `projectOwner` y compañía) quedan fuera y no se tocan.
- **El cruce por ambiente del informe de huérfanos se queda.** Con los buckets separados y las
  sobras borradas, cada informe cruzado contra su propia API sale sin nada "de otro ambiente", y ese
  cero es la comprobación de que la separación sigue en pie. Si más adelante aparece con objetos, no
  es información: es que alguien volvió a apuntar un ambiente al bucket del otro.

## Alternativas descartadas

**Un prefijo por ambiente dentro del mismo bucket** (`local/productos/…`, `dev/productos/…`). Es más
barato: ni cuenta nueva, ni llave nueva, ni migrar credenciales. Y no resuelve lo que hay que
resolver. Separa nombres y no permisos: la misma llave sigue pudiendo escribir y borrar en los dos
prefijos, así que un `--rehacer-imagenes` con la variable equivocada —que es exactamente el error
que ocurrió— sigue pudiendo pisar el otro ambiente. El informe de huérfanos tendría que seguir
cruzando registros para saber de quién es cada prefijo.

**Dejar solo el cruce del informe**, que es lo que ya estaba. Funciona mientras el registro exista y
esté al día, y prueba de qué ambiente es el **producto**, no que el objeto esté vivo — por eso lo de
otro ambiente no pasa a "reclamado" sino a "no juzgable", que es honesto y también inútil para
decidir un borrado. Sobre todo: deja la escritura cruzada posible. Vigilar el resultado de un error
que se puede impedir es aceptar el error.

**Un proyecto de GCP por ambiente, con local en el suyo.** Sería la separación más fuerte —cuota,
facturación e IAM aparte— y cuesta más de lo que compra hoy: la capa gratuita de Storage se mide por
proyecto, y partir el de desarrollo obligaría a repetir APIs, federación y registro de imágenes para
una máquina. Producción sí va en proyecto aparte, y eso no cambia.

**Un emulador de Cloud Storage en local.** Ya estaba descartado en `docs/07` y se mantiene: la firma
de URL, el CORS y el preflight son justo donde aparecen los fallos, y un emulador los simula.

## Consecuencias

- Migrar local es volver a subir: `cargar-catalogo.mjs --rehacer-imagenes` contra `localhost`, que
  lee las maestras del disco y reescribe las URL por la API. No hay `UPDATE` a mano. Y no hay sets
  de rotación en el bucket —cero objetos bajo `rotacion/`—, así que no queda nada fuera de ese
  camino.
- **Entre el cambio de variable y el rehacer, quitar una imagen borra la fila y no el objeto.**
  `AlmacenDeImagenesGcs.objectKeyDe` no reconoce una URL que no empiece por su base pública, y
  `QuitarImagenDeGaleria` devuelve entonces "salió de la galería sin borrar ningún objeto" con su
  aviso en el registro. Estaba escrito "para el día que la URL pública cambie por un CDN"; este es
  ese día. Lo que queda son objetos sin reclamar, y para eso está el informe.
- Los 348 objetos viejos de local se quedan en el bucket de dev hasta que se borren, y para
  listarlos hay que cruzar ese bucket contra la API **de local**: son de productos de local, así que
  contra la API de dev el informe los declara "de otro ambiente" y no ofrece nada que borrar. Es la
  protección de la deuda 29 aplicada a un caso donde estorba, y no es un defecto — el informe no
  puede saber que local ya se mudó. Medir y borrar, como el 22 de septiembre.
- La cuenta `tecnosport-dev-imagenes` se queda sin usar en cuanto local firma con la suya, así que
  se borra junto con su llave JSON de esta máquina; su `objectAdmin` sobre el bucket de dev ya se
  retiró, y con él se fue la posibilidad del error que abrió la deuda 29. Mientras las dos llaves
  convivan en `~/.gcp/`, la vieja no firma nada: el backend usa la que diga
  `GOOGLE_APPLICATION_CREDENTIALS`.
- El bucket de dev queda con `prevent_destroy`. Borrarlo exige quitar esa línea a mano, que es la
  pausa que se quiere: dentro viven las imágenes del catálogo de dev y un bucket no se recrea con su
  contenido.
- La capa gratuita de Storage son 5 GB-mes **por proyecto**, no por bucket, así que tener dos no
  acerca el cobro: el catálogo entero pesa 2,18 MiB.
- Producción hereda la regla escrita, no por costumbre: su bucket es suyo, y el script de local se
  niega también a tocarlo.

## El runbook de la migración

En este orden, y el primer paso no es opcional: mientras `.env.local` diga `tecnosport-dev-imagenes`,
el script se niega a crear nada.

1. `.env.local`: `GCS_BUCKET_IMAGENES=tecnosport-local-imagenes`,
   `GCS_URL_PUBLICA=https://storage.googleapis.com/tecnosport-local-imagenes`, y
   `GOOGLE_APPLICATION_CREDENTIALS` a la llave nueva.
2. `node infra/local/bucket-imagenes.mjs` — crea el bucket, la cuenta, la llave, el CORS y el ciclo
   de vida.
3. En `infra/envs/dev`, `terraform apply "-target=google_storage_bucket.imagenes"` para el CORS del
   ambiente desplegado. Con `-target` a propósito: el estado trae una deriva ajena en el servicio de
   Cloud Run —etiquetas puestas a mano, `reinicio=r2`— y aplicarla de paso no es parte de esto. **Y
   con la bandera entre comillas**: sin ellas, en PowerShell llega `google_storage_bucket` a secas y
   Terraform responde `Invalid target`, que es un error que no menciona el entrecomillado.
4. Quitarle a la cuenta vieja el acceso al bucket de dev, con
   `gcloud storage buckets remove-iam-policy-binding` sobre
   `serviceAccount:tecnosport-dev-imagenes@tecnosport-dev.iam.gserviceaccount.com` y
   `roles/storage.objectAdmin`. De paso sobra un binding de una cuenta ya borrada (`imagenes-dev@`),
   que se puede retirar igual.
5. `gradlew.bat bootRun`, y `node tools/cargar-catalogo.mjs --rehacer-imagenes` primero en
   simulación y luego con `--escribir`. Comprobar en el navegador que la vitrina y una ficha pintan
   desde el host nuevo.
6. `npm run huerfanos -- --bucket tecnosport-dev-imagenes --api http://localhost:8080`, con
   `bootRun` arriba. **La API es la de local, no la de dev**, y esto se escribió al revés la primera
   vez: los 348 objetos viejos son de productos **de local**, así que cruzándolos contra la API de
   dev caen en "de otro ambiente, no los juzgo" —la protección de la deuda 29 haciendo su trabajo— y
   el informe no ofrece nada que borrar. Contra la API de local salen como **sin reclamar por
   nadie**, que es lo que son: local ya apunta al bucket nuevo. El informe lo dice él mismo en su
   última línea cuando llena esa sección ("para juzgarlos, corre este informe con --api apuntando a
   ese ambiente"), y aun así hubo que ejecutarlo para verlo.
7. Borrar esa lista del bucket de dev, que debe quedar en **300** objetos: los 348 viejos incluyen
   los **4** huérfanos que ya estaban ahí desde el 22 de septiembre, y por eso local reclama 344 y
   no 348.
8. Volver a medir los dos, cada uno contra su propia API. En régimen los dos informes salen limpios
   y sin nada "de otro ambiente"; mientras los 348 sigan en el bucket de dev, esa sección informa de
   ellos con razón.
9. Y las sobras, que se limpian al final: el `objectAdmin` de la cuenta ya borrada `imagenes-dev@`
   sale del `apply` de los permisos autoritativos —no de un `gcloud` a mano, que es lo que dejaría el
   próximo igual de invisible—, y la cuenta vieja `tecnosport-dev-imagenes@` se borra con
   `gcloud iam service-accounts delete`, junto con su llave JSON de `~/.gcp/`. Borrar la cuenta
   revoca sus llaves, así que el orden entre esas dos cosas no importa.
