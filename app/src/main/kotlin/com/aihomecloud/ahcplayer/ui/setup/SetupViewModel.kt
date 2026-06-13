package com.aihomecloud.ahcplayer.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.db.SourceEntity
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)

    val sources = db.sourceDao().getAll()
        .map { list ->
            list.filter { it.sourceType == "SMB" }
                .map { MediaSource(it.id, it.name, it.host, it.share, it.port) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSource(name: String, host: String, share: String, port: Int = 445) {
        viewModelScope.launch {
            db.sourceDao().insert(SourceEntity(name = name, host = host, share = share, port = port))
        }
    }
}
