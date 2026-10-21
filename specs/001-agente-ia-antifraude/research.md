# Research: Agente IA facilitador de detección y orientación frente al fraude digital

**Fecha**: 2026-10-20 · **Plan**: [plan.md](./plan.md) · **Spec**: [spec.md](./spec.md)

Alcance: Fase 1 (PWA + API). La Fase 2 (app Android, US8) solo se tiene en cuenta para no cerrar puertas (§12).

---

## 1. Modelo de IA gratuito e intercambiable ("jev" → modelo gratuito)

**Decision**: Capa `AiProvider` sobre la **API compatible con OpenAI** (`/chat/completions` + `/audio/transcriptions`), configurada solo por variables de entorno (`AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`, `AI_STT_MODEL`). Proveedor por defecto: **Groq** (plan gratuito) con un modelo Llama de la familia 3.3 70B / Llama 4 para razonamiento y **Whisper large-v3** para transcribir audio. Proveedor `none` = solo reglas.

**Rationale**:
- Groq, Gemini (endpoint de compatibilidad OpenAI), OpenRouter, Ollama/vLLM local, OpenAI y Azure OpenAI hablan el mismo formato. Cambiar de proveedor = cambiar 3–4 variables (cumple FR-024, SC-005). Proveedores con API propia (p. ej. Anthropic) se añaden como un adaptador más detrás de la misma interfaz.
- Groq gratuito no pide tarjeta; límites aproximados (a confirmar en `console.groq.com/settings/limits`): ~30 req/min y 1.000–14.400 req/día según modelo; Whisper ~20 req/min y ~2.000 req/día. Suficiente para una prueba de concepto.
- Un solo proveedor cubre texto y audio (US1 + US7) con baja latencia (SC-001, SC-012).

**Actualización 2026-10-20 (validación con clave real)**: `llama-3.3-70b-versatile` ya no está disponible en Groq. Modelos de chat disponibles en el plan gratuito: `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3.8-27b` (todos con 1.000 solicitudes/día y 8.000 tokens/min por organización). Comparados con el mismo prompt: `qwen/qwen3.8-27b` clasificó bien un aviso legítimo de código (gpt-oss-120b lo marcó alto) y respondió en ~0,5–4 s → **modelo elegido: `qwen/qwen3.8-27b`**. Transcripción: `whisper-large-v3` disponible. Con 8.000 tokens/min el plan gratuito admite ~4–6 análisis con IA por minuto en total; el exceso recibe veredicto por reglas (`ia.estado = no_disponible`).

**Privacidad (decisivo frente a Gemini)**: Los términos del plan **gratuito** de la Gemini API permiten a Google usar entradas y salidas para mejorar sus productos y entrenar modelos, con posible revisión humana, y recomiendan no enviar información sensible. Para un servicio que recibe evidencias de víctimas de fraude esto choca con FR-020a. Gemini queda como alternativa configurable (útil si se pasa a plan de pago, donde no aplica ese uso) y el aviso de privacidad debe nombrar el proveedor activo. La política de retención de Groq se debe confirmar antes de salir a público y citarse en el aviso.

