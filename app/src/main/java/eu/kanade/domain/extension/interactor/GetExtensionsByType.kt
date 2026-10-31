package eu.kanade.domain.extension.interactor

import eu.kanade.domain.extension.model.Extensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<Extensions> {
        val showNsfwSources = preferences.showNsfwSource().get()

        return combine(
            preferences.enabledLanguages().changes(),
            extensionManager.installedExtensionsFlow,
            extensionManager.untrustedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguages, _installed, _untrusted, _available ->
            val (updates, installed) = _installed
                .filter { (showNsfwSources || !it.isNsfw) }
                .sortedWith(
                    compareBy<Extension.Installed> {
                        !it.isObsolete /* SY --> */ && !it.isRedundant /* SY <-- */
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .partition { it.hasUpdate }

            val untrusted = _untrusted
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            // KMK -->
            // This used to scan the installed and untrusted lists for every available extension
            // (O(available × installed)) on every emission, which crawls when hundreds of
            // extensions are installed at once. Use hash lookups instead.
            val installedKeys = _installed.mapTo(HashSet()) { it.pkgName to it.signatureHash }
            val untrustedKeys = _untrusted.mapTo(HashSet()) { it.pkgName to it.signatureHash }
            // KMK <--

            val available = _available
                .filter { extension ->
                    // KMK -->
                    val key = extension.pkgName to extension.signatureHash
                    key !in installedKeys &&
                        key !in untrustedKeys &&
                        // KMK <--
                        (showNsfwSources || !extension.isNsfw)
                }
                .flatMap { ext ->
                    ext.sources.filter { it.lang in enabledLanguages }
                        .map {
                            ext.copy(
                                name = it.name,
                                lang = it.lang,
                                pkgName = "${ext.pkgName}-${it.id}",
                                sources = listOf(it),
                            )
                        }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            Extensions(updates, installed, available, untrusted)
        }
    }
}
