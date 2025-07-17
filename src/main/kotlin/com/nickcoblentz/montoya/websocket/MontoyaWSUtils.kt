package com.nickcoblentz.montoya.websocket

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.proxy.websocket.BinaryMessageReceivedAction
import burp.api.montoya.proxy.websocket.BinaryMessageToBeSentAction
import burp.api.montoya.proxy.websocket.InterceptedBinaryMessage
import burp.api.montoya.proxy.websocket.InterceptedTextMessage
import burp.api.montoya.proxy.websocket.ProxyMessageHandler
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreation
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreationHandler
import burp.api.montoya.proxy.websocket.TextMessageReceivedAction
import burp.api.montoya.proxy.websocket.TextMessageToBeSentAction
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.contextmenu.WebSocketContextMenuEvent
import burp.api.montoya.ui.contextmenu.WebSocketMessage
import burp.api.montoya.ui.editor.EditorOptions
import burp.api.montoya.ui.settings.SettingsPanelBuilder
import burp.api.montoya.ui.settings.SettingsPanelPersistence
import burp.api.montoya.websocket.extension.ExtensionWebSocketMessageHandler
import com.nickcoblentz.montoya.settings.PanelSettingsDelegate
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JTextField


// Montoya API Documentation: https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html
// Montoya Extension Examples: https://github.com/PortSwigger/burp-extensions-montoya-api-examples

class MontoyaWSUtils : BurpExtension , ContextMenuItemsProvider, ProxyWebSocketCreationHandler {

    companion object {
        const val EXTENSION_NAME = "WS Utils"
        const val DEFAULT_WS_REQUEST_LIMIT = 25
    }

    private var selectedProxyCreation: ProxyWebSocketCreation? = null
    private val proxyWebSocketCreations = mutableListOf<ProxyWebSocketCreation>()
    private var webSocketMessages: MutableList<WebSocketMessage> = mutableListOf()
    private val executorService = Executors.newVirtualThreadPerTaskExecutor()



    private lateinit var api: MontoyaApi
    private val projectSettings : MyProjectSettings by lazy { MyProjectSettings() }
    private var virtualThreadLimitSemaphore = Semaphore(DEFAULT_WS_REQUEST_LIMIT)
    private var currentThreadLimit = DEFAULT_WS_REQUEST_LIMIT

    private val showUpgradeRequestMenuItem = JMenuItem("Show Upgrade Request")
    private val showIntruderIntegerMenu = JMenuItem("Intruder: Integers")
    private var selectedWebSocketMessage: WebSocketMessage? = null


    private fun resetSemaphore() {
        if(projectSettings.wsRequestLimit != currentThreadLimit) {
            virtualThreadLimitSemaphore = Semaphore(projectSettings.wsRequestLimit)
            currentThreadLimit = projectSettings.wsRequestLimit
        }
        api.logging().logToOutput("WebSocket Request Limit set to: $currentThreadLimit")
    }

    override fun initialize(api: MontoyaApi?) {

        this.api = requireNotNull(api) { "api : MontoyaApi is not allowed to be null" }
        // This will print to Burp Suite's Extension output and can be used to debug whether the extension loaded properly
        api.logging().logToOutput("Started loading the extension...")


        api.extension().setName(EXTENSION_NAME)


        api.userInterface().registerSettingsPanel(projectSettings.settingsPanel)

        resetSemaphore()

        showUpgradeRequestMenuItem.addActionListener {
            webSocketMessages.forEach { message ->
                message.upgradeRequest()?.let { upgradeRequest ->
                    val requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY, EditorOptions.WRAP_LINES)
                    requestEditor.request=upgradeRequest
                    val burpFrame = BurpGuiFrame("HTTP Request Editor - " + upgradeRequest.url())

                    burpFrame.frame.contentPane.add(requestEditor.uiComponent(), BorderLayout.CENTER)
                    burpFrame.showFrame()
                }
            }
        }

