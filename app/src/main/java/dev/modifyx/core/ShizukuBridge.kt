package dev.modifyx.core

import rikka.shizuku.Shizuku

/**
 * Thin, crash-safe adapter over the Shizuku API. Shizuku must be installed and running,
 * and the user has to grant this app the API permission once.
 */
object ShizukuBridge {

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isGranted(): Boolean = runCatching {
        isBinderAlive() && Shizuku.checkSelfPermission() == 0
    }.getOrDefault(false)

    fun version(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(-1)

    fun requestPermission(requestCode: Int): Boolean = runCatching {
        if (!isBinderAlive()) return false
        if (isGranted()) return true
        Shizuku.requestPermission(requestCode)
        true
    }.getOrDefault(false)

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun addBinderListener(listener: Shizuku.OnBinderReceivedListener) {
        runCatching { Shizuku.addBinderReceivedListener(listener) }
    }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        runCatching { Shizuku.addBinderDeadListener(listener) }
    }

    /** Binds the shell user service. Requires the API permission to be granted. */
    fun bindUserService(
        args: Shizuku.UserServiceArgs,
        connection: android.content.ServiceConnection,
    ): Boolean = runCatching {
        if (!isGranted()) return false
        Shizuku.bindUserService(args, connection)
        true
    }.getOrDefault(false)

    fun unbindUserService(
        args: Shizuku.UserServiceArgs,
        connection: android.content.ServiceConnection,
    ) {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
    }
}
