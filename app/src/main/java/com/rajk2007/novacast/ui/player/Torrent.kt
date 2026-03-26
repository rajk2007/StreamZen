package com.rajk2007.novacast.ui.player

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.rajk2007.novacast.CommonActivity
import com.rajk2007.novacast.ErrorLoadingException
import com.rajk2007.novacast.app
import com.rajk2007.novacast.mvvm.logError
import com.rajk2007.novacast.utils.ExtractorLink
import com.rajk2007.novacast.utils.ExtractorLinkType
import com.rajk2007.novacast.utils.newExtractorLink
// import torrServer.TorrServer
import java.io.File
import java.net.ConnectException
import java.net.URLEncoder

object Torrent {
    var hasAcceptedTorrentForThisSession: Boolean? = null
    private const val TORRENT_SERVER_PATH: String = "torrent_tmp"
    private const val TIMEOUT: Long = 3
    private const val TAG: String = "Torrent"

    /** Cleans up both old aria2c files and newer go server, (even if the new is also self cleaning) */
    @Throws
    fun deleteAllFiles(): Boolean {
        val act = CommonActivity.activity ?: return false
        val defaultDirectory = "${act.cacheDir.path}/$TORRENT_SERVER_PATH"
        return File(defaultDirectory).deleteRecursively()
    }

    private var TORRENT_SERVER_URL = "" // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/main/server.go#L23

