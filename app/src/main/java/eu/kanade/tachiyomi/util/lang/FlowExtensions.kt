package eu.kanade.tachiyomi.util.lang

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

// KMK -->
/**
 * Emits the first value right away, then at most one value (the latest) per [periodMillis].
 * Unlike debounce, a steady stream of upstream values can't starve the collector.
 */
fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = conflate().transform {
    emit(it)
    delay(periodMillis)
}
// KMK <--
