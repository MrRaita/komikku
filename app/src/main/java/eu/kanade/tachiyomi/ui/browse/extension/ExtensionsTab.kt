package eu.kanade.tachiyomi.ui.browse.extension

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.ExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun extensionsTab(
    extensionsScreenModel: ExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current

    val state by extensionsScreenModel.state.collectAsState()
    // KMK -->
    val downloads by extensionsScreenModel.downloads.collectAsState()
    // KMK <--
    var privateExtensionToUninstall by remember { mutableStateOf<Extension?>(null) }

    // KMK -->
    var confirmUninstallSelected by remember { mutableStateOf(false) }
    var confirmTrustSelected by remember { mutableStateOf(false) }

    val selectedExtensions = if (state.selection.isEmpty()) {
        emptyList()
    } else {
        state.items.values.asSequence()
            .flatten()
            .map { it.extension }
            .filter { it.selectionKey() in state.selection }
            .distinctBy { it.selectionKey() }
            .toList()
    }
    val selectionToolbar: (@Composable () -> Unit)? = if (state.selectionMode) {
        {
            ExtensionSelectionToolbar(
                selectedCount = selectedExtensions.size,
                canInstall = selectedExtensions.any { it is Extension.Available },
                canUpdate = selectedExtensions.any { it is Extension.Installed && it.hasUpdate },
                canTrust = selectedExtensions.any { it is Extension.Untrusted },
                canUninstall = selectedExtensions.any { it !is Extension.Available },
                onClearSelection = extensionsScreenModel::clearSelection,
                onSelectAll = extensionsScreenModel::selectAll,
                onInvertSelection = extensionsScreenModel::invertSelection,
                onInstall = extensionsScreenModel::installSelected,
                onUpdate = extensionsScreenModel::updateSelected,
                onTrust = { confirmTrustSelected = true },
                onUninstall = { confirmUninstallSelected = true },
            )
        }
    } else {
        null
    }
    // KMK <--

    return TabContent(
        titleRes = MR.strings.label_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        // KMK -->
        selectionToolbar = selectionToolbar,
        // KMK <--
        actions = persistentListOf(
            // KMK -->
            AppBar.Action(
                title = stringResource(KMR.strings.action_toggle_nsfw_only),
                icon = Icons.Outlined._18UpRating,
                iconTint = if (state.nsfwOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                onClick = { extensionsScreenModel.toggleNsfwOnly() },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_webview_refresh),
                onClick = extensionsScreenModel::findAvailableExtensions,
            ),
            // KMK <--
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(ExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.extensionStores),
                onClick = { navigator.push(ExtensionStoresScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            BackHandler(enabled = state.searchQuery != null) {
                extensionsScreenModel.search(null)
            }
            // KMK -->
            BackHandler(enabled = state.selectionMode) {
                extensionsScreenModel.clearSelection()
            }
            // KMK <--
            ExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { extension ->
                    // KMK -->
                    if (state.longPressSelection) {
                        extensionsScreenModel.toggleSelection(extension)
                    } else {
                        // KMK <--
                        when (extension) {
                            is Extension.Available -> extensionsScreenModel.installExtension(extension)
                            else -> {
                                if (context.isPackageInstalled(extension.pkgName)) {
                                    extensionsScreenModel.uninstallExtension(extension)
                                } else {
                                    privateExtensionToUninstall = extension
                                }
                            }
                        }
                        // KMK -->
                    }
                    // KMK <--
                },
                onClickItemCancel = extensionsScreenModel::cancelInstallUpdateExtension,
                onClickUpdateAll = extensionsScreenModel::updateAllExtensions,
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let {
                        navigator.push(
                            WebViewScreen(
                                url = it.baseUrl,
                                initialTitle = it.name,
                                sourceId = it.id,
                            ),
                        )
                    }
                },
                onInstallExtension = extensionsScreenModel::installExtension,
                onOpenExtension = { navigator.push(ExtensionDetailsScreen(it.pkgName)) },
                onTrustExtension = { extensionsScreenModel.trustExtension(it) },
                onUninstallExtension = { extensionsScreenModel.uninstallExtension(it) },
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onRefresh = extensionsScreenModel::findAvailableExtensions,
                // KMK -->
                selection = state.selection,
                onToggleSelection = extensionsScreenModel::toggleSelection,
                downloads = downloads,
                // KMK <--
            )

            privateExtensionToUninstall?.let { extension ->
                ExtensionUninstallConfirmation(
                    extensionName = extension.name,
                    onClickConfirm = {
                        extensionsScreenModel.uninstallExtension(extension)
                    },
                    onDismissRequest = {
                        privateExtensionToUninstall = null
                    },
                )
            }

            // KMK -->
            if (confirmUninstallSelected) {
                AlertDialog(
                    title = {
                        Text(text = stringResource(MR.strings.ext_confirm_remove))
                    },
                    text = {
                        Text(
                            text = stringResource(
                                KMR.strings.ext_remove_selected_message,
                                selectedExtensions.count { it !is Extension.Available },
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                extensionsScreenModel.uninstallSelected()
                                confirmUninstallSelected = false
                            },
                        ) {
                            Text(text = stringResource(MR.strings.ext_remove))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmUninstallSelected = false }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    onDismissRequest = { confirmUninstallSelected = false },
                )
            }
            if (confirmTrustSelected) {
                AlertDialog(
                    title = {
                        Text(text = stringResource(MR.strings.untrusted_extension))
                    },
                    text = {
                        Text(text = stringResource(MR.strings.untrusted_extension_message))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                extensionsScreenModel.trustSelected()
                                confirmTrustSelected = false
                            },
                        ) {
                            Text(text = stringResource(MR.strings.ext_trust))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmTrustSelected = false }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                    },
                    onDismissRequest = { confirmTrustSelected = false },
                )
            }
            // KMK <--
        },
    )
}

// KMK -->
@Composable
private fun ExtensionSelectionToolbar(
    selectedCount: Int,
    canInstall: Boolean,
    canUpdate: Boolean,
    canTrust: Boolean,
    canUninstall: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onTrust: () -> Unit,
    onUninstall: () -> Unit,
) {
    val actions: ImmutableList<AppBar.AppBarAction> = persistentListOf<AppBar.AppBarAction>().builder().apply {
        if (canUninstall) {
            add(
                AppBar.Action(
                    title = stringResource(MR.strings.ext_uninstall),
                    icon = Icons.Outlined.Delete,
                    onClick = onUninstall,
                ),
            )
        }
        if (canInstall) {
            add(
                AppBar.Action(
                    title = stringResource(MR.strings.ext_install),
                    icon = Icons.Outlined.GetApp,
                    onClick = onInstall,
                ),
            )
        }
        if (canUpdate) {
            add(
                AppBar.Action(
                    title = stringResource(MR.strings.ext_update),
                    icon = Icons.Outlined.Refresh,
                    onClick = onUpdate,
                ),
            )
        }
        if (canTrust) {
            add(
                AppBar.Action(
                    title = stringResource(MR.strings.ext_trust),
                    icon = Icons.Outlined.VerifiedUser,
                    onClick = onTrust,
                ),
            )
        }
        add(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_select_all),
                onClick = onSelectAll,
            ),
        )
        add(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_select_inverse),
                onClick = onInvertSelection,
            ),
        )
    }.build()

    AppBar(
        titleContent = { AppBarTitle(selectedCount.toString()) },
        actions = { AppBarActions(actions) },
        isActionMode = true,
        onCancelActionMode = onClearSelection,
    )
}
// KMK <--

@Composable
private fun ExtensionUninstallConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.ext_confirm_remove))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_private_extension_message, extensionName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.ext_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
