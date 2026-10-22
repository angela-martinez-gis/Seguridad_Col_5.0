# Validación de la especificación técnica — Agente de intercepción de llamadas (Android PoC)

**Fecha**: 2026-10-22 · **Documento validado**: [`especificacion-tecnica.md`](especificacion-tecnica.md)
**Fuente de verificación**: código fuente del framework de Android (AOSP, rama `main`): `CallScreeningService.java`, `TelecomManager.java`, `InCallService.java`, `MediaRecorder.java`, `RecognizerIntent.java`, y guías oficiales de developer.android.com (inicio de actividades en segundo plano, audio compartido).

Leyenda: ✅ correcto · ⚠️ correcto con ajustes · ❌ no funciona como está escrito

---

## 1. Resumen

El objetivo (que un agente conteste, haga 2 preguntas y clasifique la llamada como Fraude, Spam o Legítima) es válido para una demostración. Pero **la arquitectura de la sección 2 supone tres cosas que Android no permite a una app normal**:

1. **`CallScreeningService` no puede contestar llamadas.** Solo permite dejarla pasar, silenciarla o rechazarla.
2. **Una app de terceros no puede capturar el audio de la llamada** (`VOICE_CALL`, `VOICE_DOWNLINK`, `VOICE_UPLINK`). Para eso hace falta `CAPTURE_AUDIO_OUTPUT`, que según la documentación es un permiso *"reserved for use by system components and is not available to third-party applications"*.
3. **Una app de terceros no puede inyectar la voz del agente (TTS) en la llamada.** No existe una API pública para meter audio en el canal de salida de una llamada celular.

Pixel y Samsung tienen agentes de este tipo (Call Screen, Call Assist), pero funcionan porque son apps del sistema con permisos privilegiados.

**Conclusión**: la PoC sí se puede construir, pero hay que cambiar la arquitectura. La app debe ser el **marcador predeterminado** del teléfono (`InCallService`) y el audio debe pasar por el **altavoz**. Además, el día 1 se debe hacer una **prueba técnica de audio en el teléfono real**, porque ese es el riesgo más alto del proyecto. Ver la sección 4.

---

## 2. Validación punto por punto

### 2.1 Objetivo y casos de uso (secc. 1) — ✅
- Los tres estados (Fraude, Spam, Legítimo) y el alcance PoC sin integración con el operador son coherentes.
- ⚠️ "Tomar el rol del usuario" se debe redactar como **asistente que se identifica como tal**, que es lo que ya hace el guion ("habla el asistente de seguridad"). Ver 2.9.

### 2.2 Arquitectura (secc. 2) — ❌
- **"AudioRecord / In-Call Audio Stream"**: no es posible. Las fuentes de audio de llamada requieren `CAPTURE_AUDIO_OUTPUT` (solo sistema).
- **Micrófono durante la llamada**: según la guía oficial de audio compartido (Android 10+), la llamada siempre tiene prioridad sobre el micrófono. Solo los servicios de accesibilidad y las apps privilegiadas tienen excepciones. Una app normal puede **recibir silencio**. Se debe medir en el teléfono de la demo (prueba técnica de la sección 4).
- **"Speaker / In-Call Audio Stream" para el TTS**: no existe una API para inyectar audio en la llamada. La única vía en una PoC es **acoplamiento acústico**: altavoz activado y TTS reproducido por el parlante para que el micrófono lo transmita. Depende del equipo y la cancelación de eco puede atenuarlo.

### 2.3 Permisos (secc. 3.1) — ⚠️

