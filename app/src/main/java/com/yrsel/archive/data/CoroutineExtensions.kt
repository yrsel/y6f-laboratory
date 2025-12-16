package com.yrsel.archive.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.job
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/*
처리율 제한하여 동시 요청 수를 조절하도록 Semaphore 활용
 */
suspend fun <T, R> List<T>.mapAsync(
    concurrencyLimit: Int = Int.MAX_VALUE,
    transformation: suspend (T) -> R,
): List<R> =
    coroutineScope {
        val semaphore = Semaphore(concurrencyLimit)
        this@mapAsync
            .map {
                async {
                    semaphore.withPermit {
                        transformation(it)
                    }
                }
            }.awaitAll()
    }

/*
중단 함수에서 지연 초기화
 */
fun <T> suspendLazy(initializer: suspend () -> T): suspend () -> T {
    var initializer: (suspend () -> T)? = initializer
    val mutex = Mutex()
    var holder: Any? = Any()

    return {
        if (initializer == null) {
            holder as T
        } else {
            mutex.withLock {
                initializer?.let {
                    holder = it()
                    initializer = null
                }
                holder as T
            }
        }
    }
}

/*
  파라미터화 된 연결에서 연결을 재사용하는 방법(WebSocket, RSocket,...) */
class ConnectionPool<K, V>(
    private val scope: CoroutineScope,
    private val builder: (K) -> Flow<V>,
) {
    private val connections = mutableMapOf<K, Flow<V>>()

    fun getConnection(key: K): Flow<V> =
        synchronized(this) {
            connections.getOrPut(key) {
                builder(key).shareIn(
                    scope = scope,
                    started = SharingStarted.WhileSubscribed(),
                )
            }
        }
}

/*
중단 가능한 프로세스 여러 개 중 가장 먼저 끝나는 결과를 원할 때 사용
 */
suspend fun <T> raceOf(
    racer: suspend CoroutineScope.() -> T,
    vararg racers: suspend CoroutineScope.() -> T,
): T =
    coroutineScope {
        select {
            (listOf(racer) + racers).forEach { racer ->
                async { racer() }.onAwait {
                    coroutineContext.job.cancelChildren()
                    it
                }
            }
        }
    }

/*
 재시도 함수 */
inline fun <T> retryWhen(
    predicate: (Throwable, retries: Int) -> Boolean,
    operation: () -> T,
): T {
    var retries = 0
    var fromDownstream: Throwable? = null
    while (true) {
        try {
            return operation()
        } catch (e: Throwable) {
            if (fromDownstream != null) {
                e.addSuppressed(fromDownstream)
            }
            fromDownstream = e
            if (e is CancellationException || !predicate(e, retries++)) {
                throw e
            }
        }
    }
}