        showIntruderIntegerMenu.addActionListener {
            webSocketMessages.forEach { message ->
                val burpFrame = BurpGuiFrame("WS Intruder")

                val webSocketConnectionsComboBox = JComboBox(proxyWebSocketCreations.indices.map { index ->
                    val creation = proxyWebSocketCreations[index]
                    val item = "$index ${creation.upgradeRequest().url()}"
                    api.logging().logToOutput("Adding item to combo box: $item")
                    item
                }.reversed().toTypedArray())

                val startIntegerField = JTextField("0", 10) // Default start value, 10 columns wide
                val endIntegerField = JTextField("100", 10) // Default end value, 10 columns wide
                val replaceField = JTextField("REPLACEME", 10) // Default end value, 10 columns wide


                val mainPanel = JPanel(GridBagLayout())
                val gbc = GridBagConstraints()
                gbc.insets = Insets(5, 5, 5, 5) // Padding

                // WebSocket Connections Label and ComboBox
                gbc.gridx = 0
                gbc.gridy = 0
                gbc.anchor = GridBagConstraints.WEST
                mainPanel.add(JLabel("Select WebSocket Connection:"), gbc)

                gbc.gridx = 1
                gbc.gridy = 0
                gbc.fill = GridBagConstraints.HORIZONTAL
                mainPanel.add(webSocketConnectionsComboBox, gbc)

                // Starting Integer Label and Field
                gbc.gridx = 0
                gbc.gridy = 1
                gbc.fill = GridBagConstraints.NONE // Reset fill
                mainPanel.add(JLabel("Starting Integer:"), gbc)

                gbc.gridx = 1
                gbc.gridy = 1
                gbc.fill = GridBagConstraints.HORIZONTAL
                mainPanel.add(startIntegerField, gbc)

                // Ending Integer Label and Field
                gbc.gridx = 0
                gbc.gridy = 2
                gbc.fill = GridBagConstraints.NONE
                mainPanel.add(JLabel("Ending Integer:"), gbc)

                gbc.gridx = 1
                gbc.gridy = 2
                gbc.fill = GridBagConstraints.HORIZONTAL
                mainPanel.add(endIntegerField, gbc)


                gbc.gridx = 0
                gbc.gridy = 3
                gbc.fill = GridBagConstraints.NONE
                mainPanel.add(JLabel("Replace This String (All Instances):"), gbc)

                gbc.gridx = 1
                gbc.gridy = 3
                gbc.fill = GridBagConstraints.HORIZONTAL
                mainPanel.add(replaceField, gbc)


                // Buttons Panel
                val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
                val startButton = JButton("Start")

                startButton.addActionListener {
                    resetSemaphore()
                    val startInteger = startIntegerField.text.toInt()
                    val endInteger = endIntegerField.text.toInt()
                    val replaceString = replaceField.text

                    Thread.ofVirtual().start {
                    val selectedWebSocketConnection = webSocketConnectionsComboBox.selectedItem as String
                    selectedWebSocketConnection.substringBefore(" ").toIntOrNull()?.let { index ->
                        api.logging().logToOutput("Starting WS Intruder on connection $index")

                        api.logging().logToOutput("Replace Value Found?: ${message.payload().toString().contains(replaceString)}")

                        selectedProxyCreation = proxyWebSocketCreations[index]
                        for(i in startInteger..endInteger) {
                            virtualThreadLimitSemaphore.acquire()
                            try {
                                Thread.ofVirtual().start {
                                    val newMessage = message.payload().toString().replace(replaceString, i.toString())
                                    api.logging()
                                        .logToOutput("Current Progress ====================\n$startInteger <= $i <= $endInteger \n${selectedProxyCreation} ${selectedProxyCreation?.proxyWebSocket()}\n-------------------")
                                    if(proxyWebSocketCreations.contains(selectedProxyCreation)) {
                                        api.logging()
                                            .logToOutput("Sending Message (${message.direction()}):\n$newMessage\n-----------------")
                                        selectedProxyCreation?.proxyWebSocket()?.sendTextMessage(newMessage, message.direction())
                                    }
                                    else {
                                        api.logging().logToOutput("Proxy WebSocket Connection is no longer there...")
                                        api.logging().logToError("Proxy WebSocket Connection is no longer there... $index: ${selectedProxyCreation?.upgradeRequest()?.url()}")
                                    }
                                }
                            }
                            catch (e: Exception) {
                                api.logging().logToError("Error running virtual thread: ${e.message}\n${e.stackTraceToString()}")
                            }
                            finally {
                                virtualThreadLimitSemaphore.release()
                            }
                        }

                    }
                        }


                }

                val cancelButton = JButton("Cancel")

                cancelButton.addActionListener {
                    burpFrame.frame.dispose()
                }

                buttonPanel.add(cancelButton)
                buttonPanel.add(startButton)

                gbc.gridx = 0
                gbc.gridy = 4
                gbc.gridwidth = 2 // Span across two columns
                gbc.anchor = GridBagConstraints.CENTER
                mainPanel.add(buttonPanel, gbc)

                burpFrame.frame.contentPane.add(mainPanel, BorderLayout.CENTER)

                burpFrame.showFrame()
            }
        }

