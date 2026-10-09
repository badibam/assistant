package app.treelune.core.mcp

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.treelune.core.strings.Strings
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
        if (running(McpAccess.state.value)) McpAccess.close(this) else McpAccess.open(this)
    }

    private fun show(state: McpAccess.State) {
        val tile = qsTile ?: return
        tile.label = Strings.`for`(context = this).shared("external_access_tile")
        tile.state = if (running(state)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    /** Open, or on its way with the Tailscale node waiting on a step: a touch closes it. */
    private fun running(state: McpAccess.State) = state is McpAccess.State.Open || state is McpAccess.State.Preparing
}
