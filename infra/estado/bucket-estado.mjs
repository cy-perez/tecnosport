#!/usr/bin/env node
// Crea el bucket donde vive el estado de Terraform de un ambiente.
//
//   node infra/estado/bucket-estado.mjs dev
//   node infra/estado/bucket-estado.mjs prod
//
// Es el arranque en frío, y por eso es un script y no Terraform: para guardar el estado hace
// falta el bucket, y para crear el bucket con Terraform haría falta un estado. El círculo se
// rompe una vez, aquí, con algo idempotente — correrlo dos veces no cambia nada.
//
// Requiere `gcloud` autenticado (`gcloud auth login`) sobre el proyecto del ambiente. Cada ambiente
// guarda su estado en su propio proyecto: quien lee el estado de dev no lee el de producción.
// El ambiente es obligatorio y sin valor por omisión, para que nadie cree el de producción por
// olvidar el argumento.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// `.env.local` manda sobre los valores por omisión, igual que en
// infra/local/bucket-imagenes.mjs.
const local = {};
const archivoLocal = resolve(fileURLToPath(import.meta.url), '../../../.env.local');
if (existsSync(archivoLocal)) {
  for (const linea of readFileSync(archivoLocal, 'utf8').split(/\r?\n/)) {
    const m = linea.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)$/);
    if (m) local[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
  }
}
const config = (clave) => process.env[clave] || local[clave];

const AMBIENTES = {
  dev: { proyecto: config('GCP_PROYECTO_DEV') ?? 'tecnosport-dev' },
  prod: { proyecto: config('GCP_PROYECTO_PROD') ?? 'tecnosport-prod' },
};
const AMBIENTE = process.argv[2];
if (!AMBIENTES[AMBIENTE]) {
  console.error(`Uso: node infra/estado/bucket-estado.mjs <${Object.keys(AMBIENTES).join('|')}>`);
  process.exit(1);
}

const PROYECTO = AMBIENTES[AMBIENTE].proyecto;
// La misma región que los buckets y los servicios: un solo lugar para todo el ambiente.
const REGION = 'us-east1';
// Tiene que coincidir con `bucket` del bloque backend en infra/envs/<ambiente>/backend.tf.
const BUCKET = `tecnosport-${AMBIENTE}-estado-terraform`;

const ESWIN = process.platform === 'win32';
const BINARIO = ESWIN ? 'gcloud.cmd' : 'gcloud';
const citar = (a) => (ESWIN && /[ ()&^]/.test(a) ? `"${a}"` : a);

// El proyecto va en cada llamada y no con `gcloud config set project`: eso dejaría el gcloud de la
// máquina apuntando a producción, y el siguiente comando sin `--project` iría a parar allá.
function gcloud(argumentos, { tolerarFallo = false } = {}) {
  const args = [...argumentos, `--project=${PROYECTO}`];
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
Listo. Ahora, desde infra/envs/${AMBIENTE}:

  terraform init
  terraform plan

El bloque backend ya apunta a gs://${BUCKET}.`);