        api.userInterface().registerContextMenuItemsProvider(this)

        api.proxy().registerWebSocketCreationHandler(this)

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
                return listOf(showUpgradeRequestMenuItem,showIntruderIntegerMenu)
            }
        }
        return super.provideMenuItems(event)
    }

    override fun handleWebSocketCreation(proxyWebSocketCreation: ProxyWebSocketCreation?) {
        proxyWebSocketCreation?.let {creation ->
            creation.proxyWebSocket().registerProxyMessageHandler((object : ProxyMessageHandler {
                override fun handleTextMessageReceived(interceptedTextMessage: InterceptedTextMessage): TextMessageReceivedAction {
                    return TextMessageReceivedAction.continueWith(interceptedTextMessage)
                }

                override fun handleTextMessageToBeSent(interceptedTextMessage: InterceptedTextMessage?): TextMessageToBeSentAction {
                    return TextMessageToBeSentAction.continueWith(interceptedTextMessage)
                }

                override fun handleBinaryMessageReceived(interceptedBinaryMessage: InterceptedBinaryMessage?): BinaryMessageReceivedAction {
                    return BinaryMessageReceivedAction.continueWith(interceptedBinaryMessage)
                }

                override fun handleBinaryMessageToBeSent(interceptedBinaryMessage: InterceptedBinaryMessage?): BinaryMessageToBeSentAction {
                    return BinaryMessageToBeSentAction.continueWith(interceptedBinaryMessage)
                }

                override fun onClose() {
                    super.onClose()
                    proxyWebSocketCreations.remove(creation)
                    api.logging().logToOutput("Removing one - closed")

                    if(projectSettings.wsBumpWSConnection && selectedProxyCreation != null) {
                        selectedProxyCreation = proxyWebSocketCreations.lastOrNull()
                    }

                }
            }))
            proxyWebSocketCreations.add(creation)
            api.logging().logToOutput("WebSocket Connection Created: ${creation.upgradeRequest().url()}")
        }
    }


}


class MyProjectSettings() {
    val settingsPanelBuilder : SettingsPanelBuilder = SettingsPanelBuilder.settingsPanel()
        .withPersistence(SettingsPanelPersistence.PROJECT_SETTINGS) // you can change this to user settings if you wish
        .withTitle(MontoyaWSUtils.EXTENSION_NAME)
        .withDescription("Add your description here")
        .withKeywords("Add Keywords","Here")

    private val settingsManager = PanelSettingsDelegate(settingsPanelBuilder)

    val wsRequestLimit: Int by settingsManager.integerSetting("Limit the number of WebSocket messages sent at one time to",
        MontoyaWSUtils.DEFAULT_WS_REQUEST_LIMIT)

    val wsBumpWSConnection: Boolean by settingsManager.booleanSetting("Automatically Reconnect to Next WS Connection Matching Upgrade Request", false)

    val settingsPanel = settingsManager.buildSettingsPanel()
}