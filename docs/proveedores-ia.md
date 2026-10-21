# Proveedores de IA: cómo cambiar de modelo

El servicio habla con cualquier proveedor compatible con la API de OpenAI (`/chat/completions` y `/audio/transcriptions`).
**Cambiar de proveedor = cambiar variables de entorno**; no hay cambios de código ni de interfaz (FR-024, SC-005).
El código está en `apps/api/src/ai/` y nada fuera de esa carpeta conoce al proveedor (Constitución §IV).

## Variables

| Variable | Descripción |
|---|---|
| `AI_PROVIDER` | `groq` · `gemini` · `openrouter` · `openai` · `ollama` · `none` (solo reglas) · `mock` (pruebas; prohibido en producción) |
| `AI_BASE_URL` | Opcional: si se omite se usa la URL conocida del proveedor |
| `AI_API_KEY` | Clave del proveedor (secreto; nunca en el código) |
| `AI_MODEL` | Modelo de chat para el agente |
| `AI_STT_MODEL` | Modelo de transcripción de audio (vacío = sin transcripción; queda "Me llamaron" escrito o dictado en el navegador) |
| `AI_TIMEOUT_MS` | Presupuesto total del agente (por defecto 12 000 ms) |
| `AI_MAX_TOOL_CALLS` | Máximo de consultas adicionales a fuentes que el agente puede pedir (por defecto 1; cada una añade una llamada al modelo y consume cuota) |

## Perfiles

### Groq (predeterminado, gratuito)

```env
AI_PROVIDER=groq
AI_API_KEY=gsk_...
AI_MODEL=qwen/qwen3.8-27b
AI_STT_MODEL=whisper-large-v3
```

- Clave gratis en https://console.groq.com (sin tarjeta). Límites en `console.groq.com/settings/limits`.
- Límite gratuito verificado (2026-10-20): 1.000 solicitudes/día y 8.000 tokens/min por organización → ~4–6 análisis con IA por minuto en total. Lo que exceda se resuelve con reglas y la app lo indica.
- `llama-3.3-70b-versatile` fue retirado; alternativas probadas: `openai/gpt-oss-120b` (más falsos positivos), `openai/gpt-oss-20b`.
- Datos: según https://console.groq.com/docs/your-data, por defecto no conserva entradas ni salidas de inferencia (solo metadatos de uso). El compromiso de no entrenar con los datos está en su DPA: **confirmarlo antes de salir a público**.

### Google Gemini

```env
AI_PROVIDER=gemini
AI_API_KEY=AIza...
AI_MODEL=gemini-2.5-flash
AI_STT_MODEL=
```

- ⚠️ **Plan gratuito**: Google puede usar lo enviado para mejorar productos y entrenar modelos, con posible revisión humana (https://ai.google.dev/gemini-api/terms). Para un servicio de víctimas de fraude úsalo **solo en plan de pago**.
- Verifica si el endpoint de compatibilidad ofrece `/audio/transcriptions` para tu modelo; si no, deja `AI_STT_MODEL` vacío (la PWA ofrece dictado en el navegador).

### OpenRouter

```env
AI_PROVIDER=openrouter
AI_API_KEY=sk-or-...
AI_MODEL=meta-llama/llama-3.3-70b-instruct:free
```

Los modelos `:free` cambian con frecuencia; el tratamiento de datos depende del proveedor enrutado.

### Modelo propio (Ollama / vLLM)

```env
AI_PROVIDER=ollama
AI_BASE_URL=http://mi-servidor:11434/v1
AI_MODEL=llama3.1:8b
```

Privacidad total, pero requiere un servidor con GPU/RAM que no existe en el hosting gratuito.

### Sin IA

```env
AI_PROVIDER=none
```

El análisis usa reglas y fuentes abiertas; la interfaz lo indica.

## Antes de cambiar en producción

1. Ejecuta la evaluación con el proveedor nuevo: workflow **Evaluación del agente IA** (`eval-ia.yml`) o en local
   `npm run start -w @itf/api` + `npm run eval -- --mode=agent` (meta: detección ≥ 90 %, especificidad ≥ 80 %).
2. Mide la latencia: `SMOKE_API_URL=http://localhost:8787 npm run smoke:ai -w @itf/api` (meta: p95 ≤ 15 s).
3. Actualiza el resumen de datos del proveedor en `apps/web/src/services/config.ts` (`PROVEEDORES_IA`) si cambia su política.
4. Cambia las variables en Render y redepliega. Cada caso registra `ia.proveedor` y `ia.modelo`.

Para un proveedor **no** compatible con OpenAI, implementa la interfaz `AiProvider` (`apps/api/src/ai/types.ts`) y regístralo en `apps/api/src/ai/factory.ts`.
