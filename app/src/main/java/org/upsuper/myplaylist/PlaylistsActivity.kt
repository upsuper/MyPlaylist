package org.upsuper.myplaylist

import android.Manifest
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import kotlinx.android.synthetic.main.activity_playlists.*

private const val READ_STORAGE_REQUEST_CODE = 1;

class PlaylistsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var playlists: List<Playlist>
    private lateinit var viewModel: PlaylistModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        playlistList.layoutManager = LinearLayoutManager(this)

        prefs = getPreferences(Context.MODE_PRIVATE)
        viewModel = ViewModelProviders.of(this)[PlaylistModel::class.java]
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                READ_STORAGE_REQUEST_CODE)
        } else {
            fillData()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            READ_STORAGE_REQUEST_CODE -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fillData()
                }
            }
        }
    }

    private fun fillData() {
        Controller.getPlaylists(contentResolver) {
            playlists = it
            playlistList.adapter = PlaylistAdapter(it, viewModel) { isAnyChecked ->
                onAnyOnCheckedChange(isAnyChecked)
            }
            onAnyOnCheckedChange(viewModel.isAnyChecked())
        }
    }

    fun generate(view: View) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            val selected = viewModel.getSelectedPositions()
                .map { playlists[it].id }.toLongArray()
            putExtra(EXTRA_SELECTED_PLAYLISTS, selected)
            putExtra(EXTRA_NUMBER, numberEdit.text.toString().toInt())
        }
        startActivity(intent)
    }

    fun clearSelection(view: View) {
        viewModel.clearAll()
        playlistList.adapter?.notifyDataSetChanged()
        onAnyOnCheckedChange(false)
    }

    private fun onAnyOnCheckedChange(isAnyChecked: Boolean) {
        generateButton.isEnabled = isAnyChecked
        clearButton.isEnabled = isAnyChecked
    }
}

class PlaylistModel : ViewModel() {
    private val checkedSet = HashSet<Int>()

    fun setChecked(position: Int, checked: Boolean) {
        if (checked) {
            checkedSet.add(position)
        } else {
            checkedSet.remove(position)
        }
    }

    fun isChecked(position: Int): Boolean = checkedSet.contains(position)

    fun isAnyChecked(): Boolean = checkedSet.isNotEmpty()

    fun getSelectedPositions(): Set<Int> = checkedSet

    fun clearAll() = checkedSet.clear()
}

private class PlaylistAdapter(
    private val playlists: List<Playlist>,
    private val model: PlaylistModel,
    private val onAnyOnCheckedChangeListener: (Boolean) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    class ViewHolder(val checkBox: CheckBox) : RecyclerView.ViewHolder(checkBox)

    override fun getItemCount(): Int = playlists.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val checkBox = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_playlist_item, parent, false) as CheckBox
        return ViewHolder(checkBox)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.checkBox.apply {
            text = playlists[position].name
            isChecked = model.isChecked(position)
            setOnCheckedChangeListener { _, isChecked ->
                val wasAnyChecked = model.isAnyChecked()
                model.setChecked(position, isChecked);
                val isAnyChecked = model.isAnyChecked()
                if (wasAnyChecked != isAnyChecked) {
                    onAnyOnCheckedChangeListener(isAnyChecked)
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.checkBox.setOnCheckedChangeListener(null)
    }
}