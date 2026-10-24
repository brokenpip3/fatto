package com.brokenpip3.fatto.data

import java.io.File

interface DateDisplayConsentStore {
    fun isAccepted(): Boolean

    fun accept(): Boolean
}

/** Pass Context.noBackupFilesDir so acknowledgement is local to this installation. */
class FileDateDisplayConsentStore(private val directory: File) : DateDisplayConsentStore {
    private val marker = File(directory, "date-display-uda-notice-v1.accepted")

    override fun isAccepted(): Boolean = runCatching { marker.isFile }.getOrDefault(false)

    override fun accept(): Boolean =
        runCatching {
            (directory.isDirectory || directory.mkdirs()) && (marker.createNewFile() || marker.isFile)
        }.getOrDefault(false)
}
