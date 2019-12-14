package org.upsuper.myplaylist

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment

private const val ARG_PLAYLIST_IDS = "playlist_ids"
private const val ARG_PLAYLIST_NAMES = "playlist_names"

class PlaylistDialogFragment : DialogFragment() {

    private lateinit var listener: PlaylistDialogListener

    interface PlaylistDialogListener {
        fun onPlaylistSelected(id: Long, name: String)
        fun onNewPlaylist()
    }

    companion object {
        fun create(playlists: List<Playlist>): PlaylistDialogFragment {
            val dialog = PlaylistDialogFragment()
            val args = Bundle()
            val playlistIds = playlists.map { it.id }.toLongArray()
            args.putLongArray(ARG_PLAYLIST_IDS, playlistIds)
            val playlistNames = playlists.map { it.name }.toTypedArray()
            args.putStringArray(ARG_PLAYLIST_NAMES, playlistNames)
            dialog.arguments = args
            return dialog
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as PlaylistDialogListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments!!
        val playlistIds = args.getLongArray(ARG_PLAYLIST_IDS)!!
        val playlistNames = args.getStringArray(ARG_PLAYLIST_NAMES)!!
        var selected: Int? = null
        return AlertDialog.Builder(activity!!)
            .setTitle(R.string.title_target_playlist)
            .setSingleChoiceItems(playlistNames, -1) { _, pos ->
                selected = pos
            }
            .setPositiveButton(R.string.button_ok) { _, _ ->
                selected?.apply {
                    listener.onPlaylistSelected(playlistIds[this], playlistNames[this])
                }
            }
            .setNegativeButton(R.string.button_cancel) { _, _ -> }
            .setNeutralButton(R.string.button_new_playlist) { _, _ ->
                listener.onNewPlaylist()
            }
            .create()
    }
}
