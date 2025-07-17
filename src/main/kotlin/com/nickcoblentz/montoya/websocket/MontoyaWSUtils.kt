package com.nickcoblentz.montoya.websocket

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.proxy.websocket.ProxyWebSocket
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreation
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreationHandler
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
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Toolkit
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JTextField


// Montoya API Documentation: https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html
// Montoya Extension Examples: https://github.com/PortSwigger/burp-extensions-montoya-api-examples

class YourBurpKotlinExtensionName : BurpExtension , ContextMenuItemsProvider, ProxyWebSocketCreationHandler {
    private val proxyWebSocketCreations = mutableListOf<ProxyWebSocketCreation>()
    private var webSocketMessages: MutableList<WebSocketMessage> = mutableListOf()


    private lateinit var api: MontoyaApi
    //private val projectSettings : MyProjectSettings by lazy { MyProjectSettings() }

    private val showUpgradeRequestMenuItem = JMenuItem("Show Upgrade Request")
    private val showIntruderIntegerMenu = JMenuItem("Intruder: Integers")

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


        //api.userInterface().registerSettingsPanel(projectSettings.settingsPanel)

        showUpgradeRequestMenuItem.addActionListener {
            webSocketMessages.forEach { message ->
                message.upgradeRequest()?.let { upgradeRequest ->
                    val requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY, EditorOptions.WRAP_LINES)
                    requestEditor.request=upgradeRequest
                    val burpFrame = BurpGuiFrame("HTTP Request Editor - " + upgradeRequest.url())

                    burpFrame.frame.contentPane.add(requestEditor.uiComponent(), BorderLayout.CENTER);
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
                }.toTypedArray())

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
                    val startInteger = startIntegerField.text.toInt()
                    val endInteger = endIntegerField.text.toInt()
                    val replaceString = replaceField.text

                    val selectedWebSocketConnection = webSocketConnectionsComboBox.selectedItem as String
                    selectedWebSocketConnection.substringBefore(" ").toIntOrNull()?.let { index ->
                        api.logging().logToOutput("Starting WS Intruder on connection $index")

                        api.logging().logToOutput("Replace Value Found?: ${message.payload().toString().contains(replaceString)}")

                        val proxyCreation = proxyWebSocketCreations[index]
                        for(i in startInteger..endInteger) {
                            Thread.ofVirtual().start {
                                val newMessage = message.payload().toString().replace(replaceString, i.toString())
                                api.logging()
                                    .logToOutput("Sending Message (${message.direction()}):\n$newMessage\n-----------------")
                                proxyCreation.proxyWebSocket().sendTextMessage(newMessage, message.direction())
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

                burpFrame.frame.contentPane.add(mainPanel, BorderLayout.CENTER);

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
            proxyWebSocketCreations.add(creation)
            api.logging().logToOutput("WebSocket Connection Created: ${creation.upgradeRequest().url()}")
        }
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