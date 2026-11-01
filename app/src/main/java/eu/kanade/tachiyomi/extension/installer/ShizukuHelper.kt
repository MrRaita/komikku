package eu.kanade.tachiyomi.extension.installer

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import mihon.app.shizuku.IShellInterface
import mihon.app.shizuku.ShellInterface
import rikka.shizuku.Shizuku
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

// KMK -->
/**
 * Shizuku helpers used outside of the install service: checking/requesting the permission and
 * silently uninstalling packages through the Shizuku shell service.
 */
object ShizukuHelper {

    enum class Status {
        READY,
        NEEDS_PERMISSION,
        NOT_RUNNING,
    }

    fun status(): Status = when {
        !Shizuku.pingBinder() -> Status.NOT_RUNNING
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
        else -> Status.NEEDS_PERMISSION
    }

    /** Shows Shizuku's permission dialog and waits for the answer. */
    suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                if (continuation.isActive) {
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to request Shizuku permission" }
            Shizuku.removeRequestPermissionResultListener(listener)
            if (continuation.isActive) continuation.resume(false)
        }
    }

    /**
     * Uninstalls [packageNames] one by one through the Shizuku shell. Cancelling the calling
     * coroutine stops before the next package.
     *
     * @return the packages that could not be removed, or null if the Shizuku shell was unreachable.
     */
    suspend fun uninstall(
        context: Context,
        packageNames: List<String>,
        onResult: (packageName: String, success: Boolean) -> Unit = { _, _ -> },
    ): List<String>? {
        if (packageNames.isEmpty()) return emptyList()

        val connected = CompletableDeferred<IShellInterface>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                connected.complete(IShellInterface.Stub.asInterface(service))
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        // A separate process from the install service so neither can tear down the other's shell
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShellInterface::class.java))
            .tag("shizuku_uninstall_service")
            .processNameSuffix("shizuku_uninstall_service")
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)

        return try {
            Shizuku.bindUserService(args, connection)
            val shell = withTimeoutOrNull(BIND_TIMEOUT_MS) { connected.await() } ?: return null
            val userId = Process.myUid() / PER_USER_RANGE
            val failed = mutableListOf<String>()
            for (packageName in packageNames) {
                coroutineContext.ensureActive()
                val success = try {
                    shell.uninstall(packageName, userId)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Shizuku uninstall failed for $packageName" }
                    false
                }
                if (!success) failed += packageName
                onResult(packageName, success)
            }
            failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Shizuku uninstall service failed" }
            null
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to unbind Shizuku uninstall service" }
            }
        }
    }

    private const val PERMISSION_REQUEST_CODE = 14046
    private const val BIND_TIMEOUT_MS = 10_000L
    private const val PER_USER_RANGE = 100_000
}
// KMK <--
