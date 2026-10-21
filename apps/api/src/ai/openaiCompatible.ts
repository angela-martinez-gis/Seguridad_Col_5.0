import OpenAI, { toFile } from "openai";
import type { AiProvider, ChatRequest, ChatResponse, Transcripcion } from "./types";

/* Un único adaptador para todos los proveedores que hablan el formato de OpenAI:
   Groq, Gemini (endpoint de compatibilidad), OpenRouter, OpenAI, Ollama…
   Admite una cadena de modelos de respaldo: los planes gratuitos limitan los tokens POR MODELO
   y por día, así que cuando un modelo agota su cuota se usa el siguiente. */

export interface OpenAiCompatibleOptions {
  id: string;
  baseURL: string;
  apiKey: string;
  model: string;
  /** Modelos a usar, en orden, cuando el anterior responde 429 (cuota agotada). */
  fallbackModels?: string[];
  sttModel?: string;
  /** Solo para pruebas. */
  fetch?: typeof fetch;
}

/** Ajustes por familia de modelo: los de razonamiento (gpt-oss) necesitan margen y esfuerzo bajo. */
function ajustesModelo(model: string) {
  if (/gpt-oss/i.test(model)) return { max_tokens: 1200, reasoning_effort: "low" as const };
  // Las respuestas reales rondan 300 tokens; reservar de más consume el límite del plan gratuito.
  return { max_tokens: 500 };
}

/** Lee "Please try again in 6m50.4s" del mensaje de Groq; por defecto, 1 minuto. */
export function esperaSugeridaMs(mensaje: string): number {
  const m = mensaje.match(/try again in (?:(\d+)h)?(?:(\d+)m)?(?:([\d.]+)s)?/i);
  if (!m) return 60_000;
  const [, h, min, s] = m;
  return ((Number(h ?? 0) * 60 + Number(min ?? 0)) * 60 + Number(s ?? 0)) * 1000 || 60_000;
}

export class OpenAiCompatibleProvider implements AiProvider {
  readonly id: string;
  readonly model: string;
  readonly models: string[];
  readonly sttModel?: string;
  private client: OpenAI;
  /** Modelo → instante hasta el que no se intenta (cuota agotada). */
  private enPausa = new Map<string, number>();

  constructor(opts: OpenAiCompatibleOptions) {
    this.id = opts.id;
    this.model = opts.model;
    this.models = [opts.model, ...(opts.fallbackModels ?? []).filter((m) => m && m !== opts.model)];
    this.sttModel = opts.sttModel;
    // Sin reintentos automáticos: un fallo de IA no debe duplicar la espera de la persona.
    this.client = new OpenAI({ baseURL: opts.baseURL, apiKey: opts.apiKey || "sin-clave", maxRetries: 0, fetch: opts.fetch });
  }

  async chat(req: ChatRequest, signal: AbortSignal): Promise<ChatResponse> {
    const usarTools = !req.json && req.tools && req.tools.length > 0;
    const ahora = Date.now();
    const disponibles = this.models.filter((m) => (this.enPausa.get(m) ?? 0) <= ahora);
    let ultimoError: unknown = Object.assign(new Error("Todos los modelos agotaron su cuota"), { status: 429 });

    for (const model of disponibles) {
      try {
        const res = await this.client.chat.completions.create(
          {
            model,
            messages: req.messages,
            temperature: 0.1,
            ...ajustesModelo(model),
            ...(usarTools ? { tools: req.tools, tool_choice: "auto" as const } : {}),
            ...(req.json ? { response_format: { type: "json_object" as const } } : {}),
          } as OpenAI.Chat.ChatCompletionCreateParamsNonStreaming,
          { signal },
        );
        const msg = res.choices[0]?.message;
        return {
          content: msg?.content ?? null,
          model,
          toolCalls: (msg?.tool_calls ?? [])
            .filter((t) => t.type === "function")
            .map((t) => ({ id: t.id, name: t.function.name, arguments: t.function.arguments })),
        };
      } catch (e) {
        if ((e as { status?: number }).status !== 429 || signal.aborted) throw e;
        this.enPausa.set(model, Date.now() + esperaSugeridaMs((e as Error).message ?? ""));
        ultimoError = e;
      }
    }
    throw ultimoError;
  }

  async transcribe(audio: Buffer, filename: string, mime: string, signal: AbortSignal): Promise<Transcripcion> {
    if (!this.sttModel) throw new Error("Transcripción no configurada (AI_STT_MODEL vacío)");
    const file = await toFile(audio, filename, { type: mime });
    const res = (await this.client.audio.transcriptions.create(
      { file, model: this.sttModel, language: "es", response_format: "verbose_json" },
      { signal },
    )) as unknown as { text: string; duration?: number };
    return { text: res.text ?? "", durationSec: res.duration ?? 0 };
  }
}
