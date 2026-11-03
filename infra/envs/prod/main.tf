# Ambiente de producción: tecnosport.co (ADR-0105).
#
# Las mismas piezas de dev, que es su ensayo, con tres diferencias que importan:
#   - La base es Cloud SQL con IP privada, y la API llega a ella por Direct VPC egress.
#   - Las migraciones las corre un Cloud Run Job dentro de esa red, porque el runner de GitHub no
#     llega a una base sin IP pública. La cuenta de despliegue no conoce la clave de la base.
#   - El dominio se mapea a la web sin balanceador. La web reenvía `/api` a la API por su proxy
#     (`apps/web/src/proxy-api.ts`), así que un solo origen sirve las dos y la cookie de sesión no
#     cruza dominios.
#
# Lo que NO está aquí, a propósito:
#   - Los valores de los secretos, el usuario de la base y su clave: los carga una persona con
#     gcloud, para que nunca queden escritos en el estado.
#   - Los registros DNS: viven en GoDaddy, junto con los MX del correo del negocio, que no se tocan.
#     El output `registros_dns` dice qué poner.

data "google_project" "actual" {}

# ── APIs ────────────────────────────────────────────────────────────────────────────────────────
resource "google_project_service" "apis" {
  for_each = toset([
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "cloudtasks.googleapis.com",
    "cloudscheduler.googleapis.com",
    "compute.googleapis.com",
    "servicenetworking.googleapis.com",
    "sqladmin.googleapis.com",
    "monitoring.googleapis.com",
    "billingbudgets.googleapis.com",
  ])
  service            = each.value
  disable_on_destroy = false
}

locals {
  # La URL determinista de la API, que se sabe antes de crear el servicio: sale del número del
  # proyecto. En dev la URL pública va por variable y se llena después del primer apply, porque
  # `module.api.url` dentro del propio módulo es un ciclo; aquí no hace falta el segundo apply.
  # Cloud Run atiende las dos URL del servicio, y los tokens del tick y de la ingesta se emiten
  # para esta.
  origen_api = "https://tecnosport-api-${data.google_project.actual.number}.${var.region}.run.app"
  origen_web = "https://${var.dominio}"

  # Imagen de arranque de Google: el servicio tiene que existir antes que una imagen nuestra, y el
  # despliegue la reemplaza (`ignore_changes` en el módulo).
  imagen_de_arranque = "us-docker.pkg.dev/cloudrun/container/hello"
}

# ── Registro de imágenes ────────────────────────────────────────────────────────────────────────
# Producción no construye: el flujo copia aquí la imagen del SHA que ya corrió en dev. Copiarla y
# no leerla del registro de dev porque allá se borra lo de más de siete días, y una revisión cuya
# imagen ya no existe no se puede restaurar.
resource "google_artifact_registry_repository" "contenedores" {
  location      = var.region
  repository_id = "contenedores"
  format        = "DOCKER"
  description   = "Imágenes de la API, de la web y de las migraciones de producción, copiadas de dev."

  # Diez versiones de cada imagen se quedan pase lo que pase: son las revisiones a las que se puede
  # volver. Lo demás se borra a los 30 días.
  cleanup_policies {
    id     = "conservar-las-ultimas"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }

  cleanup_policies {
    id     = "borrar-lo-viejo"
    action = "DELETE"
    condition {
      older_than = "2592000s" # 30 días
    }
  }

  depends_on = [google_project_service.apis]
}

# ── Red privada y base de datos ─────────────────────────────────────────────────────────────────
# La base no tiene IP pública. Se llega a ella por esta red, con dos piezas que no cobran: el
# acceso a servicios privados, que le da a Cloud SQL una IP dentro de ella, y una subred para el
# Direct VPC egress de Cloud Run. Sin conector de VPC, que sí cobra por instancia encendida.
resource "google_compute_network" "principal" {
  name                    = "tecnosport"
  auto_create_subnetworks = false
  depends_on              = [google_project_service.apis]
}