**Verificación de términos (T057, 2026-10-20)**: la documentación oficial de Groq ([Your Data](https://console.groq.com/docs/your-data)) indica que por defecto no conserva entradas ni salidas de inferencia (solo metadatos de uso) y que hay controles de retención cero. El compromiso de no entrenar con los datos aparece en su DPA según fuentes secundarias: **confirmarlo en el acuerdo vigente antes de salir a público**. El resumen se muestra en la PWA (`apps/web/src/services/config.ts` → Privacidad y diálogo de consentimiento).

**Alternatives considered**:
- *Gemini Flash gratuito*: multimodal y generoso, rechazado como predeterminado por el uso de datos del plan gratuito.
- *OpenRouter modelos `:free`*: válido como respaldo, pero disponibilidad y límites cambian con frecuencia.
- *Modelo local (Ollama)*: privacidad total, pero exige servidor con GPU/RAM que no existe en hosting gratuito.
- *SDK por proveedor*: más acoplamiento; rechazado en favor del formato OpenAI común.

## 2. Diseño del agente (autonomía + robustez)

**Decision**: Orquestador en dos capas:
1. **Planificador determinista** (siempre): según la evidencia (enlaces, números, remitentes, entidades mencionadas) dispara en paralelo las consultas a fuentes abiertas aplicables, con timeout por fuente de 4 s.
2. **Agente LLM con *tool calling*** (si hay consentimiento y proveedor): recibe evidencia redactada + hallazgos de reglas + resultados de fuentes, puede pedir hasta 3 llamadas de herramienta adicionales (p. ej. verificar otro dominio o un número) y devuelve un JSON validado contra el esquema `AiAssessment` ([contracts/ai-provider.md](./contracts/ai-provider.md)). Presupuesto total 12 s.

Veredicto final = **max(nivel reglas, nivel IA)** con explicación si discrepan (FR-006). Si la IA falla, excede cuota, devuelve JSON inválido o supera el tiempo → veredicto por reglas + `ia.estado = "no_disponible"` (FR/SC-003).

**Rationale**: El paso determinista garantiza que siempre se consulten las fuentes obvias (rápido y reproducible); el LLM aporta la "decisión autónoma" de FR-009 y la modalidad (FR-003) sin ser punto único de falla.

**Inyección de instrucciones (FR-007)**: evidencia enviada como dato delimitado (`<evidencia>…</evidencia>`) en mensaje de usuario, prompt de sistema que prohíbe seguir instrucciones del contenido, salida forzada a JSON con esquema, herramientas de solo lectura sin efectos, y regla local que detecta frases tipo "ignora tus instrucciones" y suma riesgo.

## 3. Fuentes abiertas

| Fuente | Uso | Acceso | Decisión |
|---|---|---|---|
| **Google Safe Browsing v5** (Lookup) | URL en listas de phishing/malware | Gratis, **solo uso no comercial**, API key; exige atribución y aviso | Usar (el proyecto es no comercial). Si se comercializa → Google Web Risk |
| **URLhaus (abuse.ch)** | URL/host distribuyendo malware | Gratis con *Auth-Key* de cuenta abuse.ch | Usar |
| **RDAP** (rdap.org / bootstrap IANA) | Fecha de registro del dominio (antigüedad < 30 días = señal) | Público, sin clave | Usar. `.co` publica RDAP |
| **Catálogo de entidades** (JSON versionado en repo) | Dominios y canales oficiales con `fuente` y `verificado` | Interno, curado a mano desde sitios oficiales / Superfinanciera | Usar; se amplía progresivamente (FR-013) |
| **Reportes de comunidad** (propios, SQLite) | Dominio/número reportado por ≥5 personas distintas en 72 h | Interno | Usar; reemplaza `REPORTES_SIMULADOS` (FR-010) |
| PhishTank | Lista de phishing | Registro de nuevas apps cerrado desde hace años | Rechazado |
| VirusTotal | Reputación | Gratis con límite muy bajo y términos restrictivos para productos | Rechazado para la PoC |

Cada consulta produce un `SourceCheck` con fuente, indicador, resultado, `consultadoEn` y estado; una fuente caída no detiene el análisis (FR-012). Las URLs nunca se abren desde el dispositivo (FR-011); las consultas se hacen por API desde el servidor, sin visitar el sitio.

## 4. Front-end: PWA responsive

**Decision**: **React 18 + Vite + TypeScript**, `vite-plugin-pwa` (Workbox) para manifiesto, *service worker*, modo sin conexión y **Web Share Target** (Android). CSS propio con variables heredadas del prototipo (morado `#5b1a8a/#7b1fa2`, verde `#22e07a`, niveles bajo/medio/alto), mobile-first desde 360 px.

**Rationale**: Reutiliza directamente HTML/CSS/JS del prototipo (reglas, OCR, SHA-256 encadenado); misma base empaquetable con Capacitor en Fase 2. React por ecosistema (ECharts, Testing Library) y por facilidad de mantener muchas vistas (análisis, "Me llamaron", registro, panel).

**Alternatives**: Vanilla/Lit (menos dependencias pero más trabajo de estado y vistas), Svelte (bien, menor ecosistema para el equipo), Next.js (SSR innecesario; complica despliegue estático).

## 5. Gráficos llamativos

**Decision**: **Apache ECharts 5** (carga modular). Componentes:
- **Medidor de riesgo** (gauge) animado 0–100 con tres bandas, aguja y etiqueta de nivel + ícono (no solo color, FR-027/US4-4).
- **Desglose de señales**: barras horizontales apiladas por categoría (urgencia, datos, suplantación, enlace, fuentes abiertas, voz/IA) con origen (regla/IA/fuente).
- **Panel público**: barras de modalidades, ranking de entidades suplantadas, línea semanal de casos con área degradada; contadores animados en tarjetas KPI.
- Paleta validada para daltonismo, modo claro/oscuro, `aria-label` y tabla alternativa por gráfico.

**Rationale**: animaciones y gauge nativos, buen rendimiento móvil, renderer SVG/Canvas, licencia Apache. **Alternatives**: Chart.js (gauge requiere plugin), Recharts (menos vistoso en gauges), D3 (demasiado esfuerzo para una PoC).

## 6. OCR y voz

**Decision**:
- **Capturas**: se mantiene **Tesseract.js** en el navegador (versión fijada), idioma `spa`. La imagen nunca sale del dispositivo; solo el texto revisado (mejora de privacidad sobre enviar la imagen a la IA).
- **Audio subido/compartido** (≤3 min, ≤25 MB): se envía con consentimiento a `POST /api/transcripciones` → `AI_STT_MODEL` (Whisper). No se guarda el audio (FR-001b).
- **Dictado** ("Me llamaron"): Web Speech API cuando el navegador la ofrece (Chrome/Android, Safari); si no, grabación con `MediaRecorder` y el mismo endpoint de transcripción.

## 7. Back-end

**Decision**: **Node.js 22 LTS + Fastify 5 + TypeScript**, validación con **Zod** (esquemas compartidos con el front desde `packages/core`), `@fastify/rate-limit` (FR-023), `@fastify/multipart` (audio), `@fastify/cors` (solo el origen de la PWA), `@fastify/helmet`.

**Rationale**: mismo lenguaje que el front → el motor de reglas, la redacción de datos personales y los tipos del veredicto viven en un paquete compartido y se ejecutan igual en el dispositivo (modo sin conexión / sin consentimiento) y en el servidor. Fastify: rápido, tipado, `inject()` para pruebas de contrato.

**Alternatives**: Python FastAPI (bueno para IA, pero duplica las reglas en dos lenguajes), Hono (válido, menos plugins maduros), Express (más lento, menos tipado).

## 8. Almacenamiento

**Decision**: **SQLite** vía `node:sqlite` (incluido en Node ≥ 22.13; actualizado en implementación: evita compilar `better-sqlite3` en Windows y en Docker), archivo en `/data/app.db`. **Aceptado** que se borre en cada despliegue/reinicio (decisión del 2026-10-20); por eso el comprobante descargable es autoverificable (§9). Migraciones SQL versionadas para que pasar luego a Postgres/Turso solo cambie la capa de repositorio.

## 9. Registro trazable y verificable

**Decision**:
- **Huella de evidencia**: SHA-256 del texto normalizado (NFC, espacios colapsados, sin datos personales redactados) calculada **en el cliente**; el servidor nunca recibe el texto para el registro, solo la huella (FR-022).
- **Cadena**: `hashEntrada = SHA-256(JSON canónico de la entrada sin hashEntrada)`, `previa = hashEntrada` del caso anterior **del mismo identificador anónimo** (cadena por persona, como el prototipo) + cadena global por servidor para auditoría.
- **Firma**: el servidor firma cada caso con **Ed25519** (`node:crypto`); clave en secreto `CASE_SIGNING_KEY` (si falta, clave efímera con aviso). Clave pública en `GET /api/clave-publica`. El **comprobante** (JSON + vista imprimible/PDF del navegador) incluye caso, huellas, firma y clave → verificable por terceros aunque la base se borre.
- **Identificador anónimo**: UUID v4 generado en el dispositivo (almacenado localmente); el servidor guarda solo `SHA-256(uuid + sal)`. Borrar historial (FR-019) elimina todos sus casos y consentimientos.

## 10. Datos personales y consentimiento

**Decision**: `redactPII()` en `packages/core` (se ejecuta en cliente **y** servidor): números de tarjeta (Luhn), cuentas, cédulas (6–10 dígitos con contexto), códigos OTP de 4–8 dígitos junto a "código/clave/token", correos y teléfonos personales → `[DATO OCULTO:tipo]`. Los números que la persona quiere verificar (remitente) se tratan como indicador, no se redactan.

Consentimiento por análisis (FR-020): modal con qué se envía / a quién (nombre del proveedor IA activo) / para qué; genera `consentId` registrado en `consents` (SC-011). Sin consentimiento → reglas locales en el dispositivo.

## 11. Despliegue con GitHub Actions

**Decision**:
- **Monorepo npm workspaces** (`apps/web`, `apps/api`, `packages/core`) — npm ya está instalado (Node 22.17 / npm 10.9); no se añade pnpm.
- **Web (PWA)** → **GitHub Pages** con `actions/deploy-pages`: conserva la URL pública actual del HTML (FR-031) y carga instantánea (sin arranque en frío).
- **API** → **Render** (servicio web gratuito con Docker, `render.yaml`): el workflow despliega llamando al *deploy hook* (secreto `RENDER_DEPLOY_HOOK_URL`) **solo si CI pasó** (FR-030).
- Workflow `keepalive.yml` (cron cada 14 min, `GET /api/salud`) para mitigar el apagado tras 15 min de inactividad; 1 servicio × ~744 h/mes cabe en las 750 h gratuitas. La PWA además "despierta" la API al abrirse.
- El mismo `Dockerfile` también sirve la PWA compilada (`SERVE_WEB=true`) → alternativa de despliegue único en cualquier host de contenedores.

**Rationale**: Render: free tier con Docker, se apaga tras 15 min sin tráfico (~1 min en despertar), disco efímero (compatible con la decisión de SQLite), 750 h/mes. **Fly.io ya no tiene plan gratuito** para cuentas nuevas (solo prueba de 2 h / 7 días). Cloud Run: válido pero exige tarjeta y configuración GCP.

**Supuesto a verificar**: el HTML actual está publicado en GitHub Pages del repo `angela-martinez-gis/Seguridad_Col_5.0`. Si está en otro host, solo cambia el job de despliegue web.

## 12. Compatibilidad con la Fase 2 (Android)

`apps/web` no usa APIs incompatibles con WebView; las reglas y la redacción están en `packages/core` (ejecutables en el dispositivo). En Fase 2 se añade `apps/android` con **Capacitor** + plugin nativo (NotificationListenerService, CallScreeningService) que llama a `analyzeLocal()` del mismo paquete. No requiere cambios de diseño en la Fase 1.

## 13. Pruebas

**Decision**: **Vitest** (core, API con `fastify.inject`, componentes con Testing Library), **Playwright** E2E en viewport 360×800 (Chromium + WebKit) y escritorio, **conjunto dorado** de ≥50 mensajes etiquetados en español colombiano (`packages/core/test/fixtures/golden.json`) para SC-002, prueba de manipulación de la cadena (SC-004) y pruebas con proveedor IA simulado (`AI_PROVIDER=mock`) para que CI no consuma cuota.

---

### Fuentes consultadas
- Gemini API – términos adicionales / uso de datos en servicios no pagos: https://ai.google.dev/terms · https://ai.google.dev/gemini-api/docs/logs-policy
- Gemini API – precios / plan gratuito: https://ai.google.dev/gemini-api/docs/pricing
- Groq – precios y límites del plan gratuito (fuente secundaria): https://eesel.ai/blog/groq-pricing
- Google Safe Browsing – uso permitido y términos: https://developers.google.com/safe-browsing/reference/Appropriate.Usage · https://developers.google.com/safe-browsing/terms
- URLhaus API (Auth-Key): https://xsoar.pan.dev/docs/reference/integrations/feed-ur-lhaus
- Render – plan gratuito: https://render.com/docs/free
- Fly.io – planes descontinuados: https://fly.io/docs/about/discontinued-plans/
