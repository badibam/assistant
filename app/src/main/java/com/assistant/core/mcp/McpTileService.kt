package com.assistant.core.mcp

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.assistant.core.strings.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A tile of the quick settings that opens and closes the external access in one touch, and shows whether it is open. */
class McpTileService : TileService() {

    private var watching: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        watching = CoroutineScope(Dispatchers.Main).launch {
            McpAccess.state.collect { show(it) }
        }
    }

    override fun onStopListening() {
        watching?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (McpAccess.state.value is McpAccess.State.Open) McpAccess.close(this) else McpAccess.open(this)
    }

    private fun show(state: McpAccess.State) {
        val tile = qsTile ?: return
        tile.label = Strings.`for`(context = this).shared("external_access_tile")
        tile.state = if (state is McpAccess.State.Open) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