# Un /24 da para el Direct VPC egress de sobra: Cloud Run toma dos IP por instancia, y la API y
# el job de migraciones juntos no pasan de cuatro instancias.
resource "google_compute_subnetwork" "servicios" {
  name                     = "servicios-${var.region}"
  region                   = var.region
  network                  = google_compute_network.principal.id
  ip_cidr_range            = "10.10.0.0/24"
  private_ip_google_access = true
}

resource "google_compute_global_address" "servicios_de_google" {
  name          = "rango-servicios-de-google"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 20
  network       = google_compute_network.principal.id
}

# `ABANDON` al destruir: borrar el emparejamiento con una instancia de Cloud SQL viva falla, y
# dejaría el estado a medias.
resource "google_service_networking_connection" "servicios_de_google" {
  network                 = google_compute_network.principal.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.servicios_de_google.name]
  deletion_policy         = "ABANDON"
}

# La instancia más pequeña de la edición Enterprise, que es la única que tiene `db-f1-micro`. Una
# sola zona: con poco tráfico, la alta disponibilidad duplicaría el costo para cubrir una caída
# de zona que el respaldo y la recuperación a un instante dado ya cubren con unos minutos de corte.
resource "google_sql_database_instance" "principal" {
  name             = "tecnosport-principal"
  database_version = "POSTGRES_16"
  region           = var.region

  # Las dos protecciones: la de Terraform, que impide que un `apply` la reemplace o la destruya, y
  # la de Cloud SQL, que impide borrarla desde la consola o con gcloud.
  deletion_protection = true

  settings {
    tier              = "db-f1-micro"
    edition           = "ENTERPRISE"
    availability_type = "ZONAL"
    disk_type         = "PD_SSD"
    disk_size         = 10
    # Crece sola si se llena, con techo: un disco lleno tumba la base, y uno sin techo cobra sin
    # avisar.
    disk_autoresize       = true
    disk_autoresize_limit = 50

    deletion_protection_enabled = true

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.principal.id
      # TLS obligatorio también dentro de la red privada. La API conecta con `sslmode=require`.
      ssl_mode = "ENCRYPTED_ONLY"
    }

    # Respaldo diario a las 3 de la madrugada de Colombia y 30 guardados, lo que pide
    # `docs/07-infra-gcp.md` (Respaldos). El registro de transacciones permite volver a cualquier
    # instante de los últimos siete días, que es el máximo de esta edición.
    backup_configuration {
      enabled                        = true
      start_time                     = "08:00"
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = 7
      backup_retention_settings {
        retained_backups = 30
      }
    }

    # Domingo a las 3 de la madrugada de Colombia.
    maintenance_window {
      day          = 7
      hour         = 8
      update_track = "stable"
    }
  }

  depends_on = [google_service_networking_connection.servicios_de_google]
}

resource "google_sql_database" "tecnosport" {
  name     = "tecnosport"
  instance = google_sql_database_instance.principal.name
}

# ── Identidades ─────────────────────────────────────────────────────────────────────────────────
# Una cuenta por servicio, como en dev. La de por omisión del proyecto es editora de todo.
resource "google_service_account" "api" {
  account_id   = "tecnosport-api"
  display_name = "Servicio de la API en Cloud Run"
  depends_on   = [google_project_service.apis]
}

resource "google_service_account" "web" {
  account_id   = "tecnosport-web"
  display_name = "Servicio de la web con SSR en Cloud Run"
  depends_on   = [google_project_service.apis]
}

# La del job de migraciones. Aparte de la API: lee `db-clave` y nada más.
resource "google_service_account" "migraciones" {
  account_id   = "tecnosport-migraciones"
  display_name = "Migraciones de la base (Cloud Run Job)"
  depends_on   = [google_project_service.apis]
}

resource "google_service_account" "despliegue" {
  account_id   = "tecnosport-despliegue"
  display_name = "Despliegues desde GitHub Actions"
  depends_on   = [google_project_service.apis]
}

# La API firma las URL de subida de imágenes sin llave JSON: le pide a IAM que firme por ella.
resource "google_service_account_iam_member" "api_firma_por_si_misma" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.api.email}"
}

