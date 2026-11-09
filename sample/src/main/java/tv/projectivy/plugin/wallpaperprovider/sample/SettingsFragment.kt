package tv.projectivy.plugin.wallpaperprovider.sample

import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction

class SettingsFragment : GuidedStepSupportFragment() {
    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance(
            getString(R.string.plugin_name),
            "v${BuildConfig.VERSION_NAME}\n\n${getString(R.string.plugin_description)}",
            getString(R.string.settings),
            AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_plugin)
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        PreferencesManager.init(requireContext())

        actions.add(editAction(ACTION_TEMPLATE, R.string.setting_template_title, PreferencesManager.urlTemplate))
        actions.add(editAction(ACTION_FALLBACK, R.string.setting_fallback_title, PreferencesManager.fallbackUrl))
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_COLOR)
                .title(R.string.setting_color_title)
                .description(colorLabel(PreferencesManager.useCardColor))
                .build()
        )
    }

    private fun editAction(id: Long, title: Int, value: String) =
        GuidedAction.Builder(context)
            .id(id)
            .title(title)
            .description(value.ifEmpty { getString(R.string.not_set) })
            .editDescription(value)
            .descriptionEditable(true)
            .build()

    private fun colorLabel(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_TEMPLATE, ACTION_FALLBACK -> {
                val value = action.editDescription?.toString().orEmpty().trim()
                action.description = value.ifEmpty { getString(R.string.not_set) }
                notifyActionChanged(findActionPositionById(action.id))
                if (action.id == ACTION_TEMPLATE) PreferencesManager.urlTemplate = value
                else PreferencesManager.fallbackUrl = value
            }
            ACTION_COLOR -> {
                val newValue = !PreferencesManager.useCardColor
                PreferencesManager.useCardColor = newValue
                action.description = colorLabel(newValue)
                notifyActionChanged(findActionPositionById(action.id))
            }
        }
    }

    companion object {
        private const val ACTION_TEMPLATE = 1L
        private const val ACTION_FALLBACK = 2L
        private const val ACTION_COLOR = 3L
    }
}
