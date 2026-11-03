output "origen_api" {
  description = "La URL determinista de la API. De ella cuelgan los webhooks que se registran en los paneles de Wompi y de Skydropx, la confirmación de Sistecrédito, el tick y la ingesta."
  value       = local.origen_api
}

output "url_web" {
  value = module.web.url
}

output "registros_dns" {
  description = "Lo que hay que poner en GoDaddy para la raíz y para `www`. Solo esos: los MX y los TXT del correo no se tocan (docs/07-infra-gcp.md, DNS)."
  value = {
    for nombre, mapeo in google_cloud_run_domain_mapping.web :
    nombre => mapeo.status[0].resource_records
  }
}

output "proveedor_identidad_github" {
  description = "Va en la variable GCP_PROVEEDOR_IDENTIDAD_PROD de GitHub."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "cuenta_de_despliegue" {
  description = "Va en la variable GCP_CUENTA_DESPLIEGUE_PROD de GitHub, y en `cuenta_despliegue_prod` de dev para que pueda leer el registro de allá."
  value       = google_service_account.despliegue.email
}

output "registro_de_imagenes" {
  description = "Va en la variable GCP_REGISTRO_IMAGENES_PROD de GitHub."
  value       = "${var.region}-docker.pkg.dev/${var.proyecto}/${google_artifact_registry_repository.contenedores.repository_id}"
}

output "conexion_de_la_base" {
  description = "Para `gcloud sql users create` y para conectarse desde Cloud SQL Studio."
  value       = google_sql_database_instance.principal.connection_name
}
