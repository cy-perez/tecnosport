# Ambiente de desarrollo desplegado, entero, en la capa gratuita de GCP.
#
# Lo que NO está aquí, y es deliberado:
#   - La base de datos: es Neon, fuera de GCP, porque Cloud SQL no tiene capa gratuita.
#   - El bucket de imágenes y su cuenta: los creó `infra/dev/bucket-imagenes.mjs` antes de que
#     existiera este Terraform. Aquí solo se le dan permisos; no se administran desde aquí.
#   - Los valores de los secretos: se cargan con gcloud. Terraform crea el recipiente y nunca ve
#     el contenido, que si no acabaría escrito en el estado.

data "google_project" "actual" {}

# ── APIs ────────────────────────────────────────────────────────────────────────────────────────
# Habilitarlas es gratis; lo que se cobra es usarlas. `disable_on_destroy = false` a propósito:
# apagar una API al destruir un recurso puede tumbar algo que no es de este Terraform.
resource "google_project_service" "apis" {
  for_each = toset([
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "sts.googleapis.com",
    "cloudresourcemanager.googleapis.com",
  ])
  service            = each.value
  disable_on_destroy = false
}

# ── Registro de imágenes ────────────────────────────────────────────────────────────────────────
resource "google_artifact_registry_repository" "contenedores" {
  location      = var.region
  repository_id = "contenedores"
  format        = "DOCKER"
  description   = "Imágenes de la API y de la web para el ambiente de desarrollo."

  # La capa gratuita son 0,5 GB y dos imágenes con historial se la comen. Sin poda, esto empieza a
  # cobrar por guardar despliegues que nadie va a volver a mirar.
  cleanup_policies {
    id     = "conservar-las-ultimas"
    action = "KEEP"
    most_recent_versions {
      keep_count = 3
    }
  }

  cleanup_policies {
    id     = "borrar-lo-viejo"
    action = "DELETE"
    condition {
      older_than = "604800s" # 7 días
    }
  }

  depends_on = [google_project_service.apis]
}

# ── Identidades ─────────────────────────────────────────────────────────────────────────────────
# Una cuenta por servicio. La de por omisión del proyecto es editora de todo, y un servicio que
# solo sirve páginas no tiene por qué poder borrar un bucket.
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

resource "google_service_account" "despliegue" {
  account_id   = "tecnosport-despliegue"
  display_name = "Despliegues desde GitHub Actions"
  depends_on   = [google_project_service.apis]
}

# La API firma las URL de subida de imágenes. Con una llave JSON eso es trivial, y es justo lo que
# no queremos en la nube: sin llave, firmar exige que la cuenta pueda pedirle a IAM que firme por
# ella — sobre sí misma.
resource "google_service_account_iam_member" "api_firma_por_si_misma" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.api.email}"
}

resource "google_storage_bucket_iam_member" "api_escribe_imagenes" {
  bucket = var.bucket_imagenes
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.api.email}"
}

# ── Federación de identidad con GitHub ──────────────────────────────────────────────────────────
# Sin llaves JSON en los secretos de GitHub: una llave que se filtra sirve para siempre y desde
# cualquier parte. Aquí GitHub presenta un token firmado de esa ejecución y GCP lo cambia por
# credenciales de corta vida.
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
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # **Esta condición es la seguridad entera.** Sin ella, cualquier repositorio de GitHub del mundo
  # puede pedirle un token a este proveedor y desplegar en este proyecto.
  attribute_condition = "assertion.repository == \"${var.repositorio_github}\""

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# Y encima del filtro del proveedor, quién puede hacerse pasar por la cuenta de despliegue.
resource "google_service_account_iam_member" "github_usa_la_cuenta" {
  service_account_id = google_service_account.despliegue.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.repositorio_github}"
}

locals {
  permisos_de_despliegue = [
    "roles/artifactregistry.writer", # subir imágenes
    "roles/run.developer",           # desplegar revisiones
  ]
}

resource "google_project_iam_member" "despliegue" {
  for_each = toset(local.permisos_de_despliegue)
  project  = var.proyecto
  role     = each.value
  member   = "serviceAccount:${google_service_account.despliegue.email}"
}

# Desplegar un servicio que corre como otra cuenta exige poder "actuar como" ella. Se concede
# sobre cada cuenta y no en todo el proyecto: así el despliegue no puede usar cuentas ajenas.
resource "google_service_account_iam_member" "despliegue_actua_como" {
  for_each = {
    api = google_service_account.api.name
    web = google_service_account.web.name
  }
  service_account_id = each.value
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.despliegue.email}"
}

