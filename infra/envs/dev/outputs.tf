output "url_api" {
  value = module.api.url
}

output "url_web" {
  description = "Tras el primer apply, este valor va a `dominio_publico_web` y se vuelve a aplicar: los canónicos, el hreflang y el sitemap necesitan el nombre público del sitio, y no existe hasta que Cloud Run crea el servicio."
  value       = module.web.url
}

output "proveedor_identidad_github" {
  description = "Para el flujo de despliegue: es el `workload_identity_provider` de google-github-actions/auth."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "cuenta_de_despliegue" {
  value = google_service_account.despliegue.email
}

output "registro_de_imagenes" {
  value = "${var.region}-docker.pkg.dev/${var.proyecto}/${google_artifact_registry_repository.contenedores.repository_id}"
}
