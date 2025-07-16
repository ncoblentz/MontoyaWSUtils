import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.contextmenu.WebSocketContextMenuEvent
import burp.api.montoya.ui.contextmenu.WebSocketMessage
import burp.api.montoya.ui.editor.EditorOptions
import burp.api.montoya.ui.settings.SettingsPanelBuilder
import burp.api.montoya.ui.settings.SettingsPanelPersistence
import com.nickcoblentz.montoya.settings.PanelSettingsDelegate
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import javax.swing.JFrame
import javax.swing.JMenuItem


// Montoya API Documentation: https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html
// Montoya Extension Examples: https://github.com/PortSwigger/burp-extensions-montoya-api-examples

class YourBurpKotlinExtensionName : BurpExtension , ContextMenuItemsProvider {
    private var webSocketMessages: MutableList<WebSocketMessage> = mutableListOf()


    private lateinit var api: MontoyaApi
    private val projectSettings : MyProjectSettings by lazy { MyProjectSettings() }

    private val showUpgradeRequestMenuItem = JMenuItem("Show Upgrade Request")

    companion object {
        const val EXTENSION_NAME = "WS Utils"
    }




    override fun initialize(api: MontoyaApi?) {

        // In Kotlin, you have to explicitly define variables as nullable with a ? as in MontoyaApi? above
        // This is necessary because the Java Library allows null to be passed into this function
        // requireNotNull is a built-in Kotlin function to check for null that throws an Illegal Argument exception if it is null
        // after checking for null, the Kotlin compiler knows that any reference to api  or this.api below will not = null and you no longer have to check it
        // Finally, assign the MontoyaApi instance (not nullable) to a class property to be accessible from other functions in this class
        this.api = requireNotNull(api) { "api : MontoyaApi is not allowed to be null" }
        // This will print to Burp Suite's Extension output and can be used to debug whether the extension loaded properly
        api.logging().logToOutput("Started loading the extension...")


        // Name our extension when it is displayed inside of Burp Suite
        api.extension().setName(EXTENSION_NAME)

        // Code for setting up your extension starts here...


        showUpgradeRequestMenuItem.addActionListener {
            webSocketMessages.forEach { message ->
                message.upgradeRequest()?.let { upgradeRequest ->
                    val requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY, EditorOptions.WRAP_LINES)
                    requestEditor.request=upgradeRequest
                    val editorFrame = JFrame("HTTP Request Editor - " + upgradeRequest.url())
                    editorFrame.contentPane.add(requestEditor.uiComponent(), BorderLayout.CENTER);
                    //editorFrame.setSize(800, 600); // Set a preferred size
                    val toolkit: Toolkit = Toolkit.getDefaultToolkit()
                    val screenSize: Dimension = toolkit.screenSize
                    val frameWidth = (screenSize.width * 0.50).toInt()
                    val frameHeight = (screenSize.height * 0.50).toInt()
                    editorFrame.setSize(frameWidth, frameHeight)
                    editorFrame.setLocationRelativeTo(null); // Center the window on the screen
                    editorFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Close only this window
                    editorFrame.isVisible = true;
                }
            }
        }

        api.userInterface().registerContextMenuItemsProvider(this)

        // Code for setting up your extension ends here

        // See logging comment above
        api.logging().logToOutput("...Finished loading the extension")

    }




    override fun provideMenuItems(event: WebSocketContextMenuEvent?): List<Component?>? {

        webSocketMessages.clear()
        event?.let { e ->
            if(e.messageEditorWebSocket().isPresent) {
                webSocketMessages = mutableListOf(e.messageEditorWebSocket().get().webSocketMessage())
            }
            if(e.selectedWebSocketMessages().isNotEmpty()) {
                webSocketMessages = e.selectedWebSocketMessages()
            }

            if(webSocketMessages.isNotEmpty()) {
                return listOf(showUpgradeRequestMenuItem)
            }
        }
        return super.provideMenuItems(event)
    }
}


class MyProjectSettings() {
    val settingsPanelBuilder : SettingsPanelBuilder = SettingsPanelBuilder.settingsPanel()
        .withPersistence(SettingsPanelPersistence.PROJECT_SETTINGS) // you can change this to user settings if you wish
        .withTitle(YourBurpKotlinExtensionName.EXTENSION_NAME)
        .withDescription("Add your description here")
        .withKeywords("Add Keywords","Here")

    private val settingsManager = PanelSettingsDelegate(settingsPanelBuilder)

    val example1Setting: String by settingsManager.stringSetting("An example string setting here", "test default value here")
    val example2Setting: Boolean by settingsManager.booleanSetting("An example boolean setting here", false)

    val settingsPanel = settingsManager.buildSettingsPanel()
}