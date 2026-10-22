package co.seguridad.agentellamadas.motor

import co.seguridad.agentellamadas.datos.Escenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorReglasTest {

    @Test
    fun `cada escenario se clasifica como se espera al final de la llamada`() {
        for (e in Escenarios.todos) {
            val c = MotorReglas.clasificar("${e.respuesta1}. ${e.respuesta2}")
            assertEquals("Escenario ${e.id}: ${c.puntajes}", e.esperado, c.categoria)
        }
    }

    @Test
    fun `la primera respuesta ya orienta la pregunta dos`() {
        for (e in Escenarios.todos) {
            val c = MotorReglas.clasificar(e.respuesta1)
            assertEquals("Escenario ${e.id}: ${c.puntajes}", e.esperado, c.categoria)
        }
    }

    @Test
    fun `los fraudes se tipifican segun el engano`() {
        val esperados = mapOf(
            "fraude-banco" to Tipo.BANCARIO,
            "fraude-familiar" to Tipo.FAMILIAR,
            "fraude-premio" to Tipo.PREMIO,
            "fraude-autoridad" to Tipo.AUTORIDAD,
            "fraude-soporte" to Tipo.SOPORTE,
            "spam-robot" to Tipo.ROBOT,
            "spam-encuesta" to Tipo.ENCUESTA,
            "spam-credito" to Tipo.FINANCIERO,
        )
        for ((id, tipo) in esperados) {
            val e = Escenarios.porId(id)
            assertEquals(id, tipo, MotorReglas.clasificar(e.respuesta1).tipo)
        }
    }

    @Test
    fun `no es urgente no cuenta como urgencia`() {
        val c = MotorReglas.clasificar("No es urgente, dígale que me llame cuando pueda")
        assertTrue(c.coincidencias.none { it.frase == "urgente" })
        assertEquals(Categoria.LEGITIMO, c.categoria)
    }

    @Test
    fun `pedir la clave es fraude aunque venga con una oferta`() {
        val c = MotorReglas.clasificar("Tenemos una promoción, solo confírmeme la clave de su tarjeta")
        assertEquals(Categoria.FRAUDE, c.categoria)
    }

    @Test
    fun `un saludo sin contexto queda sin determinar`() {
        assertEquals(Categoria.INDETERMINADO, MotorReglas.clasificar("Hola, buenas tardes").categoria)
    }

    @Test
    fun `forzar cambia la categoria y conserva las senales`() {
        val base = MotorReglas.clasificar(Escenarios.porId("spam-telemercadeo").respuesta1)
        val forzada = MotorReglas.forzar(base, Categoria.FRAUDE)
        assertEquals(Categoria.FRAUDE, forzada.categoria)
        assertEquals(base.coincidencias, forzada.coincidencias)
        assertNotEquals(base.categoria, forzada.categoria)
        assertTrue("Las señales comerciales no justifican un fraude", forzada.motivosDelVeredicto.isEmpty())
    }
}
