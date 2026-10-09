package app.treelune.core.fields

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.selectors.Named
import app.treelune.core.ui.selectors.ThingBrowser
import app.treelune.core.ui.selectors.ThingPath
import app.treelune.core.utils.LogManager
import androidx.compose.runtime.saveable.Saver

/** The name of [reference] as it is now, loaded once shown: null while loading. */
@Composable
internal fun rememberReferenceName(reference: Reference?, context: Context): String? {
    val s = remember { Strings.`for`(context = context) }
    var shown by remember(reference) { mutableStateOf<String?>(null) }
    LaunchedEffect(reference) {
        if (reference == null) return@LaunchedEffect
        shown = try {
            when (val name = loadReferenceNames(listOf(reference), context)[reference]) {
                is ReferenceName.Named -> name.name
                ReferenceName.Deleted, null -> s.shared("pointer_target_deleted")
            }
        } catch (e: Exception) {
            LogManager.ui("Reference $reference: name not read: ${e.message}", "ERROR", e)
            s.shared("field_reference_unreadable")
        }
    }
    return shown
}

/** A REFERENCE value: the name of what it designates, as it is now. */
@Composable
fun ReferenceValue(value: Any?, context: Context) {
    val s = remember { Strings.`for`(context = context) }
    val reference = ReferenceTarget.referenceOf(value)
    if (reference == null) {
        UI.Text(text = value.toString(), type = TextType.BODY)
        return
    }
    UI.Text(text = rememberReferenceName(reference, context) ?: s.shared("tools_loading"), type = TextType.BODY)
}

/**
 * The input of a REFERENCE field: what it designates by name, a picker opened on touch, and a
 * button emptying it when it is optional.
 */
@Composable
fun ReferenceInput(fieldDef: FieldDefinition, value: Any?, onChange: (Any?) -> Unit, context: Context, required: Boolean) {
    val reference = ReferenceTarget.referenceOf(value)
    val name = rememberReferenceName(reference, context)
    var picking by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            UI.FormField(
                label = fieldDef.displayName,
                value = if (reference == null) "" else name ?: "",
                onChange = {},
                required = required,
                readonly = true,
                onClick = { picking = true }
            )
        }
        if (!required && reference != null) {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                display = app.treelune.core.ui.ButtonDisplay.ICON,
                size = app.treelune.core.ui.Size.S,
                onClick = { onChange(null) }
            )
        }
    }

    if (picking) {
        ReferencePicker(
            target = ReferenceTarget.fromConfig(fieldDef.config),
            onDismiss = { picking = false },
            onPick = { picked ->
                picking = false
                onChange(mapOf("kind" to picked.kind.name) + (picked.id?.let { mapOf("id" to it) } ?: emptyMap()))
            }
        )
    }
}

/** Where the picker opens: a single tool whose entries alone are taken is entered at once. */
private suspend fun startPath(target: ReferenceTarget, context: Context): ThingPath {
    val only = target.toolInstances.singleOrNull()
    if (target.kinds != setOf(ReferenceKind.ENTRY) || only == null) return ThingPath()
    return app.treelune.core.ui.selectors.toolPath(only, context) ?: ThingPath()
}

/**
 * The things a reference of [target] may designate, reached through the app's one way to them
 * (ThingBrowser), and chosen once the place reached is of a kind the field takes.
 */
@Composable
internal fun ReferencePicker(target: ReferenceTarget, onDismiss: () -> Unit, onPick: (Reference) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // null until the place it opens on is known
    var path by rememberSaveable(stateSaver = ThingPathSaver) { mutableStateOf<ThingPath?>(null) }
    LaunchedEffect(Unit) { if (path == null) path = startPath(target, context) }

    val current = path
    UI.Dialog(
        type = DialogType.CONFIRM,
        confirmEnabled = current != null && current.kind in target.kinds,
        onConfirm = { current?.let { onPick(it.reference) } },
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.Text(text = s.shared("field_reference_pick"), type = TextType.TITLE, fillMaxWidth = true)
            if (current == null) UI.LoadingIndicator()
            else ThingBrowser(current, { path = it }, target)
        }
    }
}

/** The place reached across a rotation, null before it opens. */
private val ThingPathSaver: Saver<ThingPath?, String> = Saver(
    save = { it?.toJson() ?: "" },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let { ThingPath.fromJson(it) } }
)
