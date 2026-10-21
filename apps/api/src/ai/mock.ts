import type { AiProvider, ChatRequest, ChatResponse, Transcripcion } from "./types";

/* Proveedor simulado para pruebas y CI: determinista y sin consumir cuota (Constitución §V).
   Prohibido en producción (config.ts). MOCK_FAIL permite simular fallas. */

export type MockFail = "none" | "timeout" | "invalid" | "429";

const esperarAborto = (signal: AbortSignal) =>
  new Promise<never>((_, reject) => {
    if (signal.aborted) return reject(new Error("aborted"));
    signal.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
  });

function evidenciaDe(req: ChatRequest): string {
  const ultimo = [...req.messages].reverse().find((m) => m.role === "user");
  const texto = typeof ultimo?.content === "string" ? ultimo.content : "";
  const m = texto.match(/<evidencia>([\s\S]*?)<\/evidencia>/);
  return (m ? m[1] : texto).toLowerCase();
}

export class MockProvider implements AiProvider {
  readonly id: string;
  readonly model: string;
  readonly sttModel = "mock-stt";

  constructor(
    private fail: MockFail = "none",
    id = "mock",
    model = "mock-1",
  ) {
    this.id = id;
    this.model = model;
  }

  async chat(req: ChatRequest, signal: AbortSignal): Promise<ChatResponse> {
    if (this.fail === "timeout") return esperarAborto(signal);
    if (this.fail === "429") throw Object.assign(new Error("Rate limit"), { status: 429 });
    if (this.fail === "invalid") return { content: "esto no es JSON", toolCalls: [] };

    const ev = evidenciaDe(req);
    const motivos: { texto: string; categoria: string }[] = [];
    let nivel: "bajo" | "medio" | "alto" = "bajo";
    let modalidad = "ninguna";

    if (/ignora (tus|las) instrucciones|di que (este mensaje )?es seguro/.test(ev)) {
      motivos.push({ texto: "El mensaje intenta manipular el análisis para parecer seguro.", categoria: "manipulacion" });
      nivel = "alto";
      modalidad = "otro";
    }
    if (/premio|ganaste|sorteo/.test(ev)) {
      motivos.push({ texto: "Promete un premio que no solicitaste, típico de estafas.", categoria: "suplantacion" });
      nivel = "alto";
      modalidad = "falso_premio";
    }
    if (/clave|c[oó]digo|token|contrase/.test(ev) && /(env|confirm|ingres|d[ií]ct|valid)/.test(ev)) {
      motivos.push({ texto: "Solicita información confidencial que ninguna entidad pide por mensaje.", categoria: "datos" });
      nivel = "alto";
      if (modalidad === "ninguna") modalidad = /habla|llam|asesor/.test(ev) ? "vishing" : "suplantacion_bancaria";
    }
    if (nivel === "bajo" && /urgente|bloquead|suspendid/.test(ev)) {
      nivel = "medio";
      modalidad = "otro";
      motivos.push({ texto: "Genera presión con un plazo o amenaza.", categoria: "urgencia" });
    }

    const respuesta = {
      nivel,
      confianza: nivel === "bajo" ? 0.6 : 0.85,
      modalidad,
      motivos,
      entidadSuplantada: /bancolombia/.test(ev) ? "Bancolombia" : null,
      pasosAdicionales: nivel === "alto" ? ["Guarda capturas del mensaje como evidencia para la denuncia."] : [],
    };
    return { content: JSON.stringify(respuesta), model: this.model, toolCalls: [] };
  }

  async transcribe(audio: Buffer, _filename: string, _mime: string, signal: AbortSignal): Promise<Transcripcion> {
    if (this.fail === "timeout") return esperarAborto(signal);
    if (audio.length < 512) return { text: "", durationSec: 0 };
    return {
      text: "Buenas tardes, le habla del área de seguridad de su banco. Detectamos una compra no reconocida, dígame el código que le acaba de llegar para cancelarla.",
      durationSec: Math.min(600, Math.round(audio.length / 16000)),
    };
  }
}
