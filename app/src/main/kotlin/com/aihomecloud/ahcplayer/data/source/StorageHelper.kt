package com.aihomecloud.ahcplayer.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageHelper {
    /** Removable volumes mounted under /storage (USB OTG drives, SD cards). */
    suspend fun getUsbVolumes(): List<File> = withContext(Dispatchers.IO) {
        val storageRoot = File("/storage")
        storageRoot.listFiles { f ->
            f.isDirectory && f.canRead() && f.name != "emulated" && f.name != "self" &&
                !f.listFiles().isNullOrEmpty()
        }?.toList() ?: emptyList()
    }
}
