#!/usr/bin/env node
// Crea el bucket de imágenes de desarrollo y la cuenta de servicio que firma las
// URL de subida (docs/07-infra-gcp.md: "Imágenes en dev: bucket real de Cloud
// Storage, no un emulador").
//
// Es idempotente: correrlo dos veces no rompe nada ni duplica la llave.
//
//   node infra/dev/bucket-imagenes.mjs
//
// Requiere `gcloud` autenticado (`gcloud auth login`) sobre un proyecto de
// desarrollo con facturación habilitada. Nada de esto toca producción: el
// proyecto es otro, y así se queda.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { homedir } from 'node:os';

const PROYECTO = process.env.GCP_PROYECTO_DEV ?? 'tecnosport-dev';
const BUCKET = process.env.GCS_BUCKET_IMAGENES ?? 'tecnosport-dev-imagenes';
// Capa gratuita: Standard en us-central1, us-east1 o us-west1 (docs/07-infra-gcp.md).
const REGION = process.env.GCP_REGION_DEV ?? 'us-central1';
const CUENTA = 'imagenes-dev';
const LLAVE = process.env.GOOGLE_APPLICATION_CREDENTIALS ?? `${homedir()}/.gcp/${BUCKET}.json`;
// El origen del túnel HTTPS del teléfono se agrega aquí cuando exista, separado
// por comas: sin él, el asistente de captura no puede subir desde el celular.
const ORIGENES = (process.env.GCS_ORIGENES_CORS ?? 'http://localhost:4200').split(',');

const correo = `${CUENTA}@${PROYECTO}.iam.gserviceaccount.com`;

function gcloud(args, { tolerarFallo = false } = {}) {
  console.log(`\n$ gcloud ${args.join(' ')}`);
  try {
    const salida = execFileSync('gcloud', args, { encoding: 'utf8', stdio: ['inherit', 'pipe', 'pipe'] });
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

function existe(args) {
  return gcloud(args, { tolerarFallo: true }).ok;
}

console.log(`Proyecto ${PROYECTO} · bucket gs://${BUCKET} · región ${REGION}`);

gcloud(['config', 'set', 'project', PROYECTO]);
gcloud(['services', 'enable', 'storage.googleapis.com', 'iam.googleapis.com']);

// 1. El bucket. Acceso uniforme: los permisos se dan por IAM, nunca por ACL de objeto.
if (existe(['storage', 'buckets', 'describe', `gs://${BUCKET}`, '--format=value(name)'])) {
  console.log('\nEl bucket ya existe, no se recrea.');
} else {
  gcloud([
    'storage', 'buckets', 'create', `gs://${BUCKET}`,
    `--location=${REGION}`,
    '--default-storage-class=STANDARD',
    '--uniform-bucket-level-access',
  ]);
}

// 2. Lectura pública: la ficha de producto sirve las imágenes por URL directa
//    (GCS_URL_PUBLICA). La escritura sigue siendo solo con URL firmada.
gcloud([
  'storage', 'buckets', 'add-iam-policy-binding', `gs://${BUCKET}`,
  '--member=allUsers',
  '--role=roles/storage.objectViewer',
]);

// 3. CORS: sin esto el PUT firmado desde el navegador muere en el preflight.
const cors = JSON.stringify([
  {
    origin: ORIGENES,
    method: ['GET', 'HEAD', 'PUT'],
    responseHeader: ['Content-Type'],
    maxAgeSeconds: 3600,
  },
]);
const archivoCors = `${process.env.TEMP ?? '/tmp'}/cors-${BUCKET}.json`;
writeFileSync(archivoCors, cors);
console.log(`\nCORS para: ${ORIGENES.join(', ')}`);
gcloud(['storage', 'buckets', 'update', `gs://${BUCKET}`, `--cors-file=${archivoCors}`]);

// 4. La cuenta de servicio que firma. objectAdmin solo sobre este bucket, no
//    sobre el proyecto entero.
if (existe(['iam', 'service-accounts', 'describe', correo, '--format=value(email)'])) {
  console.log('\nLa cuenta de servicio ya existe, no se recrea.');
} else {
  gcloud([
    'iam', 'service-accounts', 'create', CUENTA,
    '--display-name=Imagenes de producto (dev)',
  ]);
}

gcloud([
  'storage', 'buckets', 'add-iam-policy-binding', `gs://${BUCKET}`,
  `--member=serviceAccount:${correo}`,
  '--role=roles/storage.objectAdmin',
]);

// 5. La llave privada, que es lo que `signUrl` necesita de verdad. Se crea una
//    sola vez: una llave nueva en cada corrida dejaría llaves vivas regadas.
if (existsSync(LLAVE)) {
  console.log(`\nYa hay una llave en ${LLAVE}; no se crea otra.`);
} else {
  mkdirSync(dirname(LLAVE), { recursive: true });
  gcloud(['iam', 'service-accounts', 'keys', 'create', LLAVE, `--iam-account=${correo}`]);
  console.log(`\nLlave escrita en ${LLAVE}. No la subas a git.`);
}

console.log(`
Listo. En .env.local:

  GCS_BUCKET_IMAGENES=${BUCKET}
  GCS_URL_PUBLICA=https://storage.googleapis.com/${BUCKET}
  GOOGLE_APPLICATION_CREDENTIALS=${LLAVE}

Después reinicia bootRun: el bean Storage lee la credencial al arrancar.
`);
