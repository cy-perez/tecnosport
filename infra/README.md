# infra

Infraestructura de TecnoSport en Google Cloud Platform, definida con Terraform.

Nada se crea a mano en la consola. Si existe y no está aquí, no existe.

## Estructura

```
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
- **El bucket de imágenes y su cuenta**, creados por `dev/bucket-imagenes.mjs` antes de que
  existiera este Terraform. Se les dan permisos desde aquí; no se administran desde aquí.
- **Los valores de los secretos.** Terraform crea el recipiente en Secret Manager y nunca ve el
  contenido: lo que se le pasa por variable acaba escrito en el estado, y el estado está en un
  bucket. Las versiones se cargan con `gcloud secrets versions add`.

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
