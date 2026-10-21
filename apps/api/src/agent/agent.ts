import { AiAssessment, type RelatoLlamada, type SourceCheck, type Veredicto } from "@itf/core";
import type { AiProvider, ChatMessage } from "../ai/types";
import { construirMensajeUsuario, SYSTEM_PROMPT } from "./prompt";
import { ejecutarHerramienta, HERRAMIENTAS } from "./tools";
import type { FuentesDeps } from "./planner";

export interface ResultadoAgente {
  assessment: AiAssessment | null;
  fuentesExtra: SourceCheck[];
  error?: string;
  /** Mensaje técnico del proveedor (sin contenido de la persona), para diagnóstico en logs. */
  errorDetalle?: string;
  /** Modelo que produjo la evaluación (principal o de respaldo). */
  modelo?: string;
  llamadasHerramientas: number;
}

export interface OpcionesAgente {
  provider: AiProvider;
  texto: string;
  relato?: RelatoLlamada;
  reglas: Veredicto;
  fuentes: SourceCheck[];
  deps: FuentesDeps;
  timeoutMs: number;
  maxToolCalls: number;
}

function extraerJson(contenido: string | null): unknown {
  if (!contenido) return null;
  const limpio = contenido.replace(/^```(?:json)?\s*|\s*```$/g, "").trim();
  try {
    return JSON.parse(limpio);
  } catch {
    const m = limpio.match(/\{[\s\S]*\}/);
    if (!m) return null;
    try {
      return JSON.parse(m[0]);
    } catch {
      return null;
    }
  }
}

/**
 * Agente con tool calling (research.md §2). Cualquier falla, respuesta inválida o
 * exceso de tiempo devuelve assessment=null: el veredicto por reglas sigue (Constitución §III).
 */
export async function ejecutarAgente(o: OpcionesAgente): Promise<ResultadoAgente> {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), o.timeoutMs);
  const fuentesExtra: SourceCheck[] = [];
  let llamadas = 0;

  const messages: ChatMessage[] = [
    { role: "system", content: SYSTEM_PROMPT },
    { role: "user", content: construirMensajeUsuario({ texto: o.texto, relato: o.relato, reglas: o.reglas, fuentes: o.fuentes }) },
  ];

  try {
    for (let paso = 0; paso <= o.maxToolCalls; paso++) {
      const puedeUsarTools = llamadas < o.maxToolCalls;
      const res = await o.provider.chat({ messages, tools: puedeUsarTools ? HERRAMIENTAS : undefined }, ac.signal);

      if (res.toolCalls.length && puedeUsarTools) {
        messages.push({
          role: "assistant",
          content: res.content ?? "",
          tool_calls: res.toolCalls.map((t) => ({ id: t.id, type: "function" as const, function: { name: t.name, arguments: t.arguments } })),
        });
        for (const call of res.toolCalls) {
          const r = llamadas < o.maxToolCalls ? await ejecutarHerramienta(call.name, call.arguments, o.deps) : [];
          llamadas++;
          fuentesExtra.push(...r);
          messages.push({ role: "tool", tool_call_id: call.id, content: JSON.stringify(r.map(({ fuente, resultado, detalle, estado }) => ({ fuente, resultado, detalle, estado }))) });
        }
        continue;
      }

      const parsed = AiAssessment.safeParse(extraerJson(res.content));
      if (parsed.success) return { assessment: parsed.data, fuentesExtra, modelo: res.model, llamadasHerramientas: llamadas };

      // Último intento: pedir JSON estricto sin herramientas.
      messages.push({ role: "assistant", content: res.content ?? "" });
      messages.push({ role: "user", content: "Responde ahora solo con el objeto JSON válido indicado, sin texto adicional." });
      const final = await o.provider.chat({ messages, json: true }, ac.signal);
      const reintento = AiAssessment.safeParse(extraerJson(final.content));
      return reintento.success
        ? { assessment: reintento.data, fuentesExtra, modelo: final.model, llamadasHerramientas: llamadas }
        : { assessment: null, fuentesExtra, error: "respuesta_invalida", llamadasHerramientas: llamadas };
    }
    return { assessment: null, fuentesExtra, error: "sin_respuesta", llamadasHerramientas: llamadas };
  } catch (e) {
    const error = ac.signal.aborted ? "timeout" : ((e as { status?: number }).status === 429 ? "cuota" : "error_proveedor");
    return { assessment: null, fuentesExtra, error, errorDetalle: (e as Error).message?.slice(0, 300), llamadasHerramientas: llamadas };
  } finally {
    clearTimeout(timer);
  }
}
