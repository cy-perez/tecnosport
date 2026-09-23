# infra

Infraestructura de TecnoSport en Google Cloud Platform, definida con Terraform.

Nada se crea a mano en la consola. Si existe y no está aquí, no existe.

## Estructura

```
local/                lo que la máquina de quien programa necesita en GCP (scripts de Node)
dev/                  arranque en frío del proyecto de desarrollo (scripts de Node)
modules/cloud-run/    un servicio de Cloud Run
envs/dev/             el ambiente de desarrollo desplegado, entero
```

**Un módulo existe cuando hay dos usos, no antes.** `cloud-run` los tiene —la API y la web— y
tendrá cuatro cuando exista producción. El resto (registro de imágenes, identidades, federación
con GitHub, secretos) vive escrito en el entorno, sin envolver: un módulo con un solo llamador es
una capa de indirección que no compró nada. Esa es la diferencia con lo que este archivo describía
antes, que era una estructura de cinco módulos que nunca existió.

`envs/prod/` se agrega cuando el proyecto esté listo para lanzarse, reutilizando los mismos
módulos con otras variables: balanceador, Cloud SQL, `min-instances=1` y Secret Manager con
valores de verdad. Dev es el ensayo de eso, no un juguete aparte.

## El arranque en frío

Para guardar el estado hace falta el bucket, y para crear el bucket con Terraform haría falta un
estado. El círculo se rompe una vez, con un script idempotente:

```
node infra/dev/bucket-estado.mjs
```

## Estado

Remoto, en `gs://tecnosport-dev-estado-terraform`, con versionado y acceso público bloqueado.
Nunca local, nunca versionado en git. El versionado no es comodidad: es lo único que separa un
`apply` equivocado de perder el estado, y un estado perdido deja la infraestructura viva con
Terraform convencido de que no existe nada.

## Uso

```
cd envs/dev
terraform init
terraform plan
terraform apply
```

`apply` es manual y deliberado.

## Lo que Terraform no administra, a propósito

- **La base de datos.** En dev es Neon, fuera de GCP, porque Cloud SQL no tiene capa gratuita.
- **El bucket de imágenes de local y su cuenta**, que crea `local/bucket-imagenes.mjs`. No es
  un ambiente desplegado: es la máquina de quien programa, y cada quien crea el suyo. El del
  ambiente desplegado sí está aquí (`ADR-0058`).
- **Los valores de los secretos.** Terraform crea el recipiente en Secret Manager y nunca ve el
  contenido: lo que se le pasa por variable acaba escrito en el estado, y el estado está en un
  bucket. Las versiones se cargan con `gcloud secrets versions add`.

## Reiniciar un servicio sin dejar deriva

`gcloud run services update ... --update-labels reinicio=r2` reinicia, sí, y deja una etiqueta
puesta a mano que **no se va nunca sola**. La que hubo en la API de dev sobrevivió once despliegues
—de la generación 78 a la 89— porque `gcloud run deploy` conserva las etiquetas del servicio: no
fue que nadie aplicara Terraform, fue que nada la iba a quitar.

Para forzar una revisión nueva sin residuo se vuelve a desplegar la imagen que ya corre:

```
imagen=$(gcloud run services describe tecnosport-api --region us-east1 --format='value(spec.template.spec.containers[0].image)')
gcloud run deploy tecnosport-api --region us-east1 --image "$imagen"
```

`spec.template.spec.containers` y no `template.containers`: `describe` devuelve el objeto al estilo
Knative, no la forma de la API v2 que usa Terraform. El mismo detalle que ya tropezó el paso de
migraciones del despliegue.

### Las etiquetas del servicio no salen en el `plan`

Las dos etiquetas de un servicio de Cloud Run se comportan al revés una de la otra:

- **`template.labels` es autoritativo**, como cualquier campo normal: lo que no esté en el código
  sale en el `plan` y el `apply` lo retira.
- **`labels` del servicio no lo es.** El provider solo administra las llaves escritas en el código,
  así que una agregada por fuera se queda para siempre y **ningún `terraform plan` la menciona**
  —el mismo trampolín que `google_storage_bucket_iam_member`, que solo añade y nunca quita, y esta
  vez sin un `_binding` al que cambiarse: el campo no tiene modo autoritativo.

Para ver lo que la mano dejó y el `plan` esconde, en `envs/dev`:

```
terraform plan -refresh-only
```

Ahí aparece dentro de `effective_labels`. Quitarlo exige gcloud —`--remove-labels reinicio`, nunca
`--clear-labels`, que se llevaría también `goog-terraform-provisioned`, que sí es de Terraform— y
después un `apply`, para que Terraform tenga la última palabra. Cerrado así el 23 de septiembre de
2026.

## Autenticación

GitHub Actions se autentica con Workload Identity Federation. No existe ninguna llave JSON de
cuenta de servicio guardada como secreto — una llave filtrada sirve para siempre y desde cualquier
parte; un token de federación vale unos minutos y solo para ese repositorio.

La condición del proveedor (`assertion.repository == "cy-perez/tecnosport"`) **es la seguridad
entera**: sin ella, cualquier repositorio de GitHub del mundo puede pedir un token de este
proveedor.

## Antes de tocar el DNS

`tecnosport.co` está en GoDaddy y el correo del negocio depende de sus registros MX. Al apuntar el
dominio a GCP se cambian solo los registros A, AAAA y el CNAME de www. Los MX, el SPF y el DMARC
no se tocan — ya se perdió el SPF una vez editando la zona, y el detalle de qué hay ahí y por qué
importa está en `docs/07-infra-gcp.md`.
