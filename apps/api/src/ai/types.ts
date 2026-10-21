import type { ChatCompletionMessageParam, ChatCompletionTool } from "openai/resources/chat/completions";

/* Contrato del proveedor de IA (specs/001-agente-ia-antifraude/contracts/ai-provider.md).
   Nada fuera de src/ai conoce a un proveedor concreto (Constitución §IV). */

export type ChatMessage = ChatCompletionMessageParam;
export type ToolDef = ChatCompletionTool;

export interface ToolCall {
  id: string;
  name: string;
  arguments: string;
}

export interface ChatRequest {
  messages: ChatMessage[];
  tools?: ToolDef[];
  /** Fuerza respuesta en JSON (sin herramientas). */
  json?: boolean;
}

export interface ChatResponse {
  content: string | null;
  /** Modelo que respondió (puede ser uno de respaldo). */
  model?: string;
  toolCalls: ToolCall[];
}

export interface Transcripcion {
  text: string;
  durationSec: number;
}

export interface AiProvider {
  readonly id: string;
  readonly model: string;
  /** Modelo principal y de respaldo, en orden. */
  readonly models?: string[];
  readonly sttModel?: string;
  chat(req: ChatRequest, signal: AbortSignal): Promise<ChatResponse>;
  transcribe?(audio: Buffer, filename: string, mime: string, signal: AbortSignal): Promise<Transcripcion>;
}
