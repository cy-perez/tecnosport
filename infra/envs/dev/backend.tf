# El estado vive en un bucket, nunca en la máquina de quien aplica y nunca en git: dos personas
# —o la misma en dos máquinas— aplicando sobre estados distintos es la forma más rápida de
# duplicar infraestructura o borrar la que existe.
#
# El bucket lo crea `infra/dev/bucket-estado.mjs` y no Terraform, por el arranque en frío: para
# guardar el estado hace falta el bucket, y para crear el bucket con Terraform haría falta un
# estado. Se rompe el círculo una vez, con un script idempotente.
terraform {
  required_version = ">= 1.9"

  backend "gcs" {
    bucket = "tecnosport-dev-estado-terraform"
    prefix = "envs/dev"
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