# ── Cola de la ingesta de proveedores (ADR-0080) ────────────────────────────────────────────────
# La misma de dev: un tramo a la vez y diez intentos con espera creciente. Las razones están allá.
resource "google_cloud_tasks_queue" "ingestas" {
  name     = "ingestas-proveedores"
  location = var.region

  rate_limits {
    max_concurrent_dispatches = 1
  }

  retry_config {
    max_attempts  = 10
    min_backoff   = "10s"
    max_backoff   = "300s"
    max_doublings = 4
  }

  depends_on = [google_project_service.apis]
}

resource "google_service_account" "tareas_de_ingesta" {
  account_id   = "tecnosport-ingesta-tareas"
  display_name = "Tareas de la ingesta de proveedores (Cloud Tasks)"
  depends_on   = [google_project_service.apis]
}

resource "google_cloud_tasks_queue_iam_member" "api_encola_ingestas" {
  name     = google_cloud_tasks_queue.ingestas.name
  location = google_cloud_tasks_queue.ingestas.location
  role     = "roles/cloudtasks.enqueuer"
  member   = "serviceAccount:${google_service_account.api.email}"
}

resource "google_service_account_iam_member" "api_actua_como_tareas_de_ingesta" {
  service_account_id = google_service_account.tareas_de_ingesta.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.api.email}"
}

# ── El tick de las tareas programadas (ADR-0104) ────────────────────────────────────────────────
# Cada minuto. Con CPU solo durante la petición, unas 43.000 llamadas al mes de uno o dos segundos
# quedan dentro de la capa gratuita de CPU de Cloud Run. Aquí no hay que esperar el primer apply
# para tener a dónde llamar: el origen de la API es el determinista.
resource "google_service_account" "tareas_programadas" {
  account_id   = "tecnosport-tareas-programadas"
  display_name = "Tick de las tareas programadas (Cloud Scheduler)"
  depends_on   = [google_project_service.apis]
}

resource "google_cloud_scheduler_job" "tick" {
  name      = "tareas-tick"
  region    = var.region
  schedule  = var.frecuencia_del_tick
  time_zone = "America/Bogota"

  attempt_deadline = "1800s"

  # Sin reintentos —el tick siguiente hace lo mismo un minuto después—, que es lo que Cloud
  # Scheduler hace por omisión. No se declara el bloque: la API no lo devuelve con su valor por
  # omisión, y el plan lo propondría en cada corrida (envs/dev).

  http_target {
    http_method = "POST"
    uri         = "${local.origen_api}/api/v1/internal/tareas/tick"
    oidc_token {
      service_account_email = google_service_account.tareas_programadas.email
      audience              = local.origen_api
    }
  }

  depends_on = [google_project_service.apis]
}

# ── Buckets ─────────────────────────────────────────────────────────────────────────────────────
# Los mismos dos de dev, con el origen de este sitio. Las razones de cada bloque están allá.
resource "google_storage_bucket" "imagenes" {
  name          = var.bucket_imagenes
  location      = "US-EAST1"
  storage_class = "STANDARD"

  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  # TODO(negocio): DIAS_RETENCION_VERSIONES_IMAGEN. El mismo de dev: 30 días para darse cuenta de
  # que se borró una imagen por error es un valor de arranque, no una decisión tomada.
  lifecycle_rule {
    action {
      type = "Delete"
    }
    condition {
      days_since_noncurrent_time = 30
    }
  }

  # `www` también: el sitio responde en las dos, y el panel puede abrirse desde cualquiera.
  cors {
    origin          = [local.origen_web, "https://www.${var.dominio}"]
    method          = ["GET", "HEAD", "PUT"]
    response_header = ["Content-Type"]
    max_age_seconds = 3600
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.apis]
}

resource "google_storage_bucket_iam_binding" "imagenes_lectura_publica" {
  bucket  = google_storage_bucket.imagenes.name
  role    = "roles/storage.objectViewer"
  members = ["allUsers"]
}

resource "google_storage_bucket_iam_binding" "api_escribe_imagenes" {
  bucket  = google_storage_bucket.imagenes.name
  role    = "roles/storage.objectAdmin"
  members = ["serviceAccount:${google_service_account.api.email}"]
}