| Permiso | Estado | Comentario |
|---|---|---|
| `READ_PHONE_STATE` | ✅ | Útil para estado de la llamada |
| `ANSWER_PHONE_CALLS` | ⚠️ | Necesario para `TelecomManager.acceptRingingCall()`, que está **deprecado** (recomienda `InCallService`). Si la app es el marcador predeterminado no se necesita |
| `MANAGE_OWN_CALLS` | ❌ | Es para apps VoIP propias (`ConnectionService` autogestionado). No aplica a llamadas de la SIM. Quitar |
| `RECORD_AUDIO` | ✅ | Necesario (con la limitación de 2.2) |
| `MODIFY_AUDIO_SETTINGS` | ✅ | Para activar el altavoz |
| `INTERNET` | ✅ | Para el LLM / STT en la nube |
| **Faltan** | — | `POST_NOTIFICATIONS` (Android 13+), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` y `FOREGROUND_SERVICE_PHONE_CALL` (Android 14+), `READ_CONTACTS` (opcional, ver 2.4) y, si se mantiene la ruta de solo filtrado, `USE_FULL_SCREEN_INTENT` o `SYSTEM_ALERT_WINDOW` |

- Además del manifiesto, el usuario debe aceptar el **rol** mediante `RoleManager.createRequestRoleIntent(...)`: `ROLE_CALL_SCREENING` o `ROLE_DIALER`. No basta con "otorgar permisos en ajustes" (checklist, secc. 8).
- La declaración del servicio con `BIND_SCREENING_SERVICE` y el `intent-filter` es ✅ correcta.

### 2.4 Código de `CallScreeningService` (secc. 3.2) — ❌
- **`setDisruptCall(false)` no existe** y no compila. Los métodos públicos de `CallResponse.Builder` son `setDisallowCall`, `setRejectCall`, `setSilenceCall`, `setSkipCallLog`, `setSkipNotification`, `setCallComposerAttachmentsToShow` y `build`.
- **El comentario "Responder automáticamente la llamada" es incorrecto**: `respondToCall` solo deja pasar, silencia o rechaza. No hay forma de contestar desde este servicio.
- `setSkipCallLog` y `setSkipNotification` solo valen con `setDisallowCall(true)`. Si no, el constructor lanza `IllegalStateException("Invalid response state for allowed call.")`. Con `false`, como en el ejemplo, no falla, pero son innecesarios. `setSkipCallLog` además solo lo respetan apps del operador o del sistema.
- **Plazo de 5 segundos**: *"A CallScreeningService must respond to a call within 5 seconds"*. Si no responde, el sistema ignora la respuesta.
- **Solo números fuera de contactos**: el servicio recibe únicamente llamadas de números que **no están en los contactos**, salvo que tenga `READ_CONTACTS`. Tampoco recibe números ocultos o desconocidos. **Importante para la demo**: el teléfono que llama no debe estar guardado, o se debe pedir `READ_CONTACTS`.
- **`startActivity(...)` desde el servicio**: Android 10+ bloquea abrir actividades desde segundo plano. `CallScreeningService` no está entre las excepciones. La pantalla HUD no abriría. Hay que usar una notificación de pantalla completa o el permiso de superposición (`SYSTEM_ALERT_WINDOW`, que sí es excepción). Con `InCallService` el problema no existe, porque la app **es** la pantalla de llamada.

### 2.5 Flujo y matriz de decisión (secc. 4) — ⚠️
- El flujo de 2 preguntas es ✅ adecuado para una demo controlada.
- **La pregunta 2 "perfilada" exige clasificar ya después de la respuesta 1**: se necesita una preclasificación después de R1 (para elegir P2) y una clasificación final después de R2. Hay que dejarlo explícito.
- **Conflicto de palabras clave**: `urgente` está en la lista de FRAUDE, pero la P2 del caso LEGÍTIMO pregunta "¿Es un asunto urgente…?" y la respuesta típica es "No es urgente…". Con reglas simples, **el caso legítimo se clasificaría como fraude**. Hay que manejar negaciones ("no es urgente") o quitar `urgente` de la lista de fraude y usarlo solo junto con otras señales.
- **Palabras ambiguas**: `banco` y `plan` aparecen en llamadas legítimas. Se recomienda un **puntaje ponderado**: señal fuerte (`clave`, `dígitos de la tarjeta`, `código`, `transferencia`) frente a señal débil (`banco`, `urgente`), con umbral. El LLM se usa solo si hay empate o duda.
- Acción LEGÍTIMO "Transferir": como la app ya contestó, transferir significa **detener al agente, apagar el altavoz y avisar al usuario** (vibración y pantalla) para que tome la llamada. No hay transferencia a otro número.

### 2.6 HUD (secc. 5) — ✅
- Colores de estado, burbujas de chat ≥ 18 dp y "Modo Director" son correctos y recomendables.
- ⚠️ El Modo Director pasa a ser **imprescindible**, no solo un respaldo para ambientes ruidosos, por los riesgos de audio de 2.2.

### 2.7 Voz STT/TTS (secc. 6) — ⚠️
- **`SpeechRecognizer`**: válido, pero está sujeto a la misma restricción de micrófono durante la llamada. No es continuo: se corta tras un silencio y hay que reiniciarlo en cada turno. Debe correr en el hilo principal.
- Usar **`es-CO`** (o `es-419`) en lugar de `es-ES` / `es-MX`, por el acento colombiano.
- Whisper en la nube: el backend actual ya usa Groq `whisper-large-v3`, pero **por petición del usuario la app actual no se modifica**. Si se usa, la APK llamaría a Groq directamente con una clave guardada en `local.properties` (no versionada). Esto es aceptable para la PoC, pero la clave queda dentro del APK.
- TTS `android.speech.tts.TextToSpeech` ✅. Reproducirlo por el **altavoz** (ver 2.2).

### 2.8 Requisitos no funcionales (secc. 7)
- **Latencia ≤ 1,5 s**: ⚠️ ajustada. Solo el fin de habla del `SpeechRecognizer` toma ~1 s. Con reglas locales es alcanzable. Con LLM se necesita un tiempo límite (~1 s) y respaldo a reglas.
- **Confianza STT < 60 %**: ❌ no confiable. `EXTRA_CONFIDENCE_SCORES` es *"optional and might not be provided"* y puede llegar `-1`. Se propone usar como criterio "resultado vacío o sin coincidencia" además de la confianza cuando exista.
- **Android 10 (API 29)+**: ✅ (roles `ROLE_CALL_SCREENING` y `ROLE_DIALER` existen desde API 29).

### 2.9 Aspectos legales (no cubiertos en la spec) — ⚠️
- La transcripción involucra datos personales del interlocutor (Ley 1581 de 2012). Para la PoC con llamadas controladas y participantes informados, el riesgo es bajo. Se recomienda que el agente **se identifique como asistente automático** (ya lo hace) y **no guardar audio**, igual que en la app actual.

### 2.10 Checklist de presentación (secc. 8) — ⚠️ añadir
- Número que llama **no guardado** en contactos.
- Aceptar el rol (marcador predeterminado o filtrado) desde el diálogo de la app.
- Verificar que el altavoz funciona en llamada y que el STT capta la voz del que llama.
- Probar en el teléfono y operador de la demo con anticipación (el comportamiento de audio varía por fabricante).

---

## 3. Arquitectura corregida (recomendada)

```
Llamada SIM entrante
   │
   ▼
