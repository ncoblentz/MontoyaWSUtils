package UpgradeRequestTab

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray
import burp.api.montoya.ui.Selection
import burp.api.montoya.ui.contextmenu.WebSocketMessage
import burp.api.montoya.ui.editor.extension.EditorCreationContext
import burp.api.montoya.ui.editor.extension.ExtensionProvidedWebSocketMessageEditor
import java.awt.Component

class UpgradeRequestTab (api: MontoyaApi, creationContext: EditorCreationContext?) : ExtensionProvidedWebSocketMessageEditor {
    override fun getMessage(): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun setMessage(message: WebSocketMessage?) {
        TODO("Not yet implemented")
    }

    override fun isEnabledFor(message: WebSocketMessage?): Boolean {
        TODO("Not yet implemented")
    }

    override fun caption(): String? {
        TODO("Not yet implemented")
    }

    override fun uiComponent(): Component? {
        TODO("Not yet implemented")
    }

    override fun selectedData(): Selection? {
        TODO("Not yet implemented")
    }

    override fun isModified(): Boolean {
        TODO("Not yet implemented")
    }

}
