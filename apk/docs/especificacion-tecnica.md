# Especificación Técnica (SPEC): Agente Autónomo de Intercepción y Validación de Llamadas (Android PoC)

---

## 1. Resumen Ejecutivo y Objetivos

### 1.1 Objetivo del Proyecto
El objetivo principal de esta demostración de concepto (*Proof of Concept* - PoC) es desarrollar una aplicación Android (APK) que demuestre la **funcionalidad práctica de un Agente de IA para la gestión autónoma de llamadas entrantes**. 

**Importante:** El propósito de este desarrollo es validar el flujo funcional, la interacción conversacional en tiempo real y los criterios de clasificación (Fraude, Spam, Legítimo) en un entorno controlado para presentaciones técnicas y ejecutivas. **No se prioriza la madurez de software para producción, escalabilidad a nivel de carrier ni arquitecturas complejas de pasarela de telefonía (IMS/VoLTE).**

### 1.2 Casos de Uso Acreditables en Presentación
El agente debe responder de forma automática a la llamada entrante, tomar el rol del usuario, realizar una secuencia fija de dos preguntas de perfilamiento y clasificar la llamada en uno de tres estados:
1. **Fraude (Alerta Roja):** Intento de engaño, extracción de credenciales, o suplantación.
2. **Spam (Alerta Amarilla):** Oferta comercial masiva o llamadas automatizadas de telemercadeo.
3. **Legítimo / Dejar Pasar (Alerta Verde):** Comunicación personal, profesional o relevante.

---

## 2. Arquitectura General del Sistema

El sistema utiliza una arquitectura reactiva basada en componentes nativos de Android y un orquestador ligero de estado.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             DISPOSITIVO ANDROID                             │
│                                                                             │
│  ┌───────────────────────┐         ┌─────────────────────────────────────┐  │
│  │   TelecomManager      │         │     Interfaz Visual de Presentación │  │
│  │ CallScreeningService  │         │      (Visualizador HUD en Vivo)     │  │
│  └───────────┬───────────┘         └──────────────────▲──────────────────┘  │
│              │ Intercepción                           │ Estado / Subtítulos │
│              ▼                                        │                     │
│  ┌────────────────────────────────────────────────────┴──────────────────┐  │
│  │                 Orquestador de Estado (State Machine)                 │  │
│  └──────┬─────────────────────────────▲───────────────────────────┬──────┘  │
│         │ Captura audio               │ Transcripción             │ Texto   │
│         ▼                             │                           ▼         │
│  ┌──────────────┐             ┌───────┴────────┐          ┌─────────────────┐ │
│  │ AudioRecord  │────────────►│ Motor STT      │          │  Motor TTS      │ │
│  │ / In-Call    │ Audio Raw   │ (Speech-to-    │          │  (Text-to-      │ │
│  │ Audio Stream │             │  Text)         │          │   Speech)       │ │
│  └──────────────┘             └────────────────┘          └────────┬────────┘ │
│                                                                    │ Audio    │
│                                                                    ▼          │
│                                                           ┌─────────────────┐ │
│                                                           │ Speaker/In-Call │ │
│                                                           │ Audio Stream    │ │
│                                                           └─────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Especificación de Componentes Nativos de Android

### 3.1 Permisos de Red y Telefonía (`AndroidManifest.xml`)
Para garantizar la intercepción sin intervención del usuario, el manifiesto de la aplicación debe incluir los siguientes permisos mínimos:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permisos de Telefonía e Intercepción -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
    
    <!-- Permisos de Audio y Hardware -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        <!-- Registro del Servicio de Filtrado de Llamadas -->
        <service
            android:name=".services.AgentCallScreeningService"
            android:permission="android.permission.BIND_SCREENING_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.telecom.CallScreeningService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

### 3.2 Implementación del Servicio de Intercepción (`CallScreeningService`)
El componente `CallScreeningService` permite detectar la llamada entrante antes de que el usuario responda convencionalmente.

```kotlin
// Ejemplo conceptual de intercepción
class AgentCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle.schemeSpecificPart
        
        // Responder automáticamente la llamada silenciando la interfaz por defecto
        val response = CallResponse.Builder()
            .setDisruptCall(false)
            .setSilenceCall(true) // Evita que suene el timbre nativo
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        // Iniciar la Activity HUD y el Orquestador
        val intent = Intent(this, PresentationHUDActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_CALLER_NUMBER", incomingNumber)
        }
        startActivity(intent)
    }
}
```

---

## 4. Definición Detallada de los 3 Casos de Uso de Demostración

Para mitigar riesgos de falla durante la presentación en vivo, la lógica de preguntas y respuestas se estructurará de la siguiente manera:

```
                  ┌───────────────────────────────────────────┐
                  │          Llamada Interceptada             │
                  └─────────────────────┬─────────────────────┘
                                        │
                                        ▼
                  ┌───────────────────────────────────────────┐
                  │ Pregunta 1: "¿Cuál es el motivo exacto    │
                  │             de su llamada?"               │
                  └─────────────────────┬─────────────────────┘
                                        │
                                        ▼
                  ┌───────────────────────────────────────────┐
                  │ Captura y Análisis de Respuesta 1          │
                  └─────────────────────┬─────────────────────┘
                                        │
                                        ▼
                  ┌───────────────────────────────────────────┐
                  │ Pregunta 2: (Perfilada según contexto)    │
                  └─────────────────────┬─────────────────────┘
                                        │
                                        ▼
                  ┌───────────────────────────────────────────┐
                  │ Evaluador de Reglas / Clasificador LLM    │
                  └──────┬──────────────────┬───────────┬─────┘
                         │                  │           │
            ┌────────────┘                  │           └─────────────┐
            ▼                               ▼                         ▼
   ┌─────────────────┐             ┌─────────────────┐       ┌─────────────────┐
   │ 1. FRAUDE       │             │ 2. SPAM         │       │ 3. LEGÍTIMO     │
   │ - Colgar de    │             │ - Despedida     │       │ - Transferir/   │
   │   inmediato.    │             │   educada.      │       │   Aviso visual. │
   └─────────────────┘             └─────────────────┘       └─────────────────┘
```

