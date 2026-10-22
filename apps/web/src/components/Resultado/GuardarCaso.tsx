import { huellaEvidencia, type Comprobante, type Veredicto } from "@itf/core";
import { useState } from "react";
import { Link } from "react-router-dom";
import type { Evidencia } from "../../hooks/useAnalisis";
import { useOnline } from "../../hooks/useOnline";
import { api, ApiError } from "../../services/apiClient";
import { usePermisos } from "../../services/permisos";
import { descargarComprobante, recordarComprobante } from "../../services/receipts";

/* La explicación de privacidad se muestra solo la primera vez en este dispositivo. */
const CLAVE_AVISO_REGISTRO = "itf_aviso_registro_visto";
function avisoRegistroVisto(): boolean {
  try {
    return localStorage.getItem(CLAVE_AVISO_REGISTRO) === "1";
  } catch {
    return false;
  }
}
function marcarAvisoRegistroVisto() {
  try {
    localStorage.setItem(CLAVE_AVISO_REGISTRO, "1");
  } catch {
    /* sin almacenamiento disponible: se mostrará otra vez, sin romper nada */
  }
}

/* Guardar el caso (FR-014): solo con el permiso activado; la huella se calcula aquí, en el dispositivo. */
export function GuardarCaso({ veredicto, evidencia, consentId }: { veredicto: Veredicto; evidencia: Evidencia; consentId: string | null }) {
  const [permisos] = usePermisos();
  const enLinea = useOnline();
  const [comprobante, setComprobante] = useState<Comprobante | null>(null);
  const [mostrarExplicacion] = useState(() => !avisoRegistroVisto());
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  async function guardar() {
    setGuardando(true);
    setError(null);
    try {
      const textoBase = [evidencia.texto, evidencia.relato?.quienDijoSer, evidencia.relato?.notas].filter(Boolean).join("\n");
      const c = await api<Comprobante>("/api/casos", {
        body: {
          huellaEvidencia: await huellaEvidencia(textoBase),
          tipoEvidencia: evidencia.tipo,
          veredicto,
          consentId: veredicto.ia.estado === "usada" ? (consentId ?? undefined) : undefined,
        },
      });
      recordarComprobante(c);
      marcarAvisoRegistroVisto();
      setComprobante(c);
    } catch (e) {
      setError(e instanceof ApiError ? (e.detalle ?? e.titulo) : "No se pudo guardar el caso.");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-guardar">
      <h3 id="titulo-guardar">Registro verificable del caso</h3>
      {comprobante ? (
        <>
          <p className="ok" role="status">Caso guardado: {comprobante.caso.id}</p>
          {mostrarExplicacion && <p className="ayuda">Se guardó la huella del mensaje y el veredicto, no el mensaje.</p>}
          <div className="fila-botones">
            <button type="button" className="btn" onClick={() => descargarComprobante(comprobante)}>
              ⬇️ Descargar comprobante
            </button>
            <Link className="btn secundario" to={`/comprobante/${comprobante.caso.id}`}>
              🖨️ Versión imprimible
            </Link>
          </div>
        </>
      ) : !permisos.guardarRegistro ? (
        <p className="aviso">
          Para guardar el caso activa el permiso <strong>“Guardar registro del caso”</strong> en <Link to="/permisos">Permisos</Link>. Solo se guarda la huella
          y el veredicto.
        </p>
      ) : (
        <>
          <p className="ayuda" style={{ marginTop: 0 }}>
            Guarda un registro con fecha, veredicto, fuentes y la huella SHA-256 del mensaje, firmado y encadenado. Te sirve como soporte para tu denuncia.
          </p>
          <button type="button" className="btn secundario" onClick={guardar} disabled={guardando || !enLinea}>
            {guardando ? <span className="cargando" aria-hidden="true" /> : "💾"} Guardar caso
          </button>
          {!enLinea && <p className="aviso">Necesitas conexión para guardar el caso.</p>}
        </>
      )}
      {error && <p className="aviso alerta" role="alert">{error}</p>}
    </section>
  );
}
