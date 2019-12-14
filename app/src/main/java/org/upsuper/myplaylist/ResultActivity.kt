package org.upsuper.myplaylist

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import android.content.*
import android.content.pm.PackageManager
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_result.*
import kotlin.math.floor
import kotlin.random.Random

const val EXTRA_SELECTED_PLAYLISTS = "selected_playlists"
const val EXTRA_NUMBER = "number"
private const val WRITE_STORAGE_REQUEST_CODE = 2;
private const val PREF_SORT_DIRECTION = "sort_direction"

class ResultActivity : AppCompatActivity(),
    PlaylistDialogFragment.PlaylistDialogListener,
    NewPlaylistDialogFragment.NewPlaylistDialogListener {

    private lateinit var allAudio: List<Pair<Audio, Playlist>>
    private var number: Int = 0

    private val adapter = ResultAdapter()
    private lateinit var prefs: SharedPreferences
    private lateinit var viewModel: ResultModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        resultList.layoutManager = LinearLayoutManager(this)
        resultList.adapter = adapter

        prefs = getPreferences(Context.MODE_PRIVATE)
        number = intent.getIntExtra(EXTRA_NUMBER, 0)
        viewModel = ViewModelProviders.of(this)[ResultModel::class.java]

        val sortDirection = prefs.getString(PREF_SORT_DIRECTION, null)
        if (sortDirection != null) {
            try {
                viewModel.sortDirection = SortDirection.valueOf(sortDirection)
            }
            catch (e: IllegalArgumentException) { }
        }

        val selectedPlaylists = intent.getLongArrayExtra(EXTRA_SELECTED_PLAYLISTS).asList()
        Controller.getPlaylists(contentResolver) { playlistList ->
            allAudio = selectedPlaylists.mapNotNull { id ->
                playlistList.find { playlist -> playlist.id == id }
            }.flatMap { playlist ->
                playlist.items.asIterable().map { audio -> Pair(audio, playlist) }
            }
            viewModel.mayInit { generateResult() }
            notifyNewResult()
        }
    }

    fun toggleSort(view: View) {
        viewModel.sortDirection = when (viewModel.sortDirection) {
            SortDirection.ASCENDING -> SortDirection.DESCENDING
            SortDirection.DESCENDING -> SortDirection.ASCENDING
        }
        prefs.edit().putString(PREF_SORT_DIRECTION, viewModel.sortDirection.name).apply()
        notifyNewResult()
    }

    fun regenerate(view: View) {
        viewModel.result = generateResult()
        notifyNewResult()
    }

    private fun generateResult(): List<ResultItem> {
        val idxList: List<Int>
        idxList = if (allAudio.size <= number) {
            (0..allAudio.size).toList()
        } else {
            val idxSet = HashSet<Int>()
            while (idxSet.size < number) {
                idxSet.add(floor(Random.Default.nextFloat() * allAudio.size).toInt())
            }
            idxSet.toList().sorted()
        }
        return idxList.map { i ->
            val pair = allAudio[i]
            ResultItem(pair.first, pair.second)
        }
    }

    private fun notifyNewResult() {
        adapter.setNewResult(viewModel.sortedResult)
        saveButton.isEnabled = true
    }

    fun save(view: View) {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                WRITE_STORAGE_REQUEST_CODE)
        } else {
            openSaveDialog()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            WRITE_STORAGE_REQUEST_CODE -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openSaveDialog()
                }
            }
        }
    }

    private fun openSaveDialog() {
        Controller.getPlaylists(contentResolver) { playlists ->
            PlaylistDialogFragment.create(playlists)
                .show(supportFragmentManager, "PlaylistDialogFragment")
        }
    }

    override fun onPlaylistSelected(id: Long, name: String) {
        val audioIds = viewModel.sortedResult.map { it.audio.id }
        Controller.saveToPlaylist(contentResolver, id, audioIds) {
            val text = getString(R.string.message_saved_to, name)
            val toast = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)
            toast.show()
        }
    }

    override fun onNewPlaylist() {
        val dialog = NewPlaylistDialogFragment()
        dialog.show(supportFragmentManager, "NewPlaylistDialogFragment")
    }

    override fun onCreateNewPlaylist(name: String) {
        PlaylistCreator(contentResolver, name) { id ->
            onPlaylistSelected(id, name)
        }.execute()
    }
}

enum class SortDirection {
    ASCENDING, DESCENDING,
}

class ResultModel : ViewModel() {
    lateinit var result: List<ResultItem>
    var sortDirection = SortDirection.ASCENDING

    fun mayInit(init: () -> List<ResultItem>) {
        if (!this::result.isInitialized) {
            result = init()
        }
    }

    val sortedResult: List<ResultItem>
        get() = when (sortDirection) {
            SortDirection.ASCENDING -> result
            SortDirection.DESCENDING -> result.asReversed()
        }
}

data class ResultItem(val audio: Audio, val playlist: Playlist)

private class ResultAdapter : RecyclerView.Adapter<ResultAdapter.ViewHolder>() {

    private var result: List<ResultItem> = listOf()

    fun setNewResult(newResult: List<ResultItem>) {
        result = newResult
        notifyDataSetChanged()
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount(): Int = result.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = result[position]
        holder.view.apply {
            val title = SpannableStringBuilder(item.audio.title)
                .append(
                    " - " + item.audio.artist,
                    TextAppearanceSpan(context, R.style.textAppearanceResultArtist),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            findViewById<TextView>(R.id.textResultTitle).text = title
            findViewById<TextView>(R.id.textResultPlaylist).text = item.playlist.name
        }
    }
}

private class PlaylistCreator(
    private val contentResolver: ContentResolver,
    private val name: String,
    private val callback: (Long) -> Unit
) : AsyncTask<Void, Void, Long>() {

    override fun doInBackground(vararg params: Void?): Long {
        val newValues = ContentValues().apply {
            put(MediaStore.Audio.Playlists.NAME, name)
        }
        val newUri = contentResolver.insert(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, newValues)
        return ContentUris.parseId(newUri)
    }

    override fun onPostExecute(result: Long?) {
        super.onPostExecute(result)
        result?.apply(callback)
    }
}