resource "google_storage_bucket" "proveedores" {
  name          = var.bucket_proveedores
  location      = "US-EAST1"
  storage_class = "STANDARD"

  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  cors {
    origin          = [local.origen_web, "https://www.${var.dominio}"]
    method          = ["GET", "HEAD", "PUT"]
    response_header = ["Content-Type"]
    max_age_seconds = 3600
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.apis]
}

resource "google_storage_bucket_iam_binding" "api_administra_proveedores" {
  bucket  = google_storage_bucket.proveedores.name
  role    = "roles/storage.objectAdmin"
  members = ["serviceAccount:${google_service_account.api.email}"]
}

# ── Federación de identidad con GitHub ──────────────────────────────────────────────────────────
resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"
  depends_on                = [google_project_service.apis]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub Actions OIDC"

  attribute_mapping = {
    "google.subject"        = "assertion.sub"
    "attribute.repository"  = "assertion.repository"
    "attribute.ref"         = "assertion.ref"
    "attribute.environment" = "assertion.environment"
  }

  # Un tercer filtro sobre los dos de dev: el ambiente de GitHub. El token solo trae `environment`
  # si el trabajo lo declara, y declararlo hace que GitHub espere la aprobación de quien revisa
  # ese ambiente. Sin esta condición, un flujo de `main` que no lo declarara desplegaría aquí sin
  # que nadie lo aprobara.
  attribute_condition = join(" && ", [
    "assertion.repository == \"${var.repositorio_github}\"",
    "assertion.ref == \"refs/heads/main\"",
    "assertion.environment == \"${var.ambiente_github}\"",
  ])

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account_iam_member" "github_usa_la_cuenta" {
  service_account_id = google_service_account.despliegue.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.repositorio_github}"
}

locals {
  permisos_de_despliegue = [
    "roles/artifactregistry.writer", # subir las imágenes copiadas de dev
    "roles/run.developer",           # desplegar revisiones, y actualizar y ejecutar el job
  ]
}

resource "google_project_iam_member" "despliegue" {
  for_each = toset(local.permisos_de_despliegue)
  project  = var.proyecto
  role     = each.value
  member   = "serviceAccount:${google_service_account.despliegue.email}"
}

resource "google_service_account_iam_member" "despliegue_actua_como" {
  for_each = {
    api         = google_service_account.api.name
    web         = google_service_account.web.name
    migraciones = google_service_account.migraciones.name
  }
  service_account_id = each.value
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.despliegue.email}"
}

# ── Secretos ────────────────────────────────────────────────────────────────────────────────────
# Solo el recipiente; el valor se carga aparte:
#   gcloud secrets versions add db-clave --data-file=- --project=tecnosport-prod
# La lista es la de dev, y también aquí es la que concede el acceso de la API. El de WhatsApp no
# está: en dev es un recipiente reservado para un adaptador que no existe.
locals {
  secretos_api = [
    "db-clave",
    "jwt-secreto",
    "smtp-clave",
    "admin-clave",
    "wompi-secreto-eventos",
    "wompi-secreto-integridad",
    "skydropx-client-id",
    "skydropx-client-secret",
    "skydropx-secreto-webhook",
    "sistecredito-llave-suscripcion",
    "sistecredito-store-id",
    "sistecredito-vendor-id",
    "meta-token",
    "anthropic-api-key",
    "proxy-web-clave",
    # **Perderla es perder todos los segundos factores** (ADR-0096). No se rota sin
    # `infra/operacion/restablecer-segundo-factor.sql`.
    "segundo-factor-llave",
  ]
}

resource "google_secret_manager_secret" "api" {
  for_each  = toset(local.secretos_api)
  secret_id = each.value

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_iam_member" "api_lee_sus_secretos" {
  for_each  = google_secret_manager_secret.api
  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}

resource "google_secret_manager_secret_iam_member" "web_lee_la_clave_del_proxy" {
  secret_id = google_secret_manager_secret.api["proxy-web-clave"].id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.web.email}"
}

