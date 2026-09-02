// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const boundaries = require('eslint-plugin-boundaries');

// Capas por funcionalidad, igual que en apps/api. Ver docs/01-arquitectura.md
// y apps/web/CLAUDE.md: "Un componente nunca inyecta HttpClient ni una clase
// de infrastructure: inyecta el puerto declarado en domain."
const ELEMENTOS_DE_CAPA = [
  { type: 'domain', pattern: 'src/app/features/*/domain/**/*' },
  { type: 'application', pattern: 'src/app/features/*/application/**/*' },
  { type: 'infrastructure', pattern: 'src/app/features/*/infrastructure/**/*' },
  { type: 'presentation', pattern: 'src/app/features/*/presentation/**/*' },
];

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    plugins: { boundaries },
    settings: {
      'boundaries/include': ['src/app/features/**/*.ts'],
      'boundaries/elements': ELEMENTOS_DE_CAPA,
    },
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          // 'app' para composición (layout, raíz); 'ts' para el sistema de
          // diseño en shared/, ver docs/04-ui-marca.md.
          prefix: ['app', 'ts'],
          style: 'kebab-case',
        },
      ],
      'boundaries/element-types': [
        'error',
        {
          default: 'allow',
          rules: [
            {
              from: 'domain',
              disallow: ['application', 'infrastructure', 'presentation'],
              message: 'domain no depende de nada. Sin HttpClient, sin Angular.',
            },
            {
              from: 'application',
              disallow: ['infrastructure', 'presentation'],
              message: 'application solo depende de domain (los puertos, no sus adaptadores).',
            },
            {
              from: 'presentation',
              disallow: ['infrastructure'],
              message:
                'presentation inyecta el puerto declarado en domain; el proveedor de la ruta decide la implementación de infrastructure.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