# ── Secretos ────────────────────────────────────────────────────────────────────────────────────
# Solo el recipiente. El valor se carga aparte y nunca pasa por Terraform:
#   gcloud secrets versions add db-clave --data-file=- --project=tecnosport-dev
locals {
  secretos_api = [
    "db-clave",
    "jwt-secreto",
    "smtp-clave",
    "admin-clave",
    "wompi-secreto-eventos",
    "wompi-secreto-integridad",
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

# **Solo `db-clave`, y solo porque el despliegue migra.** Si integración continua corre Flyway,
# integración continua conoce la contraseña de la base: es inherente, no un descuido. Lo que sí es
# una decisión es que sea ese secreto y no los siete — la cuenta de despliegue no tiene por qué
# poder leer las llaves de Wompi ni el secreto del JWT.
#
# La alternativa que no reparte la contraseña es un trabajo de Cloud Run que migre con la cuenta
# de la API, que ya la lee. Cuesta un modo "solo migrar" que la aplicación no tiene, y es lo que
# corresponde mirar para producción; para dev, esto.
resource "google_secret_manager_secret_iam_member" "despliegue_lee_la_clave_de_la_base" {
  secret_id = google_secret_manager_secret.api["db-clave"].id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.despliegue.email}"
}

# ── Servicios ───────────────────────────────────────────────────────────────────────────────────
# Imagen de arranque de Google: el servicio tiene que existir antes de que exista una imagen
# nuestra que desplegar, y el despliegue la reemplaza. Ver `ignore_changes` en el módulo.
locals {
  imagen_de_arranque = "us-docker.pkg.dev/cloudrun/container/hello"
}

module "api" {
  source = "../../modules/cloud-run"

  nombre             = "tecnosport-api"
  region             = var.region
  imagen             = local.imagen_de_arranque
  cuenta_de_servicio = google_service_account.api.email
  memoria            = "1Gi" # una JVM en 512Mi arranca demasiado justa

  # Las de la base se agregan solo cuando hay a dónde conectarse. Un DB_HOST vacío armaría
  # `jdbc:postgresql://:5432/` y el arranque moriría con un error sobre la URL, no sobre lo que
  # de verdad falta.
  variables = merge({
    APP_URL_PUBLICA     = var.dominio_publico_web
    GCS_BUCKET_IMAGENES = var.bucket_imagenes
    SMTP_HOST           = "smtp.resend.com"
    SMTP_PUERTO         = "587"
    SMTP_AUTH           = "true"
    SMTP_USUARIO        = "resend"
    CORREO_REMITENTE    = "no-responder@dev.tecnosport.co"
    WOMPI_AMBIENTE      = "sandbox"
    # La aplicación **no** migra al arrancar: lo hace el flujo de despliegue, en un paso propio y
    # antes de mover la revisión. Es lo que `docs/07-infra-gcp.md` exige para producción, y dev
    # existe para ensayar producción. Y si el paso se saltara, esto no lo tapa: Hibernate valida
    # el esquema al arrancar (`ddl-auto: validate`) y la revisión falla en voz alta en vez de
    # migrar por su cuenta a medias.
    SPRING_FLYWAY_ENABLED = "false"
    # `dev` enciende los sembradores de catálogo e inventario (`@Profile({"local","dev"})`). Sin
    # esto el ambiente arranca con el esquema migrado y **cero productos**, que es exactamente lo
    # que pasó en el primer despliegue: la API respondía 200 y la tienda estaba vacía. Los
    # sembradores son idempotentes, así que sobrevive a los arranques en frío.
    SPRING_PROFILES_ACTIVE = "dev"
    }, var.wompi_llave_publica == "" ? {} : {
    WOMPI_LLAVE_PUBLICA = var.wompi_llave_publica
    }, var.db_host == "" ? {} : {
    DB_HOST    = var.db_host
    DB_NOMBRE  = var.db_nombre
    DB_USUARIO = var.db_usuario
    # Neon exige TLS. Sin esto la conexión se rechaza y el síntoma no menciona el certificado.
    DB_PARAMS = var.db_params
  })

  # **Solo cuando los secretos tengan valor.** Un secreto recién creado no tiene ninguna versión,
  # y montar `latest` de algo que no existe hace que la revisión no arranque — con un "internal
  # error" de Cloud Run que no menciona los secretos por ningún lado. Se descubrió aplicando esto
  # la primera vez. La secuencia es: crear los recipientes, cargar los valores con gcloud, poner
  # `secretos_cargados = true` y volver a aplicar.
  # La llave privada de Wompi no está en esta lista **ni existe como secreto**, y no es un olvido:
  # esta integración no la necesita. El navegador abre el checkout con la llave pública, el backend
  # firma con el secreto de integridad, valida el webhook con el de eventos y consulta el estado de
  # una transacción con `Authorization: Bearer <llave pública>` — está verificado en el Javadoc de
  # `WompiClient`. La privada sirve para operar transacciones desde el servidor, que es justo lo que
  # este diseño evita para no ampliar el alcance de PCI (`docs/11-pagos-y-envios.md`).
  secretos = var.secretos_cargados ? {
    DB_CLAVE                 = "db-clave"
    JWT_SECRETO              = "jwt-secreto"
    SMTP_CLAVE               = "smtp-clave"
    ADMIN_CLAVE              = "admin-clave"
    WOMPI_SECRETO_EVENTOS    = "wompi-secreto-eventos"
    WOMPI_SECRETO_INTEGRIDAD = "wompi-secreto-integridad"
  } : {}

  depends_on = [google_project_service.apis]
}

module "web" {
  source = "../../modules/cloud-run"

  nombre             = "tecnosport-web"
  region             = var.region
  imagen             = local.imagen_de_arranque
  cuenta_de_servicio = google_service_account.web.email
  variables = {
    # El SSR consulta la API por su URL directa; el navegador, por el proxy de /api de este mismo
    # servidor. Dos dominios distintos romperían la cookie de sesión — ver `proxy-api.ts`.
    API_URL_PUBLICA = "${module.api.url}/api/v1"
    APP_URL_PUBLICA = var.dominio_publico_web
    # Comodín y no el dominio exacto: la URL del servicio no se conoce hasta que existe, y sin
    # esta variable `@angular/ssr` responde 400 a todo. Queda acotado a run.app, que es donde vive.
    NG_ALLOWED_HOSTS       = "*.run.app"
    NG_TRUST_PROXY_HEADERS = "x-forwarded-proto,x-forwarded-host,x-forwarded-for"
  }

  depends_on = [google_project_service.apis]
}
