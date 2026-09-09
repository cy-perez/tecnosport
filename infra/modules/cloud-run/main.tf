# Un servicio de Cloud Run del proyecto. Existe como módulo porque hay dos usos reales —la API y
# la web— y va a haber un tercero y un cuarto cuando exista producción. Lo que no tiene dos usos
# todavía se queda escrito en el entorno, sin envolver: un módulo con un solo llamador es una capa
# de indirección que no compró nada.

terraform {
  required_version = ">= 1.9"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.0"
    }
  }
}

resource "google_cloud_run_v2_service" "este" {
  name                = var.nombre
  location            = var.region
  deletion_protection = false

  template {
    service_account = var.cuenta_de_servicio

    # Cero instancias en reposo. No es una preferencia: una instancia siempre encendida son ~2,59
    # millones de vCPU-segundos al mes contra los 180.000 de la capa gratuita. En dev se paga con
    # arranque en frío; en producción, con dinero.
    scaling {
      min_instance_count = var.instancias_minimas
      max_instance_count = var.instancias_maximas
    }

    containers {
      image = var.imagen

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memoria
        }
        # El arranque en frío de una JVM con CPU limitada es doloroso; este impulso solo se cobra
        # durante el arranque.
        startup_cpu_boost = true
      }

      dynamic "env" {
        for_each = var.variables
        content {
          name  = env.key
          value = env.value
        }
      }

      # Los secretos entran por referencia, nunca por valor: así no viajan en el estado de
      # Terraform, que vive en un bucket. Las versiones las carga una persona con gcloud.
      dynamic "env" {
        for_each = var.secretos
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value
              version = "latest"
            }
          }
        }
      }

      ports {
        container_port = 8080
      }

      startup_probe {
        http_get {
          path = var.ruta_de_salud
        }
        # Una JVM tarda en levantar y Cloud Run mata lo que no responde. Con margen: el fallo aquí
        # se ve como un despliegue que "no sirve" sin más explicación.
        initial_delay_seconds = 10
        period_seconds        = 5
        failure_threshold     = 30
        timeout_seconds       = 3
      }
    }
  }

  lifecycle {
    # La imagen la mueve el despliegue, no Terraform. Sin esto, cada `apply` devolvería el
    # servicio a la imagen que diga el código y desharía el último despliegue en silencio.
    ignore_changes = [
      template[0].containers[0].image,
      client,
      client_version,
    ]
  }
}

# El sitio es público: cualquiera puede pedirlo sin credenciales. Es una tienda.
resource "google_cloud_run_v2_service_iam_member" "publico" {
  count    = var.publico ? 1 : 0
  project  = google_cloud_run_v2_service.este.project
  location = google_cloud_run_v2_service.este.location
  name     = google_cloud_run_v2_service.este.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
