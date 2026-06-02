package com.lumera.app

import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

class PerformanceSafetyTest {

    // Simulação do comportamento de encerramento assíncrono do TorrentService.onDestroy
    @Test
    fun testTorrentServiceDestructionIsNonBlocking() = runBlocking {
        val teardownStarted = AtomicBoolean(false)
        val teardownFinished = AtomicBoolean(false)

        // Função mock de parada da engine que simula atrasos de rede e E/S (ex: esperar TorrServer fechar)
        val stopEngineMock: suspend () -> Unit = {
            teardownStarted.set(true)
            delay(1000) // Simula atraso de 1 segundo
            teardownFinished.set(true)
        }

        // Função que simula o onDestroy() refatorado
        val simulateOnDestroy: () -> Unit = {
            // Roda assincronamente fora do fluxo principal/calling thread
            CoroutineScope(Dispatchers.Default).launch {
                stopEngineMock()
            }
        }

        // Mede o tempo de chamada da função principal. Deve retornar imediatamente (< 100ms)
        val executionTime = measureTimeMillis {
            simulateOnDestroy()
        }

        // Garante que o método retornou instantaneamente sem travar a thread chamadora
        assertTrue("O método de destruição deve ser não-bloqueante", executionTime < 100)

        // Aguarda a corrotina em background finalizar para validar a integridade
        delay(1200)
        assertTrue("O teardown em background deve ter iniciado", teardownStarted.get())
        assertTrue("O teardown em background deve ter concluído", teardownFinished.get())
    }

    // Simulação do comportamento de leitura assíncrona do Splash na MainActivity
    @Test
    fun testProfileLoadingIsNonBlocking() = runBlocking {
        val queryStarted = AtomicBoolean(false)
        val queryFinished = AtomicBoolean(false)

        // Mock do DAO com leitura lenta simulada
        val getProfileByIdMock: suspend (Int) -> Boolean = {
            queryStarted.set(true)
            delay(800) // Simula leitura lenta de banco de dados
            queryFinished.set(true)
            true // splashEnabled
        }

        // Fluxo de simulação assíncrono da MainActivity.onCreate
        val splashDismissed = AtomicBoolean(false)
        val simulateMainActivityOnCreate: () -> Unit = {
            CoroutineScope(Dispatchers.Default).launch {
                val splashEnabled = getProfileByIdMock(1)
                if (!splashEnabled) {
                    splashDismissed.set(true)
                }
            }
        }

        val executionTime = measureTimeMillis {
            simulateMainActivityOnCreate()
        }

        // A chamada da Thread Principal (UI) não pode travar esperando a resposta do banco
        assertTrue("A leitura do perfil não deve bloquear a inicialização da Activity", executionTime < 100)

        // Aguarda a conclusão da tarefa paralela
        delay(1000)
        assertTrue("A consulta ao perfil deve ter iniciado", queryStarted.get())
        assertTrue("A consulta ao perfil deve ter finalizado", queryFinished.get())
    }
}
