import { Link } from "react-router-dom";

export default function Inicio() {
  return (
    <>
      <section className="hero">
        <h2>¿Te llegó un mensaje, enlace, audio o llamada que parece sospechosa?</h2>
        <p>
          Identifica tu fraude lo revisa antes de que hagas clic, respondas o transfieras. Te dice qué tan riesgoso parece, por qué, qué hacer y dónde
          denunciar.
        </p>
      <div className="accesos">
        <Link className="acceso" to="/me-llamaron">
          <span className="icono" aria-hidden="true">📞</span>
          <strong>Me llamaron</strong>
          <span>Cuéntanos la llamada y te orientamos.</span>
        </Link>
        <Link className="acceso" to="/entidad">
          <span className="icono" aria-hidden="true">🏦</span>
          <strong>Verificar entidad</strong>
          <span>¿Ese número o remitente es oficial?</span>
        </Link>
        <Link className="acceso" to="/panel">
          <span className="icono" aria-hidden="true">📊</span>
          <strong>Panel</strong>
          <span>Fraudes más reportados.</span>
        </Link>
        <Link className="acceso" to="/registro">
          <span className="icono" aria-hidden="true">🧾</span>
          <strong>Registro</strong>
          <span>Tus casos y comprobantes.</span>
        </Link>
      </div>
      <Link className="btn" to="/analizar">
        Empezar
      </Link>
      </section>
      <ul className="tarjeta lista ayuda">
        <li>Sin conexión, el análisis se hace con reglas en tu dispositivo.</li>
        <li>Con tu permiso en cada análisis, el contenido (con tus datos personales ocultos) se revisa con IA y fuentes abiertas.</li>
        <li>Ante la duda, comunícate con la entidad por su canal oficial, no con los datos del mensaje.</li>
      </ul>
    </>
  );
}
