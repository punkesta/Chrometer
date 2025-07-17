package com.punksoft.chrometer

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.jvm.java
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.options.SchemeManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBSlider

class MainPanel : JBPanel<MainPanel>(BorderLayout()) {

    companion object {
        private val DEFAULT_IGNORE_ATTRIBUTES_CSV = """
            TEXT, 
            CARET_ROW_COLOR, 
            INDENT_GUIDE, 
            SELECTED_INDENT_GUIDE, 
            RIGHT_MARGIN_COLOR, 
            INLINE_REFACTORING_SETTINGS_DEFAULT, 
            INLINE_REFACTORING_SETTINGS_FOCUSED, 
            INLINE_REFACTORING_SETTINGS_HOVERED, 
            INLINE_PARAMETER_HINT, 
            INLAY_DEFAULT, 
            FOLDED_TEXT_ATTRIBUTES, 
            IDENTIFIER_UNDER_CARET_ATTRIBUTES, 
            ERRORS_ATTRIBUTES, 
            DEFAULT_INVALID_STRING_ESCAPE, 
            PROPERTIES.INVALID_STRING_ESCAPE, 
            WARNING_ATTRIBUTES, 
            RUNTIME_ERROR, 
            INFO_ATTRIBUTES, 
            DEFAULT_IDENTIFIER, 
            DEFAULT_OPERATION_SIGN, 
            DEFAULT_PARENTHS, 
            DEFAULT_REASSIGNED_LOCAL_VARIABLE, 
            DEFAULT_REASSIGNED_PARAMETER, 
            DEFAULT_SEMICOLON, 
            DEFAULT_TAG, 
            DEPRECATED_ATTRIBUTES, 
            UNMATCHED_BRACE_ATTRIBUTES, 
            WRONG_REFERENCES_ATTRIBUTES, 
            BAD_CHARACTER, 
            CONSOLE_ERROR_OUTPUT, 
            LOG_ERROR_OUTPUT, 
            MARKED_FOR_REMOVAL_ATTRIBUTES, 
            PROPERTIES.INVALID_STRING_ESCAPE, 
            UNMATCHED_BRACE_ATTRIBUTES, 
            FOLLOWED_HYPERLINK_ATTRIBUTES, 
            HYPERLINK_ATTRIBUTES, 
            KOTLIN_SMART_CAST_VALUE, 
            KOTLIN_SMART_CAST_RECEIVER, 
            KOTLIN_SMART_CONSTANT
        """.trimIndent().replace("\n",  "")

        private const val DEFAULT_IGNORE_COLORS_CSV = "21221E, A9B7C6, BCBEC4, C0CCDB"
    }

    private val colorsManager = EditorColorsManager.getInstance()

    private val stolenSchemeManager by lazy {
        val managerField = EditorColorsManagerImpl::class.java.getDeclaredField("schemeManager")
        managerField.isAccessible = true
        managerField.get(colorsManager) as SchemeManager<*>
    }

    private val hSlider = JBSlider(-100, 100, 0)
    private val sSlider = JBSlider(-100, 100, 0)
    private val bSlider = JBSlider(-100, 100, 0)
    private val ignoreAttributesTextArea = JTextArea(5, 35)
    private val ignoreColorsTextArea = JTextArea(5, 35)
    private val inverseIgnoreAttributesCheckBox = JBCheckBox("Inverse attr. selection")
    private val inverseIgnoreColorsCheckBox = JBCheckBox("Inverse color selection")
    private val applyButton = JButton("Save")
    private val resetButton = JButton("Reset")

    private var sourceScheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsSchemeImpl
    private var tempScheme: EditorColorsSchemeImpl? = null

    private fun Color.hexWithoutSharp() = String.format("%02x%02x%02x", this.red, this.green, this.blue)