    /** Returns true if the server is up */
    private suspend fun echo(): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            app.get(
                "$TORRENT_SERVER_URL/echo",
            ).text.isNotEmpty()
        } catch (e: ConnectException) {
            // `Failed to connect to /127.0.0.1:8090` if the server is down
            false
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/web/api/shutdown.go#L22
    /** Gracefully shutdown the server.
     * should not be used because I am unable to start it again, and the stopTorrentServer() crashes the app */
    suspend fun shutdown(): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            app.get(
                "$TORRENT_SERVER_URL/shutdown",
            ).isSuccessful
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    /** Lists all torrents by the server */
    @Throws
    private suspend fun list(): Array<TorrentStatus> {
        if(TORRENT_SERVER_URL.isEmpty()) {
            throw ErrorLoadingException("Not initialized")
        }
        return app.post(
            "$TORRENT_SERVER_URL/torrents",
            json = TorrentRequest(
                action = "list",
            ),
            timeout = TIMEOUT,
            headers = emptyMap()
        ).parsed<Array<TorrentStatus>>()
    }

    /** Drops a single torrent, (I think) this means closing the stream. Returns returns if it is successful */
    private suspend fun drop(hash: String): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            return app.post(
                "$TORRENT_SERVER_URL/torrents",
                json = TorrentRequest(
                    action = "drop",
                    hash = hash
                ),
                timeout = TIMEOUT,
                headers = emptyMap()
            ).isSuccessful
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    /** Removes a single torrent from the server registry */
    private suspend fun rem(hash: String): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            return app.post(
                "$TORRENT_SERVER_URL/torrents",
                json = TorrentRequest(
                    action = "rem",
                    hash = hash
                ),
                timeout = TIMEOUT,
                headers = emptyMap()
            ).isSuccessful
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }


    /** Removes all torrents from the server, and returns if it is successful */
    suspend fun clearAll(): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return true
        }
        return try {
            val items = list()
            var allSuccess = true
            for (item in items) {
                val hash = item.hash
                if (hash == null) {
                    Log.i(TAG, "No hash on ${item.name}")
                    allSuccess = false
                    continue
                }
                if (drop(hash)) {
                    Log.i(TAG, "Successfully dropped ${item.name}")
                } else {
                    Log.i(TAG, "Failed to drop ${item.name}")
                    allSuccess = false
                    continue
                }
                if (rem(hash)) {
                    Log.i(TAG, "Successfully removed ${item.name}")
                } else {
                    Log.i(TAG, "Failed to remove ${item.name}")
                    allSuccess = false
                    continue
                }
            }
            allSuccess
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    /** Gets all the metadata of a torrent, will throw if that hash does not exists
     * https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/web/api/torrents.go#L126 */
    @Throws
    suspend fun get(
        hash: String,
    ): TorrentStatus {
        if(TORRENT_SERVER_URL.isEmpty()) {
            throw ErrorLoadingException("Not initialized")
        }
        return app.post(
            "$TORRENT_SERVER_URL/torrents",
            json = TorrentRequest(
                action = "get",
                hash = hash,
            ),
            timeout = TIMEOUT,
            headers = emptyMap()
        ).parsed<TorrentStatus>()
    }

    /** Adds a torrent to the server, this is needed for us to get the hash for further modification, as well as start streaming it*/
    @Throws
    private suspend fun add(url: String): TorrentStatus {
        if(TORRENT_SERVER_URL.isEmpty()) {
            throw ErrorLoadingException("Not initialized")
        }
        return app.post(
            "$TORRENT_SERVER_URL/torrents",
            json = TorrentRequest(
                action = "add",
                link = url,
            ),
            headers = emptyMap()
        ).parsed<TorrentStatus>()
    }

    /** Spins up the torrent server. */
    private suspend fun setup(dir: String): Boolean {
        /*
        // go.Seq.load()
        if (echo()) {
            return true
        }
        // val port = TorrServer.startTorrentServer(dir, 0)
        val port = -1
        if(port < 0) {
            return false
        }
        TORRENT_SERVER_URL = "http://127.0.0.1:$port"
        // TorrServer.addTrackers(trackers.joinToString(separator = ",\n"))
        return echo()
        */
        return false
    }

    /** Transforms a torrent link into a streamable link via the server */
    @Throws
    suspend fun transformLink(link: ExtractorLink): Pair<ExtractorLink, TorrentStatus> {
        val act = CommonActivity.activity ?: throw IllegalArgumentException("No activity")
        val defaultDirectory = "${act.cacheDir.path}/$TORRENT_SERVER_PATH"
        File(defaultDirectory).mkdir()
        if (!setup(defaultDirectory)) {
            throw ErrorLoadingException("Unable to setup the torrent server")
        }
        val status = add(link.url)

        return newExtractorLink(
            source = link.source,
            name = link.name,
            url = status.streamUrl(link.url),
            type = ExtractorLinkType.VIDEO
        ) {
            this.referer = ""
            this.quality = link.quality
        } to status
    }

    private val trackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "https://tracker2.ctix.cn/announce",
        "https://tracker1.520.jp:443/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "http://tracker.openbittorrent.com:80/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://open.stealth.si:80/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://explodie.org:6969/announce",
        "https://tracker.gbitt.info:443/announce",
        "http://tracker.gbitt.info:80/announce",
        "udp://uploads.gamecoast.net:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.dump.cl:6969/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "https://tracker1.520.jp:443/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "http://tracker.openbittorrent.com:80/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://open.stealth.si:80/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://explodie.org:6969/announce",
        "https://tracker.gbitt.info:443/announce",
        "http://tracker.gbitt.info:80/announce",
        "udp://uploads.gamecoast.net:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.dump.cl:6969/announce",
        "udp://tracker.bittor.pw:1337/announce"
    )


    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/web/api/torrents.go#L18
    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/main/web/api/route.go#L7
    data class TorrentRequest(
        @JsonProperty("action")
        val action: String,
        @JsonProperty("hash")
        val hash: String = "",
        @JsonProperty("link")
        val link: String = "",
        @JsonProperty("title")
        val title: String = "",
        @JsonProperty("poster")
        val poster: String = "",
        @JsonProperty("data")
        val data: String = "",
        @JsonProperty("save_to_db")
        val saveToDB: Boolean = false,
    )

    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/torr/state/state.go#L33
    // omitempty = nullable
    data class TorrentStatus(
        @JsonProperty("title")
        var title: String,
        @JsonProperty("poster")
        var poster: String,
        @JsonProperty("data")
        var data: String?,
        @JsonProperty("timestamp")
        var timestamp: Long,
        @JsonProperty("hash")
        var hash: String?,
        @JsonProperty("stat")
        var stat: Int?,
        @JsonProperty("stat_string")
        var statString: String?,
        @JsonProperty("loaded_size")
        var loadedSize: Long?,
        @JsonProperty("torrent_size")
        var torrentSize: Long?,
        @JsonProperty("pre_size")
        var preSize: Long?,
        @JsonProperty("download_speed")
        var downloadSpeed: Double?,
        @JsonProperty("upload_speed")
        var uploadSpeed: Double?,
        @JsonProperty("total_peers")
        var totalPeers: Int?,
        @JsonProperty("pending_peers")
        var pendingPeers: Int?,
        @JsonProperty("active_peers")
        var activePeers: Int?,
        @JsonProperty("half_open_peers")
        var halfOpenPeers: Int?,
        @JsonProperty("connected_seeders")
        var connectedSeeders: Int?,
        @JsonProperty("files")
        var files: Array<TorrentFile>?,
    ) {
        fun streamUrl(url: String): String {
            if(TORRENT_SERVER_URL.isEmpty()) {
                return url
            }
            return "$TORRENT_SERVER_URL/stream/${URLEncoder.encode(url, "UTF-8")}?index=0&play"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TorrentStatus

            if (title != other.title) return false
            if (poster != other.poster) return false
            if (data != other.data) return false
            if (timestamp != other.timestamp) return false
            if (hash != other.hash) return false
            if (stat != other.stat) return false
            if (statString != other.statString) return false
            if (loadedSize != other.loadedSize) return false
            if (torrentSize != other.torrentSize) return false
            if (preSize != other.preSize) return false
            if (downloadSpeed != other.downloadSpeed) return false
            if (uploadSpeed != other.uploadSpeed) return false
            if (totalPeers != other.totalPeers) return false
            if (pendingPeers != other.pendingPeers) return false
            if (activePeers != other.activePeers) return false
            if (halfOpenPeers != other.halfOpenPeers) return false
            if (connectedSeeders != other.connectedSeeders) return false
            if (files != null) {
                if (other.files == null) return false
                if (!files.contentEquals(other.files)) return false
            } else if (other.files != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + poster.hashCode()
            result = 31 * result + (data?.hashCode() ?: 0)
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + (hash?.hashCode() ?: 0)
            result = 31 * result + (stat ?: 0)
            result = 31 * result + (statString?.hashCode() ?: 0)
            result = 31 * result + (loadedSize?.hashCode() ?: 0)
            result = 31 * result + (torrentSize?.hashCode() ?: 0)
            result = 31 * result + (preSize?.hashCode() ?: 0)
            result = 31 * result + (downloadSpeed?.hashCode() ?: 0)
            result = 31 * result + (uploadSpeed?.hashCode() ?: 0)
            result = 31 * result + (totalPeers ?: 0)
            result = 31 * result + (pendingPeers ?: 0)
            result = 31 * result + (activePeers ?: 0)
            result = 31 * result + (halfOpenPeers ?: 0)
            result = 31 * result + (connectedSeeders ?: 0)
            result = 31 * result + (files?.contentHashCode() ?: 0)
            return result
        }
    }

    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/torr/state/state.go#L112
    data class TorrentFile(
        @JsonProperty("index")
        var index: Int,
        @JsonProperty("path")
        var path: String,
        @JsonProperty("length")
        var length: Long,
    )

    data class TorrentName(
        val name: String,
        val url: String,
    )
}
