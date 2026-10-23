# APK — Agente antifraude de llamadas (prueba de concepto)

App Android nativa (Kotlin + Jetpack Compose) que **simula** una llamada entrante. Un asistente de voz la contesta, hace dos preguntas y clasifica la llamada como **Fraude**, **Spam** o **Legítima**. Según el resultado, cuelga, se despide o transfiere la llamada.

La simulación no toca las llamadas reales de la SIM; para eso está el modo experimental de [llamadas reales](#llamadas-reales-experimental). Las razones técnicas y la ruta hacia la versión real están en [docs/validacion-especificacion.md](docs/validacion-especificacion.md). La PWA y la API de la raíz del repositorio no se modifican.

## Qué hace

1. **Llamada entrante simulada**: pantalla de llamada con número, ubicación, timbre y vibración. El asistente contesta solo a los 4 s, o antes con el botón *Asistente*.
2. **Conversación por voz**: el asistente pregunta el motivo de la llamada. La segunda pregunta depende de lo que respondió el emisor (código de asesor, parentesco, número de proceso, si hay una persona en la línea…).
3. **Transcripción en vivo**: subtítulos en burbujas (emisor a la izquierda, asistente a la derecha) y medidores de puntaje Fraude / Spam / Legítimo con las señales detectadas.
4. **Reacción**:
   - Fraude: alerta roja, cuelga y ofrece "Reportar número".
   - Spam: alerta amarilla, despedida cortés y cuelga.
   - Legítima: alerta verde, transfiere la llamada.

### 12 escenarios

| Fraude | Spam | Legítimas |
|---|---|---|
| Falso banco (dígitos de la tarjeta y código SMS) | Telemercadeo de telefonía | Compañero de oficina |
| Falso familiar en apuros ("adivine quién soy") | Llamada robot ("marque uno") | Llamada familiar |
| Falso premio (cobra el envío) | Encuesta comercial | Mensajero con un paquete |
| Suplantación de la DIAN (embargo y enlace de pago) | Crédito preaprobado | |
| Falso soporte técnico (código SMS y AnyDesk) | | |

### Modos

- **Guion automático** (recomendado para presentar): el emisor habla con una voz sintética distinta a la del asistente. Funciona sin internet y sin ayuda de nadie.
- **En vivo (micrófono)**: una persona hace de emisor y habla cerca del teléfono. El reconocimiento de voz del sistema (`es-CO`) transcribe lo que dice y el motor de reglas lo clasifica.
- **Recibir la llamada en 5 s**: da tiempo de preparar la presentación antes de que entre la llamada.
- **Llamada sorpresa**: elige un escenario al azar.
- **Modo Director**: mantener presionada la barra de color superior durante la llamada. Permite forzar Fraude, Spam o Legítimo si el micrófono falla. Un punto blanco discreto indica que hay una ruta forzada.

## Llamada real con dos teléfonos (sin cuentas ni servidores)

Botón verde del inicio: **Atender una llamada en altavoz (2 teléfonos)**. Sirve para demostrar la conversación con una llamada real en teléfonos que no dejan al asistente oír ni hablar dentro de la llamada (ver la sección siguiente).

1. **Teléfono A** recibe la llamada real, la contesta y activa el **altavoz**.
2. **Teléfono B**, con esta app, se pone junto al parlante de A y se toca el botón verde.
3. B hace las preguntas con su voz: A las capta con su micrófono como si hablara una persona. B escucha las respuestas por el parlante de A y da el veredicto.
4. El presentador ejecuta la acción en A: colgar si es fraude o spam, o quitar el altavoz si es legítima.

Funciona porque B no está en ninguna llamada, así que Android no le restringe el micrófono ni el parlante. Conviene un lugar sin ruido y el volumen de A al máximo.

## Llamadas reales (experimental)

Desde el inicio, **Llamadas reales (experimental)**. La app pasa a ser el **teléfono predeterminado**, y el asistente contesta las llamadas SIM entrantes con el mismo flujo: dos preguntas, veredicto y colgar o transferir.

- **Cómo funciona**: a los 4 s de timbre contesta (`InCallService` → `Call.answer`) y activa el altavoz. Su voz sale por el parlante y el micrófono la transmite al que llama; el reconocimiento de voz escucha al emisor por ese mismo altavoz. Con fraude o spam cuelga (`Call.disconnect`). Con una llamada legítima apaga el altavoz, vibra y muestra **Tomar la llamada**.
- **Mientras sea el teléfono predeterminado**, la app maneja todas las llamadas, también las que haces tú. Para eso tiene una pantalla básica de llamada (altavoz, silencio, teclado y colgar) y un campo para marcar.
- **Para volver** a la app de Teléfono original: botón *Volver a la app de Teléfono original*, o *Ajustes → Apps → Apps predeterminadas → App de teléfono*.
- **Riesgo conocido**: algunos teléfonos le entregan silencio al micrófono durante una llamada. En ese caso aparece *"No se escuchó al emisor"* y el asistente termina como spam. En la demo se usa el **Modo Director** para forzar el resultado.
- **Interruptores**: *El asistente contesta las llamadas entrantes* (si se apaga, suenan normal) y *Voz del asistente por el canal de llamada* (diagnóstico de audio).
- **Limitaciones**: una llamada a la vez (una segunda entrante se rechaza); no hay historial ni contactos.

Probado en el emulador con `adb emu gsm call <número>`: contesta, activa el altavoz, conversa, cuelga y respeta el Modo Director. Las llamadas salientes también funcionan. **Falta probar en un teléfono real** si el reconocimiento de voz escucha al emisor y si el emisor oye al asistente (puntos B y C de la pantalla de prueba).

## Instalar

APK lista (versionada en el repositorio): `salida/AgenteAntifraude-0.1.0-poc.apk`, de 1,8 MB y firmada con la llave de depuración. Al recompilar, se copia `app/build/outputs/apk/release/app-release.apk` a esa ruta.

1. Pasar la APK al teléfono (Android 10 o superior) y abrirla. Hay que permitir "instalar apps de origen desconocido".
2. Para que se escuchen las voces, el teléfono debe tener una voz en español en *Ajustes → Sistema → Idioma → Salida de texto a voz*. Si hay dos voces en español instaladas, el emisor usa una distinta a la del asistente.

Con el teléfono conectado por USB y la depuración activada:

```bash
adb install -r salida/AgenteAntifraude-0.1.0-poc.apk
```

Para lanzar un escenario directamente desde la computadora (útil para preparar la demo), se usa su `id` de `datos/Escenarios.kt`:

```bash
adb shell am start -n co.seguridad.agentellamadas/.MainActivity --es escenario fraude-banco
```

## Compilar

Requisitos: JDK 17 y Android SDK (plataforma 34). La ruta del SDK va en `local.properties` (`sdk.dir=...`), que no se versiona.

```bash
./gradlew testDebugUnitTest   # pruebas del motor de reglas (los 12 escenarios)
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

## Estructura

```
app/src/main/java/co/seguridad/agentellamadas/
├── datos/Escenarios.kt         guiones de los 12 escenarios
├── motor/MotorReglas.kt        clasificador: palabras clave ponderadas + negaciones
├── voz/Voz.kt                  síntesis de voz (asistente y emisor)
├── voz/Escucha.kt              reconocimiento de voz (modo en vivo)
├── llamada/LlamadaViewModel.kt máquina de estados de la llamada
└── ui/                         inicio, llamada entrante, conversación, Modo Director
```

## Documentos

- [Especificación técnica original](docs/especificacion-tecnica.md)
- [Validación de la especificación](docs/validacion-especificacion.md): por qué la versión real necesita otra arquitectura