# Las migraciones leen la clave de la base, y la cuenta de despliegue no: en dev sí, porque allá
# migra el runner (ver el comentario de `despliegue_lee_la_clave_de_la_base` en envs/dev).
resource "google_secret_manager_secret_iam_member" "migraciones_leen_la_clave_de_la_base" {
  secret_id = google_secret_manager_secret.api["db-clave"].id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.migraciones.email}"
}

# ── Servicios ───────────────────────────────────────────────────────────────────────────────────
locals {
  # `sslmode=require` cifra sin verificar el certificado del servidor: dentro de la red privada,
  # quien podría suplantarlo ya tendría la red. Verificarlo pediría montar la CA de la instancia.
  url_jdbc = "jdbc:postgresql://${google_sql_database_instance.principal.private_ip_address}:5432/${google_sql_database.tecnosport.name}?sslmode=require"

  red_privada = {
    red    = google_compute_network.principal.id
    subred = google_compute_subnetwork.servicios.id
  }
}

module "api" {
  source = "../../modules/cloud-run"

  nombre             = "tecnosport-api"
  region             = var.region
  imagen             = local.imagen_de_arranque
  cuenta_de_servicio = google_service_account.api.email
  red_privada        = local.red_privada

  # 3Gi por la ingesta de proveedores, que corre en producción: el lector tiene en memoria el zip,
  # su copia en /tmp y lo descomprimido (ver envs/dev). Se cobra solo mientras atiende.
  memoria = "3Gi"

