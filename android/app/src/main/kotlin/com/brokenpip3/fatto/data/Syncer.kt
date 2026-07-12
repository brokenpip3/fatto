package com.brokenpip3.fatto.data

import uniffi.taskchampion_android.ReplicaWrapper

/**
 * Dispatches a sync to the backend selected in settings: either a
 * `taskchampion-sync-server` instance or an AWS S3 / S3-compatible bucket.
 *
 * Keeping this in one place ensures the manual sync, the reactive post-mutation
 * sync, and the background [com.brokenpip3.fatto.worker.SyncWorker] all behave
 * identically.
 */
object Syncer {
    /**
     * Run a sync using the configured backend.
     *
     * @return true if credentials were configured and a sync was attempted,
     *   false if there is nothing to sync against. Any error raised by the
     *   underlying replica is propagated to the caller.
     */
    fun sync(
        replica: ReplicaWrapper,
        settings: SettingsRepository,
    ): Boolean {
        return when (settings.getSyncType()) {
            SyncType.S3 ->
                settings.getS3Credentials()?.let { creds ->
                    replica.syncAws(
                        creds.bucket,
                        creds.region,
                        creds.endpointUrl,
                        creds.accessKeyId,
                        creds.secretAccessKey,
                        creds.secret,
                    )
                    true
                } ?: false
            SyncType.SERVER ->
                settings.getCredentials()?.let { creds ->
                    replica.sync(creds.url, creds.clientId, creds.secret)
                    true
                } ?: false
        }
    }
}