### Matriz de Decisión y Palabras Clave (Rule Engine + LLM fallback)

| Variable | Caso 1: FRAUDE | Caso 2: SPAM | Caso 3: LEGÍTIMO |
| :--- | :--- | :--- | :--- |
| **Pregunta 1 (Agente)** | *"Hola, habla el asistente de seguridad. ¿Cuál es el motivo de su llamada?"* | *"Hola, habla el asistente de seguridad. ¿Cuál es el motivo de su llamada?"* | *"Hola, habla el asistente de seguridad. ¿Cuál es el motivo de su llamada?"* |
| **Respuesta Típica (Emisor)** | *"Le llamamos de su banco por un movimiento sospechoso en su cuenta."* | *"Le llamamos para ofrecerle una promoción exclusiva en su línea telefónica."* | *"Hola, soy Carlos de la oficina, necesito preguntarte sobre el reporte de ventas."* |
| **Pregunta 2 (Agente)** | *"Por seguridad, ¿podría indicarme su código de asesor o entidad emisora?"* | *"¿Desea que eliminemos este número de nuestra base de datos comercial?"* | *"¿Es un asunto urgente o prefieres que le deje una nota a la persona?"* |
| **Respuesta Típica 2** | *"Necesito que me confirme los dígitos de su tarjeta para validar su identidad."* | *"Es una oferta por tiempo limitado, solo le tomará dos minutos..."* | *"No es urgente, solo dile que me devuelva la llamada cuando se desocupe."* |
| **Tokens / Keywords Clave** | `banco`, `tarjeta`, `clave`, `bloqueo`, `confirmar datos`, `transferencia`, `urgente` | `oferta`, `promoción`, `descuento`, `plan`, `cambio de compañía`, `gratis` | `oficina`, `amigo`, `reunión`, `nota`, `mañana`, `familiar`, `proyecto` |
| **Veredicto / Acción** | **FRAUDE DETECTADO.**<br>Corta la llamada inmediatamente. | **SPAM DETECTADO.**<br>Declina educadamente y cuelga. | **LLAMADA LEGÍTIMA.**<br>Permite comunicación o notifica. |

---

## 5. Diseño de la Interfaz HUD (Visualización para Presentaciones)

Para garantizar la efectividad visual en proyectores o pantallas compartidas:

1. **Indicador de Estado en Vivo (Top Bar):**
   * **Azul:** Escuchando / Analizando.
   * **Rojo:** ALERTA DE FRAUDE DETECTADA.
   * **Amarillo:** TELEMERCADEO / SPAM DETECTADO.
   * **Verde:** LLAMADA VERIFICADA Y PERMITIDA.

2. **Transcripción en Tiempo Real (Center Canvas):**
   * Muestra subtítulos en formato Chat (*Bubbles*) en letras grandes y legibles (mínimo 18dp):
     * Izquierda (Gris): Palabras del emisor registradas por STT.
     * Derecha (Azul): Respuestas emitidas por el Agente (TTS).

3. **Panel "Modo Director" (Oculto / Flotante para Presentadores):**
   * **Propósito:** Prevenir fallos si el micrófono del teléfono no capta adecuadamente el audio de la llamada durante la demostración en un entorno ruidoso.
   * **Botones de Forzado:**
     * `[Force Fraud Path]`
     * `[Force Spam Path]`
     * `[Force Legitimate Path]`

---

## 6. Integración de Motores de Voz (STT y TTS)

### 6.1 Speech-To-Text (STT) - Reconocimiento de Voz
* **Opción A (Recomendada PoC):** API NAtiva `android.speech.SpeechRecognizer` para transcripción local continua en español (`es-ES` / `es-MX`).
* **Opción B (Baja Latencia Cloud):** OpenAI Whisper API o Deepgram WebSocket si se cuenta con conexión estable a internet durante la demo.

### 6.2 Text-To-Speech (TTS) - Síntesis de Voz
* **Motor Recomendado:** `android.speech.tts.TextToSpeech` utilizando la voz en español de Google con tono neutro y velocidad ajustada a $1.0x$.

---

## 7. Requisitos No Funcionales para la Demostración

1. **Latencia de Interacción:** El tiempo transcurrido entre el fin de la alocución del emisor y la respuesta del agente no debe exceder los **1.5 segundos**.
2. **Resiliencia durante la Demo:** Si la tasa de confianza del motor STT es menor al 60%, el agente asumirá una segunda repetición genérica: *"Disculpe, no le escuché con claridad. ¿Podría repetir el motivo?"*
3. **Compatibilidad:** Android 10 (API Level 29) o superior.

---

## 8. Checklist de Validación para el Día de la Presentación

- [ ] Instalar APK en un dispositivo con SIM activa y volumen de altavoz alto.
- [ ] Otorgar permisos de `CallScreeningService` manualmente en los ajustes de Android.
- [ ] Verificar funcionamiento del "Modo Director" (Override manual).
- [ ] Realizar tres llamadas de prueba controladas (Fraude, Spam, Legítimo).