  variables = merge({
    APP_URL_PUBLICA        = local.origen_web
    GCS_BUCKET_IMAGENES    = var.bucket_imagenes
    GCS_BUCKET_PROVEEDORES = var.bucket_proveedores

    DB_HOST    = google_sql_database_instance.principal.private_ip_address
    DB_NOMBRE  = google_sql_database.tecnosport.name
    DB_USUARIO = var.db_usuario
    DB_PARAMS  = "?sslmode=require"
    # `db-f1-micro` admite 25 conexiones. Con el grupo de 10 que trae Hikari por omisión, tres
    # instancias pedirían 30 y la tercera no arrancaría; con 5 son 15 y quedan las del job de
    # migraciones y las de una consulta a mano.
    # Sin guiones en el nombre: así lee Spring `maximum-pool-size` desde el entorno.
    SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE = "5"

    PROVEEDORES_EXPORTACION_MAXIMA_BYTES   = "419430400"
    PROVEEDORES_DESCOMPRIMIDO_MAXIMO_BYTES = "419430400"

    SMTP_HOST    = "smtp.resend.com"
    SMTP_PUERTO  = "587"
    SMTP_AUTH    = "true"
    SMTP_USUARIO = "resend"
    # Una sola dirección para todo el sitio, decidido el 3 de noviembre de 2026: las respuestas a
    # un correo del sitio llegan al buzón del negocio. Resend envía desde la raíz verificada sin
    # tocar los MX ni el SPF del ápice: su retorno va en `send.tecnosport.co` (docs/07, Correo).
    CORREO_REMITENTE = var.correo_admin
    ADMIN_CORREO     = var.correo_admin

    WOMPI_AMBIENTE = "produccion"
    # La tarifa de dev, leída en el portal el 26 de octubre de 2026. TODO(negocio):
    # WOMPI_COMISION_PRODUCCION. Confirmar el plan de la cuenta de producción antes de abrir.
    WOMPI_COMISION_VARIABLE = "0.0265"
    WOMPI_COMISION_FIJA     = "700"
    WOMPI_COMISION_IVA      = "0.19"

    # El host de producción de Skydropx, dicho por su documentación (docs/13 §6.3) y nunca
    # ejercido: la primera cotización real lo confirma.
    SKYDROPX_URL_BASE = "https://api-pro.skydropx.com"

    # La publicación en redes sale de aquí y ya no de dev (3 de noviembre de 2026). Atada al
    # token: encenderla sin él no arranca, porque `PropiedadesMeta` lo exige.
    META_PUBLICAR_DE_VERDAD = tostring(var.meta_listo)

    # Las migraciones las corre el job, antes de mover la revisión. Hibernate valida el esquema
    # al arrancar, así que una revisión con el job saltado falla en voz alta.
    SPRING_FLYWAY_ENABLED = "false"
    # Sin perfil: no siembra catálogo, y el freno de Sistecrédito no deja encender el sandbox.

    # Decidido el 3 de noviembre de 2026: la contraentrega espera las condiciones de Skydropx
    # (ADR-0023); la recogida en el punto abre con el sitio.
    CONTRAENTREGA_HABILITADA   = "false"
    RETIRO_EN_PUNTO_HABILITADO = "true"

    ADMIN_EXIGE_SEGUNDO_FACTOR = "true"
    GOOGLE_CLIENT_ID           = var.google_client_id

    TRANSFERENCIA_NEQUI_NUMERO      = var.cuentas_de_transferencia.nequi.numero
    TRANSFERENCIA_NEQUI_TITULAR     = var.cuentas_de_transferencia.nequi.titular
    TRANSFERENCIA_DAVIPLATA_NUMERO  = var.cuentas_de_transferencia.daviplata.numero
    TRANSFERENCIA_DAVIPLATA_TITULAR = var.cuentas_de_transferencia.daviplata.titular
    TRANSFERENCIA_BBVA_NUMERO       = var.cuentas_de_transferencia.bbva.numero
    TRANSFERENCIA_BBVA_TITULAR      = var.cuentas_de_transferencia.bbva.titular

    PROVEEDORES_COLA_DE_TAREAS          = "projects/${var.proyecto}/locations/${var.region}/queues/${google_cloud_tasks_queue.ingestas.name}"
    PROVEEDORES_COLA_URL_TRAMOS         = "${local.origen_api}/api/v1/internal/ingestas"
    PROVEEDORES_COLA_CUENTA_DE_SERVICIO = google_service_account.tareas_de_ingesta.email
    PROVEEDORES_COLA_AUDIENCIA          = local.origen_api

    TAREAS_DISPARADOR         = "externo"
    TAREAS_CUENTA_DE_SERVICIO = google_service_account.tareas_programadas.email
    TAREAS_AUDIENCIA          = local.origen_api

    SISTECREDITO_HABILITADO       = tostring(var.sistecredito_listo)
    SISTECREDITO_SANDBOX_ACTIVO   = "false"
    SISTECREDITO_MONTO_MINIMO     = "50000"
    SISTECREDITO_URL_CONFIRMACION = "${local.origen_api}/api/v1/pagos/sistecredito/confirmacion"
    }, var.wompi_llave_publica == "" ? {} : {
    # Vacía no se manda: el valor por omisión de `application.yml` es un relleno que arranca, y una
    # cadena vacía lo pisaría.
    WOMPI_LLAVE_PUBLICA = var.wompi_llave_publica
  })

  # Con `secretos_cargados` en false la API no arranca, y a propósito: sin JWT, sin la clave de
  # administración y sin los secretos de Wompi no hay nada que servir. El primer apply la deja con
  # la imagen de arranque de Google, que no los pide.
  secretos = merge(var.secretos_cargados ? {
    DB_CLAVE                 = "db-clave"
    JWT_SECRETO              = "jwt-secreto"
    SMTP_CLAVE               = "smtp-clave"
    ADMIN_CLAVE              = "admin-clave"
    WOMPI_SECRETO_EVENTOS    = "wompi-secreto-eventos"
    WOMPI_SECRETO_INTEGRIDAD = "wompi-secreto-integridad"
    SKYDROPX_CLIENT_ID       = "skydropx-client-id"
    SKYDROPX_CLIENT_SECRET   = "skydropx-client-secret"
    SKYDROPX_SECRETO_WEBHOOK = "skydropx-secreto-webhook"
    PROXY_WEB_CLAVE          = "proxy-web-clave"
    SEGUNDO_FACTOR_LLAVE     = "segundo-factor-llave"
    } : {}, var.meta_listo ? {
    META_TOKEN = "meta-token"
    } : {}, var.ingesta_lista ? {
    ANTHROPIC_API_KEY = "anthropic-api-key"
    } : {}, var.sistecredito_listo ? {
    SISTECREDITO_SUBSCRIPTION_KEY = "sistecredito-llave-suscripcion"
    SISTECREDITO_STORE_ID         = "sistecredito-store-id"
    SISTECREDITO_VENDOR_ID        = "sistecredito-vendor-id"
  } : {})

