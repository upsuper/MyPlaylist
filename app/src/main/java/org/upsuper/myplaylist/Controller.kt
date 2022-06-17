package org.upsuper.myplaylist

import android.content.ContentResolver
import android.content.ContentValues
import android.os.AsyncTask
import android.provider.MediaStore

object Controller {
    private lateinit var playlistList: List<Playlist>

    fun getPlaylists(contentResolver: ContentResolver, callback: (List<Playlist>) -> Unit) {
        if (Controller::playlistList.isInitialized) {
            callback(playlistList)
        } else {
            PlaylistsLoader(contentResolver) {
                playlistList = it
                callback(it)
            }.execute()
        }
    }

    fun saveToPlaylist(
        contentResolver: ContentResolver,
        playlistId: Long,
        audioIds: List<Long>,
        callback: () -> Unit
    ) {
        PlaylistSaver(contentResolver, playlistId, audioIds, callback).execute()
    }
}

data class Audio(val id: Long, val title: String, val artist: String)
data class Playlist(val id: Long, val name: String, val items: List<Audio>)

private class PlaylistsLoader(
    private val contentResolver: ContentResolver,
    private val callback: (List<Playlist>) -> Unit
) : AsyncTask<Void, Void, List<Playlist>>() {

    override fun doInBackground(vararg params: Void?): List<Playlist> {
        val audioMap = mutableMapOf<Long, Audio>()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST
            ),
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            null
        )?.apply {
            val idIdx = getColumnIndex(MediaStore.Audio.Media._ID)
            val titleIdx = getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIdx = getColumnIndex(MediaStore.Audio.Media.ARTIST)
            while (moveToNext()) {
                val id = getLong(idIdx)
                val title = getString(titleIdx)
                val artist = getString(artistIdx)
                audioMap[id] = Audio(id, title, artist)
            }
        }

        val playlists = mutableListOf<Playlist>()
        contentResolver.query(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
            ),
            null,
            null,
            null
        )?.apply {
            val idIdx = getColumnIndex(MediaStore.Audio.Playlists._ID)
            val nameIdx = getColumnIndex(MediaStore.Audio.Playlists.NAME)
            while (moveToNext()) {
                val id = getLong(idIdx)
                val name = getString(nameIdx)
                val items = mutableListOf<Pair<Int, Audio>>()
                contentResolver.query(
                    MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                    arrayOf(
                        MediaStore.Audio.Playlists.Members.AUDIO_ID,
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER
                    ),
                    null,
                    null,
                    null
                )?.apply {
                    val audioIdIdx = getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID)
                    val playOrderIdx = getColumnIndex(MediaStore.Audio.Playlists.Members.PLAY_ORDER)
                    while (moveToNext()) {
                        val audioId = getLong(audioIdIdx)
                        val playOrder = getInt(playOrderIdx)
                        audioMap[audioId]?.apply { items.add(Pair(playOrder, this)) }
                    }
                }
                items.sortBy { it.first }
                playlists.add(Playlist(id, name, items.map { it.second }))
            }
        }

        playlists.sortBy { it.name }
        return playlists
    }

    override fun onPostExecute(result: List<Playlist>?) {
        super.onPostExecute(result)
        result?.apply(callback)
    }
}

private class PlaylistSaver(
    private val contentResolver: ContentResolver,
    private val playlistId: Long,
    private val audioIds: List<Long>,
    private val callback: () -> Unit
) : AsyncTask<Void, Void, Unit>() {

    override fun doInBackground(vararg params: Void?) {
        val playlistUri =
            MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
        contentResolver.delete(
            playlistUri,
            "${MediaStore.Audio.Playlists.Members.PLAYLIST_ID} = $playlistId",
            null
        )
        audioIds.forEachIndexed { index, audioId ->
            val newValues = ContentValues().apply {
                put(MediaStore.Audio.Playlists.Members.PLAYLIST_ID, playlistId)
                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId)
                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, index + 1)
            }
            contentResolver.insert(playlistUri, newValues)
        }
    }

    override fun onPostExecute(result: Unit?) {
        super.onPostExecute(result)
        callback()
    }
}