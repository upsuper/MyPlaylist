package org.upsuper.myplaylist

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.view.LayoutInflater
import android.widget.EditText

class NewPlaylistDialogFragment : DialogFragment() {

    private lateinit var listener: NewPlaylistDialogListener

    interface NewPlaylistDialogListener {
        fun onCreateNewPlaylist(name: String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as NewPlaylistDialogListener
    }

    // Suppress `InflateParams` because it is fine to pass `null` for root view for custom view in
    // `AlertDialog.Builder`. This is specifically mentioned as an exception in
    // https://wundermanthompsonmobile.com/2013/05/layout-inflation-as-intended/
    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.view_new_playlist_dialog, null)
        val editName = view.findViewById<EditText>(R.id.editPlaylistName)
        return AlertDialog.Builder(activity!!)
            .setView(view)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                listener.onCreateNewPlaylist(editName.text.toString())
            }
            .setNegativeButton(R.string.button_cancel) { _, _ -> }
            .create()
    }
}