  # Treinta minutos por los tramos de la ingesta y por el tick (ADR-0080, ADR-0104).
  tiempo_maximo_de_peticion = "1800s"

  # El permiso antes que el montaje: un secreto montado sin permiso deja la revisión sin arrancar
  # con un "Permission denied" que no dice que falta el binding.
  depends_on = [
    google_project_service.apis,
    google_secret_manager_secret_iam_member.api_lee_sus_secretos,
  ]
}

module "web" {
  source = "../../modules/cloud-run"

  nombre             = "tecnosport-web"
  region             = var.region
  imagen             = local.imagen_de_arranque
  cuenta_de_servicio = google_service_account.web.email
  variables = {
    API_URL_PUBLICA = "${module.api.url}/api/v1"
    APP_URL_PUBLICA = local.origen_web
    # El dominio y `www` ya están en `angular.json`; el de ensayo, para probar el sitio entero antes
    # de mover la raíz; `*.run.app`, para la comprobación de salud del despliegue.
    NG_ALLOWED_HOSTS       = "${var.dominio},www.${var.dominio},${local.subdominio_de_ensayo},*.run.app"
    NG_TRUST_PROXY_HEADERS = "forwarded,x-forwarded-proto,x-forwarded-host,x-forwarded-for"
  }

  secretos = var.secretos_cargados ? {
    PROXY_WEB_CLAVE = "proxy-web-clave"
  } : {}

  depends_on = [
    google_project_service.apis,
    google_secret_manager_secret_iam_member.web_lee_la_clave_del_proxy,
  ]
}

