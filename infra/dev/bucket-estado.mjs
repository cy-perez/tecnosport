#!/usr/bin/env node
// Crea el bucket donde vive el estado de Terraform del ambiente de desarrollo.
//
//   node infra/dev/bucket-estado.mjs
//
// Es el arranque en frío, y por eso es un script y no Terraform: para guardar el estado hace
// falta el bucket, y para crear el bucket con Terraform haría falta un estado. El círculo se
// rompe una vez, aquí, con algo idempotente — correrlo dos veces no cambia nada.
//
// Requiere `gcloud` autenticado (`gcloud auth login`) sobre el proyecto de desarrollo. Nada de
// esto toca producción: el proyecto es otro, y así se queda.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// `.env.local` manda sobre los valores por omisión, igual que en bucket-imagenes.mjs.
const local = {};
const archivoLocal = resolve(fileURLToPath(import.meta.url), '../../../.env.local');
if (existsSync(archivoLocal)) {
  for (const linea of readFileSync(archivoLocal, 'utf8').split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
    if (m) local[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
  }
}
const config = (clave) => process.env[clave] || local[clave];

const PROYECTO = config('GCP_PROYECTO_DEV') ?? 'tecnosport-dev';
// La misma región que el bucket de imágenes y que los servicios: un solo lugar para todo el
// ambiente. Tiene que coincidir con `bucket` del bloque backend en infra/envs/dev/backend.tf.
const REGION = config('GCP_REGION_DEV') ?? 'us-east1';
const BUCKET = config('GCP_BUCKET_ESTADO') ?? 'tecnosport-dev-estado-terraform';

const ESWIN = process.platform === 'win32';
const BINARIO = ESWIN ? 'gcloud.cmd' : 'gcloud';
const citar = (a) => (ESWIN && /[ ()&^]/.test(a) ? `"${a}"` : a);

function gcloud(args, { tolerarFallo = false } = {}) {
  console.log(`\n$ gcloud ${args.join(' ')}`);
  try {
    const salida = execFileSync(BINARIO, args.map(citar), {
      encoding: 'utf8',
      shell: ESWIN,
      stdio: ['inherit', 'pipe', 'pipe'],
    });
    if (salida.trim()) console.log(salida.trim());
    return { ok: true, salida };
  } catch (error) {
    const detalle = (error.stderr ?? error.message ?? '').toString().trim();
    if (tolerarFallo) {
      console.log(`(se continúa) ${detalle.split('\n').slice(0, 2).join(' ')}`);
      return { ok: false, salida: detalle };
    }
    console.error(detalle);
    process.exit(1);
  }
}

const existe = (args) => gcloud(args, { tolerarFallo: true }).ok;

console.log(`Proyecto ${PROYECTO} · estado en gs://${BUCKET} · región ${REGION}`);

gcloud(['config', 'set', 'project', PROYECTO]);
gcloud(['services', 'enable', 'storage.googleapis.com']);

// 1. El bucket. Acceso uniforme y acceso público bloqueado: aquí dentro está el mapa entero de la
//    infraestructura, y el estado de Terraform guarda en claro cosas que se le pasen por
//    variable. Que no pueda hacerse público ni por descuido.
if (existe(['storage', 'buckets', 'describe', `gs://${BUCKET}`, '--format=value(name)'])) {
  console.log('\nEl bucket de estado ya existe, no se recrea.');
} else {
  gcloud([
    'storage',
    'buckets',
    'create',
    `gs://${BUCKET}`,
    `--location=${REGION}`,
    '--default-storage-class=STANDARD',
    '--uniform-bucket-level-access',
    '--public-access-prevention',
  ]);
}

// 2. Versionado, y aquí no es una comodidad: es lo único que separa un `apply` equivocado de
//    perder el estado. Sin la versión anterior, un estado corrupto deja la infraestructura viva y
//    a Terraform convencido de que no existe nada — y el siguiente apply intentaría crearla otra
//    vez encima.
gcloud(['storage', 'buckets', 'update', `gs://${BUCKET}`, '--versioning']);

console.log(`
Listo. Ahora, desde infra/envs/dev:

  terraform init
  terraform plan

El bloque backend ya apunta a gs://${BUCKET}.`);
