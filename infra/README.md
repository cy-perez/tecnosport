# infra

Infraestructura de TecnoSport en Google Cloud Platform, definida con Terraform.

Nada se crea a mano en la consola. Si existe en producción y no está aquí, no
existe.

## Estructura

```
modules/cloud-run/    servicios de API y web
modules/cloud-sql/    PostgreSQL 16
modules/storage/      buckets de imágenes y de estado
modules/network/      balanceador, certificados, dominio
modules/secrets/      Secret Manager
envs/prod/            composición del entorno de producción
```

Staging se agrega cuando el tráfico lo justifique: los módulos ya están
parametrizados para eso.

## Estado

Remoto, en un bucket de GCS con versionado y bloqueo. Nunca local, nunca
versionado en git.

## Uso

```
cd envs/prod
terraform init
terraform plan
terraform apply
```

`plan` corre automáticamente en cada pull request. `apply` es manual y
deliberado.

## Autenticación

GitHub Actions se autentica con Workload Identity Federation. No existe ninguna
llave JSON de cuenta de servicio guardada como secreto.

## Antes de tocar el DNS

`tecnosport.co` está en GoDaddy con cPanel y el correo del negocio depende de sus
registros MX. Al apuntar el dominio a GCP se cambian solo los registros A, AAAA y
el CNAME de www. Los MX, SPF, DKIM y DMARC no se tocan. Detalle en
`docs/07-infra-gcp.md`.