# ── Migraciones ─────────────────────────────────────────────────────────────────────────────────
# El flujo de despliegue cambia la imagen por la del SHA que despliega y lo ejecuta antes de mover
# las revisiones (`apps/api/migraciones/Dockerfile`). Nace con la imagen pública de Flyway, que sin
# archivos de migración no hace nada.
resource "google_cloud_run_v2_job" "migraciones" {
  name                = "tecnosport-migraciones"
  location            = var.region
  deletion_protection = false

  template {
    task_count = 1

    template {
      service_account = google_service_account.migraciones.email
      timeout         = "900s"
      # Una migración fallida no se reintenta sola: Flyway la deja marcada y la revisa una persona.
      max_retries = 0

      vpc_access {
        egress = "PRIVATE_RANGES_ONLY"
        network_interfaces {
          network    = local.red_privada.red
          subnetwork = local.red_privada.subred
        }
      }

      containers {
        image = "docker.io/flyway/flyway:12.4.0"

        env {
          name  = "FLYWAY_URL"
          value = local.url_jdbc
        }
        env {
          name  = "FLYWAY_USER"
          value = var.db_usuario
        }
        # Con la misma bandera que los secretos de la API: en el primer apply `db-clave` no tiene
        # versión todavía, y una referencia a `latest` de un secreto vacío no se sabe si la acepta
        # la creación del job. El job sin clave no puede migrar, pero tampoco hay qué migrar aún.
        dynamic "env" {
          for_each = var.secretos_cargados ? ["db-clave"] : []
          content {
            name = "FLYWAY_PASSWORD"
            value_source {
              secret_key_ref {
                secret  = google_secret_manager_secret.api[env.value].secret_id
                version = "latest"
              }
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      template[0].template[0].containers[0].image,
      client,
      client_version,
    ]
  }

  depends_on = [google_secret_manager_secret_iam_member.migraciones_leen_la_clave_de_la_base]
}

# ── Dominio ─────────────────────────────────────────────────────────────────────────────────────
# Mapeo de dominio de Cloud Run, sin balanceador (ADR-0105). Lo que se acepta con eso: está en
# preview y Google no lo da por listo para producción, no admite apagar TLS 1.0 y 1.1, no tiene
# CDN, y el certificado se emite después de que el DNS apunta aquí, entre 15 minutos y 24 horas.
#
# **Antes del apply**, quien aplica tiene que haber verificado `tecnosport.co` en Google Search
# Console con su propia cuenta: sin eso, Google no deja crear el mapeo.
#
# El de ensayo va primero en el DNS: un CNAME que no toca la raíz, con el que se comprueba que el
# certificado se emite y el sitio sirve antes de mover `tecnosport.co`, que es lo que pedía
# `docs/07-infra-gcp.md` del balanceador. Sirve para ver el sitio y su certificado, no para un
# recorrido entero: lo que arma la API —el regreso de Wompi y de Sistecrédito, los enlaces de los
# correos— apunta a la raíz, y el CORS de los buckets solo admite la raíz y `www`.
locals {
  subdominio_de_ensayo = "ensayo.${var.dominio}"
}

resource "google_cloud_run_domain_mapping" "web" {
  for_each = toset([var.dominio, "www.${var.dominio}", local.subdominio_de_ensayo])

  location = var.region
  name     = each.value

  metadata {
    namespace = var.proyecto
  }

  spec {
    route_name = module.web.nombre
  }
}

# ── Vigilancia ──────────────────────────────────────────────────────────────────────────────────
resource "google_monitoring_notification_channel" "correo" {
  display_name = "Correo de alertas"
  type         = "email"
  labels = {
    email_address = var.correo_alertas
  }
  depends_on = [google_project_service.apis]
}

# Un aviso, no un tope: nada se apaga al pasarlo. Crearlo pide permiso de administrador de la
# cuenta de facturación, y por eso sin la cuenta no se crea.
resource "google_billing_budget" "mensual" {
  count    = var.cuenta_de_facturacion == "" ? 0 : 1
  provider = google.facturacion

  billing_account = var.cuenta_de_facturacion
  display_name    = "TecnoSport producción, mensual"

  budget_filter {
    projects = ["projects/${data.google_project.actual.number}"]
  }

  # Sin `currency_code`: toma la moneda de la cuenta de facturación, que la API exige que coincida.
  # Una cuenta colombiana factura en pesos, y un "USD" aquí haría fallar el apply.
  amount {
    specified_amount {
      units = tostring(var.presupuesto_mensual)
    }
  }

  dynamic "threshold_rules" {
    for_each = [0.5, 0.9, 1.0]
    content {
      threshold_percent = threshold_rules.value
    }
  }

  all_updates_rule {
    monitoring_notification_channels = [google_monitoring_notification_channel.correo.id]
  }

  depends_on = [google_project_service.apis]
}

# Cada cinco minutos, desde todas las regiones de comprobación, contra la portada servida por el
# dominio; el aviso salta cuando falla desde más de una. Solo cuando el
# DNS ya apunta aquí: antes, fallaría en cada consulta y el aviso dejaría de leerse.
resource "google_monitoring_uptime_check_config" "portada" {
  count = var.dominio_activo ? 1 : 0

  display_name = "Portada de ${var.dominio}"
  timeout      = "30s"
  period       = "300s"

  http_check {
    path         = "/es"
    port         = 443
    use_ssl      = true
    validate_ssl = true
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.proyecto
      host       = var.dominio
    }
  }

  depends_on = [google_project_service.apis]
}

resource "google_monitoring_alert_policy" "sitio_caido" {
  count = var.dominio_activo ? 1 : 0

  display_name = "La portada de ${var.dominio} no responde"
  combiner     = "OR"

  conditions {
    display_name = "Falla desde más de una región"
    condition_threshold {
      filter          = "metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.label.check_id=\"${google_monitoring_uptime_check_config.portada[0].uptime_check_id}\" AND resource.type=\"uptime_url\""
      comparison      = "COMPARISON_GT"
      threshold_value = 1
      duration        = "600s"
      aggregations {
        alignment_period     = "1200s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.*"]
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.correo.id]
}
