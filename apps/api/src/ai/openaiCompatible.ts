import OpenAI, { toFile } from "openai";
import type { AiProvider, ChatRequest, ChatResponse, Transcripcion } from "./types";

/* Un único adaptador para todos los proveedores que hablan el formato de OpenAI:
   Groq, Gemini (endpoint de compatibilidad), OpenRouter, OpenAI, Ollama… */

export interface OpenAiCompatibleOptions {
  id: string;
  baseURL: string;
  apiKey: string;
  model: string;
  sttModel?: string;
}

export class OpenAiCompatibleProvider implements AiProvider {
  readonly id: string;
  readonly model: string;
  readonly sttModel?: string;
  private client: OpenAI;

  constructor(opts: OpenAiCompatibleOptions) {
    this.id = opts.id;
    this.model = opts.model;
    this.sttModel = opts.sttModel;
    // Sin reintentos automáticos: un fallo de IA no debe duplicar la espera de la persona.
    this.client = new OpenAI({ baseURL: opts.baseURL, apiKey: opts.apiKey || "sin-clave", maxRetries: 0 });
  }

  async chat(req: ChatRequest, signal: AbortSignal): Promise<ChatResponse> {
    const usarTools = !req.json && req.tools && req.tools.length > 0;
    const res = await this.client.chat.completions.create(
      {
        model: this.model,
        messages: req.messages,
        temperature: 0.1,
        // Las respuestas reales rondan 300 tokens; reservar de más consume el límite por minuto del plan gratuito.
        max_tokens: 500,
        ...(usarTools ? { tools: req.tools, tool_choice: "auto" as const } : {}),
        ...(req.json ? { response_format: { type: "json_object" as const } } : {}),
      },
      { signal },
    );
    const msg = res.choices[0]?.message;
    return {
      content: msg?.content ?? null,
      toolCalls: (msg?.tool_calls ?? [])
        .filter((t) => t.type === "function")
        .map((t) => ({ id: t.id, name: t.function.name, arguments: t.function.arguments })),
    };
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
