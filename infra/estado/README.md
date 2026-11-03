# infra/estado

El arranque en frío que Terraform no puede hacerse a sí mismo: el bucket donde vive su estado.
Para guardar el estado hace falta el bucket, y para crear el bucket con Terraform haría falta un
estado. El círculo se rompe una vez por ambiente, con un script idempotente.

```
node infra/estado/bucket-estado.mjs dev
node infra/estado/bucket-estado.mjs prod
```

Crea `gs://tecnosport-<ambiente>-estado-terraform` en el proyecto de ese ambiente, con versionado y
acceso público bloqueado, y con eso `terraform init` en `infra/envs/<ambiente>` ya tiene dónde
guardar. El ambiente no tiene valor por omisión: olvidar el argumento no crea nada.

Hasta el 3 de noviembre de 2026 esto vivía en `infra/dev/` y solo servía para dev. Ahí estuvo
también el bucket de imágenes, que se partió en dos (`ADR-0058`): el de cada ambiente desplegado lo
declara Terraform en `infra/envs/<ambiente>`, y el de la máquina de quien programa lo crea
`infra/local/bucket-imagenes.mjs`.