    private fun JTextComponent.setEditEndListener(listener: (text: String) -> Unit) {
        if (this is JTextField) {
            this.addActionListener {
                listener(this.text)
            }
        }

        this.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val isCtrlOrCmd = e.isControlDown || e.isMetaDown
                if (e.keyCode == KeyEvent.VK_ENTER && isCtrlOrCmd) {
                    listener(this@setEditEndListener.text)
                }
            }
        })

        this.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) {
                listener(this@setEditEndListener.text)
            }
        })
    }

    init {
        val slidersPanel = JBPanel<JBPanel<*>>()
        slidersPanel.layout = BoxLayout(slidersPanel, BoxLayout.Y_AXIS)
        slidersPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        slidersPanel.add(createLabeledControl("Hue", hSlider))
        slidersPanel.add(createLabeledControl("Saturation", sSlider))
        slidersPanel.add(createLabeledControl("Brightness", bSlider))

        slidersPanel.add(Box.createVerticalStrut(15))

        ignoreAttributesTextArea.lineWrap = true
        ignoreAttributesTextArea.wrapStyleWord = true
        ignoreAttributesTextArea.text = DEFAULT_IGNORE_ATTRIBUTES_CSV

        val ignoreAttributesTextAreaContainer = JBScrollPane(ignoreAttributesTextArea)
        ignoreAttributesTextAreaContainer.border = BorderFactory.createTitledBorder(
            "Ignore Attributes (warnings, errors, etc.):"
        )

        ignoreColorsTextArea.lineWrap = true
        ignoreColorsTextArea.wrapStyleWord = true
        ignoreColorsTextArea.text = DEFAULT_IGNORE_ATTRIBUTES_CSV

        val ignoreColorsTextAreaContainer = JBScrollPane(ignoreColorsTextArea)
        ignoreColorsTextAreaContainer.border = BorderFactory.createTitledBorder("Ignore Colors:")

        slidersPanel.add(ignoreAttributesTextAreaContainer)
        slidersPanel.add(Box.createVerticalStrut(5))
        slidersPanel.add(ignoreColorsTextAreaContainer)

        val inverseIngoreCheckBoxContainer = JBPanel<JBPanel<*>>(BorderLayout(5, 0))
        inverseIngoreCheckBoxContainer.add(inverseIgnoreAttributesCheckBox, BorderLayout.WEST)
        inverseIngoreCheckBoxContainer.add(inverseIgnoreColorsCheckBox, BorderLayout.CENTER)

        slidersPanel.add(Box.createVerticalStrut(10))
        slidersPanel.add(inverseIngoreCheckBoxContainer)

        val bottomButtons = JBPanel<JBPanel<*>>(FlowLayout())

        bottomButtons.add(applyButton)
        bottomButtons.add(resetButton)

        val left = JBPanel<JBPanel<*>>(BorderLayout())
        left.add(slidersPanel, BorderLayout.NORTH)
        left.add(bottomButtons, BorderLayout.SOUTH)

        add(left, BorderLayout.WEST)

        // Call that before listener setup to avoid refresh
        resetControlsState()

        listOf(hSlider, sSlider, bSlider).forEach { slider ->
            slider.addChangeListener {
                if (!slider.valueIsAdjusting) {
                    setBottomButtonsEnabled(true)
                    refreshPreview()
                }
            }
        }

        ignoreAttributesTextArea.setEditEndListener {
            refreshPreview()
        }

        ignoreColorsTextArea.setEditEndListener {
            refreshPreview()
        }

        inverseIgnoreAttributesCheckBox.addChangeListener {
            refreshPreview()
        }

        inverseIgnoreColorsCheckBox.addChangeListener {
            refreshPreview()
        }

        applyButton.addActionListener {
            applyAndSave()
        }

        resetButton.addActionListener {
            reset()
        }
    }

    private fun createLabeledControl(text: String, control: JComponent): JBPanel<JBPanel<*>> {
        val panel = JBPanel<JBPanel<*>>(BorderLayout(5, 0))
        val label = JBLabel(text)
        label.preferredSize = Dimension(70, label.preferredSize.height)
        panel.add(label, BorderLayout.WEST)
        panel.add(control, BorderLayout.CENTER)
        return panel
    }

    private fun getHDelta(value: Int) = value.toFloat() / 100f

    private fun getSbDelta(value: Int) = value.toFloat() / 200f

    private fun refreshPreview() {
        checkSourceSchemeActuality()

        tempScheme = sourceScheme.clone() as EditorColorsSchemeImpl
        tempScheme!!.name = sourceScheme.name + " Temp"

        val tempScheme = this.tempScheme!!

        val h = getHDelta(hSlider.value)
        val s = getSbDelta(sSlider.value)
        val b = getSbDelta(bSlider.value)

        val ignoreAttributes = csvToSet(ignoreAttributesTextArea.text)
        // Remove sharps for better UX
        val ignoreColors = csvToSet(ignoreColorsTextArea.text) { elem ->
            elem.replace("#", "").lowercase()
        }

        val keys = sourceScheme.directlyDefinedAttributes.keys.map {
            key -> TextAttributesKey.createTextAttributesKey(key)
        }

        for (attrKey in keys/*TextAttributesKey.getAllKeys()*/) {
            val attr = tempScheme.getAttributes(attrKey)?.clone() ?: continue

            // Check if reliable to affect current color
            val isIgnoredAttr = ignoreAttributes.contains(attrKey.externalName)
            val isIgnoreAttrsInversed = inverseIgnoreAttributesCheckBox.isSelected

            if (isIgnoredAttr == isIgnoreAttrsInversed) {
                val isIgnoreColorsInversed = inverseIgnoreColorsCheckBox.isSelected

                val fgHex = attr.foregroundColor?.hexWithoutSharp()?.lowercase()

                if (attr.foregroundColor != null && ignoreColors.contains(fgHex) == isIgnoreColorsInversed) {
                    attr.foregroundColor = adjustHsb(attr.foregroundColor, h, s, b)
                }

                val bgHex = attr.backgroundColor?.hexWithoutSharp()?.lowercase()
                if (attr.backgroundColor != null && ignoreColors.contains(bgHex) == isIgnoreColorsInversed) {
                    attr.backgroundColor = adjustHsb(attr.backgroundColor, h, s, b)
                }
            }

            tempScheme.setAttributes(attrKey, attr)
        }

        colorsManager.addColorScheme(tempScheme)
        colorsManager.setGlobalScheme(tempScheme)
    }

    private fun csvToSet(csv: String, formatter: ((elem: String) -> String)? = null): Set<String> {
        val elems = mutableSetOf<String>()
        csv.split(",").forEach { elem ->
            val formattedElem = formatter?.invoke(elem) ?: elem
            elems.add(formattedElem.trim())
        }
        return elems
    }

    private fun checkSourceSchemeActuality() {
        val currName = colorsManager.globalScheme.name

        if (currName != tempScheme?.name && currName != sourceScheme.name) {
            println("User switched scheme to: $currName")
            sourceScheme = colorsManager.globalScheme.clone() as EditorColorsSchemeImpl
        }
    }

    private fun applyAndSave() {
        if (tempScheme == null) {
            return
        }

        // First off - clone the temp scheme, because ui listeners can change it
        val savableScheme = tempScheme!!.clone() as EditorColorsSchemeImpl

        val h = getHDelta(hSlider.value)
        val s = getSbDelta(sSlider.value)
        val b = getSbDelta(bSlider.value)
        savableScheme.name = sourceScheme.name + " - HSB($h, $s, $b)"

        resetControlsState()
        removeTempScheme()

        ApplicationManager.getApplication().runWriteAction {
            colorsManager.addColorScheme(savableScheme)
            colorsManager.setGlobalScheme(savableScheme)
        }
    }

    private fun reset() {
        resetControlsState()
        setupSourceScheme()
        removeTempScheme()
    }

    private fun resetControlsState() {
        hSlider.value = 0
        sSlider.value = 0
        bSlider.value = 0
        ignoreAttributesTextArea.text = DEFAULT_IGNORE_ATTRIBUTES_CSV
        ignoreColorsTextArea.text = DEFAULT_IGNORE_COLORS_CSV
        setBottomButtonsEnabled(false)
        inverseIgnoreAttributesCheckBox.isSelected = false
        inverseIgnoreColorsCheckBox.isSelected = false
    }

    private fun setBottomButtonsEnabled(isEnabled: Boolean) {
        applyButton.isEnabled = isEnabled
        resetButton.isEnabled = isEnabled
    }

    private fun setupSourceScheme() = EditorColorsManager.getInstance().setGlobalScheme(sourceScheme)

    private fun adjustHsb(color: Color, deltaH: Float, deltaS: Float, deltaB: Float): JBColor {
        val hsb = FloatArray(3)
        Color.RGBtoHSB(color.red, color.green, color.blue, hsb)
        val h = (hsb[0] + deltaH).let {
            when {
                it < 0f -> it + 1f
                it > 1f -> it - 1f
                else -> it
            }
        }
        val s = (hsb[1] + deltaS).coerceIn(0f, 1f)
        val b = (hsb[2] + deltaB).coerceIn(0f, 1f)
        val colorHsb = Color.HSBtoRGB(h, s, b)
        return JBColor(colorHsb, colorHsb)
    }

    private fun removeTempScheme() {
        ApplicationManager.getApplication().runWriteAction {
            stolenSchemeManager.removeScheme(tempScheme!!.name)
        }
    }
}