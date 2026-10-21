# Validación del quickstart — Fase 1

**Fecha**: 2026-10-20 · **Entorno**: Windows 11, Node 22.17, Chromium (Playwright), IA simulada (`AI_PROVIDER=mock`)

## Resultados automatizados

| Suite | Resultado |
|---|---|
| `packages/core` (Vitest) | 19/19 ✓ |
| `apps/api` contratos (Vitest + `fastify.inject`) | 49/49 ✓ |
| `apps/web` componentes (Vitest + Testing Library) | 2/2 ✓ |
| E2E Playwright (móvil 360×800 Chromium + escritorio) | 30/30 ✓ |
| Conjunto dorado, modo reglas (60 mensajes) | detección de fraude 100 % · especificidad 95,5 % (1 falso positivo: `l22`, recordatorio DIAN con "vence") ✓ |
| ESLint · TypeScript (3 paquetes) | ✓ sin errores |
| Arranque de producción (`NODE_ENV=production`, `SERVE_WEB=true`) | `/api/salud` 200, PWA y rutas SPA 200, CSP activa, rechaza `AI_PROVIDER=mock` ✓ |
| Bundle inicial de la PWA | ~72 KB gzip (meta < 250 KB) ✓ |

## Escenarios del quickstart

| # | Estado | Evidencia |
|---|---|---|
| V1 | ✓ | `e2e/analizar.spec.ts` (alto, ≥3 motivos con origen, denuncia, medidor) |
| V2 | ✓ | `test/contract/analisis.test.ts` (IA en timeout → reglas, `no_disponible`) |
| V3 | ✓ | `e2e/analizar.spec.ts` (sin llamadas a `/api/analisis`) |
| V4 | ✓ | `e2e/analizar.spec.ts`, `agent.test.ts` (manipulación) |
| V5 | Parcial | Flujo y texto editable implementados; OCR real no automatizado (descarga de modelos de Tesseract) |
| V6 | Parcial | `transcripciones.test.ts` con IA simulada; falta audio real con Whisper (T089) |
| V7 | ✓ | `e2e/offline.spec.ts` (pasos urgentes primero, vishing) |
| V8 | Parcial | `sources.test.ts` con respuestas simuladas; falta prueba con claves reales de Safe Browsing/URLhaus |
| V9 | ✓ | `sources.test.ts` (sin clave → no disponible, análisis sigue) |
| V10 | ✓ | `e2e/analizar.spec.ts` (permiso, ID, cadena íntegra) |
| V11 | ✓ | `chain.test.ts` (alteración y eliminación detectadas) |
| V12 | ✓ | `chain.test.ts` (comprobante válido sin fila en BD) |
| V13 | ✓ | `casos.test.ts` (borrar historial) |
| V14 | ✓ | `e2e/panel.spec.ts` + revisión visual de capturas a 360 px |
| V15 | Parcial | 360 px y escritorio en Chromium ✓; WebKit corre en CI (no instalado localmente); instalación en dispositivos físicos pendiente |
| V16 | ✓ | `e2e/offline.spec.ts` (compartir por GET); POST multipart con audio pendiente en Android real |
| V17 | ✓ | `e2e/offline.spec.ts` |
| V18 | ✓ | `analisis.test.ts` (petición 21 → 429) |
| V19 | ✓ | Modo agente con Groq real (`gpt-oss-120b` + respaldo `qwen3.8-27b`): IA usada 60/60, detección 100 %, especificidad 95,5 % |
| V20 | Parcial | `provider-swap.test.ts` ✓; cambio a Groq/Gemini real pendiente de claves |
| V21 | Pendiente | Requiere configurar secretos, Render y Pages (ver `docs/despliegue.md`) |

## Latencia (SC-001, SC-012)

Medida con Groq real, 60 análisis de texto (2026-10-20): **p50 0,92 s · p95 1,46 s · máx. 1,63 s** (meta p95 ≤ 15 s ✓). SC-012 (audio) pendiente de audio real.

## Hallazgos con el proveedor real

- `llama-3.3-70b-versatile` fue retirado de Groq → se compararon `gpt-oss-120b`, `gpt-oss-20b` y `qwen3.8-27b`.
- Límite que manda: **200.000 tokens/día por modelo**. Primeras corridas: IA usada solo 16/60 (el agente pedía 4 herramientas por análisis, 6–8 mil tokens).
- Correcciones: prompt (marcas de privacidad no son riesgo; informar ≠ pedir), herramientas solo para indicadores nuevos, `AI_MAX_TOOL_CALLS=1`, `max_tokens` 500, `reasoning_effort: low` para gpt-oss, cadena `AI_MODELOS_RESPALDO`.
- Resultado final: ~950 tokens/análisis, IA usada 60/60, sin errores de cuota.

## Accesibilidad

axe (WCAG 2.1 A/AA) sin violaciones graves en Inicio, Analizar, Me llamaron, Verificar entidad, Permisos y Privacidad,
tras oscurecer `--texto-3` de `#7d7590` a `#675f7c` (contraste ≥ 4,5:1). Paleta de origen de motivos validada con
`validate_palette.js` (claro y oscuro); el verde agua en claro queda < 3:1, compensado con leyenda, etiquetas y tabla.
