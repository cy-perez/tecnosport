# El estado de producción en su propio bucket y en su propio proyecto: con un bucket compartido,
# quien puede leer el estado de dev podría leer el de producción, y el estado describe la
# infraestructura entera. Lo crea `infra/estado/bucket-estado.mjs prod`, por el mismo arranque en
# frío que el de dev.
terraform {
  required_version = ">= 1.9"

  backend "gcs" {
    bucket = "tecnosport-prod-estado-terraform"
    prefix = "envs/prod"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.0"
    }
  }
}

provider "google" {
  project = var.proyecto
  region  = var.region
}

# Solo para el presupuesto. La API de presupuestos cobra la cuota al proyecto de quien llama, y con
# las credenciales de una persona no hay proyecto: sin esto responde que falta uno. Va en un alias y
# no en el proveedor de todo, porque forzarlo en todos haría que habilitar las APIs del proyecto
# nuevo dependiera de una API que todavía no está habilitada.
provider "google" {
  alias                 = "facturacion"
  project               = var.proyecto
  region                = var.region
  user_project_override = true
  billing_project       = var.proyecto
}
