# infra/dev

Lo mínimo de GCP que el entorno de desarrollo necesita para funcionar de verdad.

Producción se define con Terraform y no se toca a mano — esa regla, la del
`README.md` de arriba, no cambia. Y **dev tampoco, ya no**: desde que el ambiente
de desarrollo se despliega (`infra/envs/dev`), lo administra Terraform como
cualquier otro.

Lo que queda aquí es solo el arranque en frío, que Terraform no puede hacerse a
sí mismo: el bucket donde vive su estado. Cuando este directorio era "un bucket y
una cuenta de servicio" no valía la pena montar Terraform para eso; con dos
servicios de Cloud Run, un registro de imágenes, identidades y federación con
GitHub, sí. El razonamiento cambió porque cambió lo que hay.

**El bucket de imágenes ya no está aquí, y se partió en dos** (`ADR-0058`): el del
ambiente desplegado lo declara Terraform en `infra/envs/dev`, y el de esta máquina
lo crea `infra/local/bucket-imagenes.mjs`. Mientras fue uno solo lo compartían los
dos ambientes, que es de donde salieron 348 objetos de local dentro del bucket de
dev y un informe de huérfanos que tuvo que aprender a distinguirlos.

## El bucket del estado de Terraform

```
node infra/dev/bucket-estado.mjs
```

Idempotente. Crea `gs://tecnosport-dev-estado-terraform` con versionado y acceso
público bloqueado, y con eso `terraform init` en `infra/envs/dev` ya tiene dónde
guardar.
