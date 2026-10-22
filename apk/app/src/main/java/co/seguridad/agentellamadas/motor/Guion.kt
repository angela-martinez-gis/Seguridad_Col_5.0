package co.seguridad.agentellamadas.motor

import co.seguridad.agentellamadas.datos.Escenarios

/** Lo que dice el asistente, compartido por la simulación y las llamadas reales. */
object Guion {

    const val PREGUNTA_1 = "Hola, habla el asistente de seguridad de ${Escenarios.TITULAR}. ¿Cuál es el motivo de su llamada?"
    const val REPETIR = "Disculpe, no le escuché con claridad. ¿Podría repetir el motivo de su llamada?"

    fun preguntaDos(c: Clasificacion): String = when (c.categoria) {
        Categoria.FRAUDE -> when (c.tipo) {
            Tipo.BANCARIO -> "Por seguridad, ¿me indica su nombre completo, su código de asesor y desde qué sede llama?"
            Tipo.FAMILIAR -> "Entiendo. ¿Me dice su nombre completo y qué parentesco tiene con ${Escenarios.TITULAR}?"
            Tipo.PREMIO -> "¿Cuál es el nombre de la empresa y cómo se puede verificar ese sorteo?"
            Tipo.AUTORIDAD -> "¿Me indica su nombre, su cargo y el número del proceso para verificarlo en los canales oficiales?"
            Tipo.SOPORTE -> "¿Desde qué área llama y cuál es el número del caso?"
            else -> "¿De qué entidad llama y cómo puedo verificar su identidad?"
        }
        Categoria.SPAM -> when (c.tipo) {
            Tipo.ROBOT -> "¿Hay una persona en la línea? Por favor, dígame su nombre."
            else -> "¿Podría eliminar este número de su base de datos comercial?"
        }
        Categoria.LEGITIMO -> "¿Es un asunto urgente o prefiere que le deje una nota a ${Escenarios.TITULAR}?"
        Categoria.INDETERMINADO -> "¿Podría darme más detalles? ¿De parte de quién y de qué se trata?"
    }

    fun despedida(categoria: Categoria): String = when (categoria) {
        Categoria.FRAUDE -> "Esta llamada no puede continuar. Por seguridad, no se entregará ninguna información. Hasta luego."
        Categoria.LEGITIMO -> "Gracias. Ya le comunico con ${Escenarios.TITULAR}, un momento por favor."
        else -> "Gracias por llamar. ${Escenarios.TITULAR} no está interesada; por favor retire este número de su lista. Que tenga buen día."
    }

    /** Sin motivo claro al final, el asistente no deja pasar la llamada. */
    fun veredictoFinal(c: Clasificacion): Clasificacion =
        if (c.categoria == Categoria.INDETERMINADO) c.copy(categoria = Categoria.SPAM, tipo = null, confianza = 50) else c
}