InCallService (app = marcador predeterminado, ROLE_DIALER)
   │  Call.answer()  → contesta automáticamente
   │  setAudioRoute(ROUTE_SPEAKER) → altavoz
   ▼
HUD = pantalla de llamada propia (sin restricción de inicio en segundo plano)
   │
   ├── TTS por altavoz ──► el micrófono lo transmite al que llama (acoplamiento acústico)
   ├── STT (SpeechRecognizer es-CO) sobre el micrófono ◄── voz del que llama por altavoz
   ├── Motor de reglas ponderado + LLM con tiempo límite
   └── Acción: Fraude → Call.disconnect() · Spam → despedida + disconnect · Legítimo → avisar al usuario
                    + Modo Director (forzar ruta) siempre disponible
```

Alternativa si el teléfono silencia el micrófono durante la llamada: un **modo demo de llamada simulada**. El "emisor" habla desde otro teléfono por una conexión propia (VoIP/WebRTC con `ConnectionService` autogestionado, donde la app sí controla todo el audio), o el presentador usa el Modo Director. Así la demo no depende del comportamiento de audio del fabricante.

## 4. Riesgo principal y prueba técnica del día 1

| Prueba | Pasa si |
|---|---|
| A. La app (marcador predeterminado) contesta sola una llamada SIM | `Call.answer()` funciona en el teléfono de la demo |
| B. Con altavoz activo, `SpeechRecognizer` transcribe la voz del que llama | El texto llega no vacío y entendible |
| C. El TTS por altavoz se escucha en el teléfono que llama | La persona que llama entiende las preguntas |
| D. `Call.disconnect()` cuelga | La llamada termina en ambos extremos |

Si B o C fallan en el teléfono de la demo, se pasa a la alternativa de la sección 3 antes de construir el resto.

## 5. Decisiones pendientes

1. **Rol de la app**: marcador predeterminado (`ROLE_DIALER`, recomendado, permite contestar y colgar con la API oficial) o solo filtrado (`ROLE_CALL_SCREENING`, no puede contestar, solo bloquear o silenciar).
2. **Teléfono de la demo**: marca, modelo y versión de Android.
3. **STT**: local (`SpeechRecognizer`) o Whisper en la nube (Groq).
4. **Clasificador**: solo reglas ponderadas, o reglas + LLM (Groq) con tiempo límite.
