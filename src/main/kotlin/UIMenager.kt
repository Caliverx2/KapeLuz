package org.lewapnoob.KapeLuz

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.floor
import java.lang.management.ManagementFactory
import java.lang.management.BufferPoolMXBean

abstract class UIComponent {
    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0
    var isVisible: Boolean = true
    var isEnabled: Boolean = true
    var isFocused: Boolean = false
    var tooltipText: String? = null // Każdy komponent może mieć teraz tooltip
    var isTemporary: Boolean = false
    var initialX: Int = 0
    var initialY: Int = 0
    var initialW: Int = 0
    var initialH: Int = 0

    open val visualX: Int get() = x
    open val visualY: Int get() = y

    open fun saveInitialState() {
        initialX = x; initialY = y; initialW = width; initialH = height
    }
    open fun restoreInitialState() {
        x = initialX; y = initialY; width = initialW; height = initialH
    }

    abstract fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int)

    open fun onClick(x: Int, y: Int): Boolean = false
    open fun onHover(x: Int, y: Int) {}
    open fun onScroll(amount: Int) {}
    open fun onPress(x: Int, y: Int): Boolean = false
    open fun onRelease(x: Int, y: Int) {}
    open fun onDrag(x: Int, y: Int) {}
    open fun onKey(e: KeyEvent): Boolean = false

    // Metoda sprawdzania kolizji (otwarta do nadpisania dla UIText)
    open fun isMouseOver(mouseX: Int, mouseY: Int): Boolean {
        return isVisible && mouseX >= visualX && mouseX <= visualX + width && mouseY >= visualY && mouseY <= visualY + height
    }
}

enum class TextAlign {
    LEFT, CENTER, RIGHT
}

class TextEditorState(initialText: String, var onTextChanged: (() -> Unit)? = null) {
    var text: String = initialText
        set(value) {
            field = value
            if (cursorIndex > field.length) cursorIndex = field.length
            if (selectionStartIndex > field.length) selectionStartIndex = field.length
            onTextChanged?.invoke()
        }
    var cursorIndex: Int = initialText.length
    var selectionStartIndex: Int = initialText.length
    var isEditing: Boolean = false

    fun deleteSelection() {
        if (selectionStartIndex != cursorIndex) {
            val start = min(selectionStartIndex, cursorIndex)
            val end = max(selectionStartIndex, cursorIndex)
            text = text.removeRange(start, end)
            cursorIndex = start
            selectionStartIndex = cursorIndex
        }
    }

    fun handleKey(e: KeyEvent): Boolean {
        if (!isEditing) return false

        if (e.id == KeyEvent.KEY_PRESSED) {
            when (e.keyCode) {
                KeyEvent.VK_ENTER -> {
                    if (e.isShiftDown || e.isControlDown) {
                        deleteSelection()
                        text = text.substring(0, cursorIndex) + "\n" + text.substring(cursorIndex)
                        cursorIndex++
                        selectionStartIndex = cursorIndex
                    } else {
                        isEditing = false
                    }
                    return true
                }
                KeyEvent.VK_ESCAPE -> {
                    isEditing = false
                    return true
                }
                KeyEvent.VK_BACK_SPACE -> {
                    if (selectionStartIndex != cursorIndex) {
                        deleteSelection()
                    } else if (cursorIndex > 0) {
                        val oldCursor = cursorIndex
                        cursorIndex--
                        selectionStartIndex = cursorIndex
                        text = text.removeRange(oldCursor - 1, oldCursor)
                    }
                    return true
                }
                KeyEvent.VK_LEFT -> {
                    if (cursorIndex > 0) cursorIndex--
                    selectionStartIndex = cursorIndex
                    return true
                }
                KeyEvent.VK_RIGHT -> {
                    if (cursorIndex < text.length) cursorIndex++
                    selectionStartIndex = cursorIndex
                    return true
                }
            }
        } else if (e.id == KeyEvent.KEY_TYPED) {
            val char = e.keyChar
            if (char.code >= 32 && char.code != 127) {
                deleteSelection()
                text = text.substring(0, cursorIndex) + char + text.substring(cursorIndex)
                cursorIndex++
                selectionStartIndex = cursorIndex
                return true
            }
        }
        return false
    }

    fun reset(newText: String) {
        text = newText
        cursorIndex = text.length
        selectionStartIndex = text.length
        isEditing = false
    }
}

class UIButton(
    x: Int, y: Int, width: Int, height: Int,
    initialText: String = "Button",
    var textColor: Color = Color.WHITE,
    var texture: BufferedImage? = null,
    var textAlign: TextAlign = TextAlign.CENTER,
    var padding: Int = 0,
    var fontSize: Float = 40f,
    tooltip: String? = null,
    var icon: BufferedImage? = null,
    var action: () -> Unit
) : UIComponent() {
    val editor = TextEditorState(initialText)
    var text: String 
        get() = editor.text
        set(v) { editor.text = v }

    private var savedText: String = ""
    private var initialTextColor: Color = Color.WHITE

    init {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
        this.tooltipText = tooltip
    }

    override fun saveInitialState() {
        super.saveInitialState()
        savedText = text
        initialTextColor = textColor
    }

    override fun restoreInitialState() {
        super.restoreInitialState()
        editor.reset(savedText)
        textColor = initialTextColor
    }

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        val isHovered = isMouseOver(mouseX, mouseY) && isEnabled

        // Tło przycisku
        g.color = if (!isEnabled) Color(0x2c2c2c) else if (isHovered) Color(0x808080) else Color(0x6d6d6d)
        g.fillRect(x, y, width, height)

        texture?.let {
            g.drawImage(it, x, y, width, height, null)
        }

        // Podświetlenie tła podczas edycji tekstu
        if (editor.isEditing) {
            g.color = Color(255, 255, 255, 50)
            g.fillRect(x, y, width, height)
            g.color = Color.CYAN
            g.drawRect(x, y, width, height)
        }

        // Rysowanie ikony świata (40x40)
        icon?.let {
            g.drawImage(it, x, y, height, height, null) // height to 40
        }

        // --- LOGIKA CLIPPINGU ---
        val oldClip = g.clip
        g.clipRect(x, y, width, height)

        g.color = if (isEnabled) Color.WHITE else Color(0x808080)
        g.font = game.fpsFont.deriveFont(fontSize)

        val fm = g.fontMetrics
        val textWidth = fm.stringWidth(text)
        val textHeight = fm.ascent

        val iconOffset = if (icon != null) height + 5 else 0

        var drawX = when (textAlign) {
            TextAlign.LEFT   -> x + padding + iconOffset
            TextAlign.CENTER -> x + (width - textWidth) / 2
            TextAlign.RIGHT  -> x + width - textWidth - padding
        }

        val drawY = y + (height + textHeight) / 2 - 4

        // Rysowanie zaznaczenia tekstu wewnątrz przycisku
        if (editor.isEditing && editor.selectionStartIndex != editor.cursorIndex) {
            val start = min(editor.selectionStartIndex, editor.cursorIndex)
            val end = max(editor.selectionStartIndex, editor.cursorIndex)
            val selX = drawX + fm.stringWidth(editor.text.substring(0, start))
            val selW = fm.stringWidth(editor.text.substring(start, end))
            g.color = Color(0, 120, 215, 150)
            g.fillRect(selX, drawY - textHeight, selW, fm.height)
        }

        g.color = if (isEnabled) textColor else Color(0x808080)
        g.drawString(text, drawX, drawY)

        // Rysowanie kursora podczas edycji
        if (editor.isEditing && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val cursorPosX = drawX + fm.stringWidth(editor.text.substring(0, editor.cursorIndex))
            g.color = Color.WHITE
            g.fillRect(cursorPosX, drawY - textHeight, 2, fm.height)
        }

        g.clip = oldClip
    }

    override fun onClick(clickX: Int, clickY: Int): Boolean {
        if (isVisible && isEnabled && isMouseOver(clickX, clickY)) {
            action()
            return true
        }
        return false
    }

    override fun onKey(e: KeyEvent): Boolean = editor.handleKey(e)
}

class UIText(
    x: Int, y: Int,
    initialText: String,
    var fontSize: Float,
    var color: Color,
    var centered: Boolean = false,
    tooltip: String? = null,
    var textProvider: (() -> String)? = null
) : UIComponent() {
    // Flaga informująca, że wymiary wymagają przeliczenia (np. po zmianie tekstu)
    private var needsSizeUpdate = true

    val editor = TextEditorState(initialText) {
        needsSizeUpdate = true
    }

    var text: String 
        get() = editor.text
        set(v) { 
            editor.text = v
            needsSizeUpdate = true
        }

    private var savedText: String = ""
    private var initialColor: Color = Color.WHITE

    init {
        this.x = x
        this.y = y
        this.tooltipText = tooltip
        this.savedText = text
        this.initialColor = color
    }

    override fun saveInitialState() {
        super.saveInitialState()
        savedText = text
        initialColor = color
    }

    override fun restoreInitialState() {
        super.restoreInitialState()
        editor.reset(savedText)
        color = initialColor
    }

    override val visualX: Int get() = if (centered) x - width / 2 else x

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        // Pobieramy aktualny tekst z dostawcy danych (jeśli istnieje)
        textProvider?.invoke()?.let {
            if (it != text) text = it // Setter 'text' automatycznie ustawi needsSizeUpdate
        }

        g.font = game.fpsFont.deriveFont(fontSize)

        val fm = g.fontMetrics

        // Auto-sizing: aktualizujemy width i height na podstawie realnych wymiarów czcionki
        if (needsSizeUpdate) {
            val lines = text.split("\n")
            width = lines.maxOfOrNull { fm.stringWidth(it) } ?: 0
            height = lines.size * fm.height
            needsSizeUpdate = false
        }

        g.color = color

        val textWidth = fm.stringWidth(text)
        val textHeight = fm.ascent
        val drawX = if (centered) x - textWidth / 2 else x
        val drawY = y + textHeight

        // Rysowanie zaznaczenia
        if (editor.isEditing && editor.selectionStartIndex != editor.cursorIndex) {
            val start = min(editor.selectionStartIndex, editor.cursorIndex)
            val end = max(editor.selectionStartIndex, editor.cursorIndex)
            val selX = drawX + fm.stringWidth(editor.text.substring(0, start))
            val selW = fm.stringWidth(editor.text.substring(start, end))
            g.color = Color(0, 120, 215, 150)
            g.fillRect(selX, drawY - textHeight, selW, fm.height)
        }

        g.color = color
        g.drawString(editor.text, drawX, drawY)

        // Rysowanie kursora
        if (editor.isEditing && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val cursorPosX = drawX + fm.stringWidth(editor.text.substring(0, editor.cursorIndex))
            g.color = Color.WHITE
            g.fillRect(cursorPosX, drawY - textHeight, 2, fm.height)
        }
    }
    override fun onKey(e: KeyEvent): Boolean = editor.handleKey(e)
}

class UITextField(
    x: Int, y: Int, width: Int, height: Int,
    text: String = "",
    var placeholder: String = "",
    var textColor: Color = Color.WHITE,
    var fontSize: Float = 32f,
    var onTextChanged: ((String) -> Unit)? = null
) : UIComponent() {
    var text: String = text
        set(value) {
            field = value
            // Zabezpieczenie indeksów przy zmianie tekstu z zewnątrz
            if (cursorIndex > field.length) cursorIndex = field.length
            if (selectionStartIndex > field.length) selectionStartIndex = field.length
            onTextChanged?.invoke(field)
        }

    var cursorIndex = this.text.length
    var selectionStartIndex = this.text.length
    private val padding = 10
    private var lastFontMetrics: FontMetrics? = null
    private var targetScrollOffset = 0
    private var currentScrollOffset = 0.0

    init {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }

    private fun getSelectionBounds(): Pair<Int, Int> {
        val start = min(cursorIndex, selectionStartIndex)
        val end = max(cursorIndex, selectionStartIndex)
        return start to end
    }

    private fun deleteSelection() {
        val (start, end) = getSelectionBounds()
        if (start != end) {
            text = text.removeRange(start, end)
            cursorIndex = start
            selectionStartIndex = cursorIndex
        }
    }

    private fun getLineInfo(): Pair<List<String>, List<Int>> {
        val lines = text.split("\n")
        val lineStartIndices = mutableListOf<Int>()
        var currentIdx = 0
        for (line in lines) {
            lineStartIndices.add(currentIdx)
            currentIdx += line.length + 1
        }
        return lines to lineStartIndices
    }

    private fun getCursorLineAndOffset(index: Int, lines: List<String>, starts: List<Int>): Pair<Int, Int> {
        val line = starts.indexOfLast { index >= it }.coerceAtLeast(0)
        val offset = (index - starts[line]).coerceIn(0, lines[line].length)
        return line to offset
    }

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        // Rysowanie placeholdera (jeśli tekst pusty i brak focusa LUB jako etykieta nad polem)
        // W oryginalnym kodzie placeholder był rysowany nad polem (y-5), więc zachowujemy to.
        if (placeholder.isNotEmpty()) {
            g.color = Color.LIGHT_GRAY
            g.font = game.fpsFont.deriveFont(fontSize)
            g.drawString(placeholder, x, y - 5)
        }

        g.color = Color.BLACK
        g.fillRect(x, y, width, height)
        g.color = if (isFocused) Color.YELLOW else Color.WHITE
        g.drawRect(x, y, width, height)

        val oldClip = g.clip
        g.clipRect(x, y, width, height)

        g.font = game.fpsFont.deriveFont(fontSize)
        val fm = g.fontMetrics
        lastFontMetrics = fm

        // --- LOGIKA PRZEWIJANIA (SCROLL) ---
        val (lines, lineStartIndices) = getLineInfo()
        val (cursorLine, cursorOffsetInLine) = getCursorLineAndOffset(cursorIndex, lines, lineStartIndices)

        val textInCursorLine = lines[cursorLine]

        val visibleWidth = width - (padding * 2)
        val cursorPixelPos = fm.stringWidth(textInCursorLine.substring(0, cursorOffsetInLine))
        val currentLineWidth = fm.stringWidth(textInCursorLine)

        if (currentLineWidth > visibleWidth) {
            // Jeśli kursor wyjechał w prawo poza widok
            if (cursorPixelPos > targetScrollOffset + visibleWidth) {
                targetScrollOffset = cursorPixelPos - visibleWidth
            }
            // Jeśli kursor wyjechał w lewo poza widok
            if (cursorPixelPos < targetScrollOffset) {
                targetScrollOffset = cursorPixelPos
            }
        } else {
            targetScrollOffset = 0
        }

        targetScrollOffset = targetScrollOffset.coerceIn(0, max(0, currentLineWidth - visibleWidth))

        currentScrollOffset += (targetScrollOffset - currentScrollOffset) * 0.3
        if (abs(targetScrollOffset - currentScrollOffset) < 0.5) currentScrollOffset = targetScrollOffset.toDouble()
        val renderScrollOffset = currentScrollOffset.toInt()

        // --- RENDEROWANIE TEKSTU I ZAZNACZENIA ---
        val (selStart, selEnd) = getSelectionBounds()
        var currentLineY = y + fm.ascent + 5

        for (i in lines.indices) {
            val lineText = lines[i]
            val lineStart = lineStartIndices[i]
            val lineEnd = lineStart + lineText.length

            // Rysowanie zaznaczenia dla danej linii
            if (isFocused && selStart != selEnd) {
                val intersectStart = max(selStart, lineStart)
                val intersectEnd = min(selEnd, lineEnd)

                if (intersectStart < intersectEnd) {
                    val prefixInLine = lineText.substring(0, intersectStart - lineStart)
                    val selectionInLine = lineText.substring(intersectStart - lineStart, intersectEnd - lineStart)
                    
                    val selX = x + padding + fm.stringWidth(prefixInLine) - renderScrollOffset
                    val selW = fm.stringWidth(selectionInLine)
                    
                    g.color = Color(0, 0, 255, 128)
                    g.fillRect(selX, currentLineY - fm.ascent, selW, fm.height)
                }
            }

            g.color = textColor
            g.drawString(lineText, x + padding - renderScrollOffset, currentLineY)
            currentLineY += fm.height
        }

        // Rysowanie kursora (z uwzględnieniem linii)
        if (isFocused) {
            if ((System.currentTimeMillis() / 500) % 2 == 0L) {
                val cursorX = x + padding + cursorPixelPos - renderScrollOffset
                val cursorY = y + 5 + (cursorLine * fm.height)
                g.fillRect(cursorX, cursorY, 2, fm.height)
            }
        }
        g.clip = oldClip
    }

    override fun onClick(clickX: Int, clickY: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        isFocused = isMouseOver(clickX, clickY)
        
        if (isFocused && lastFontMetrics != null) {
            val fm = lastFontMetrics!!
            val (lines, starts) = getLineInfo()

            val localY = clickY - (y + 5)
            val clickedLine = (localY / fm.height).coerceIn(0, lines.size - 1)

            val localX = clickX - (x + padding) + currentScrollOffset.toInt()
            val lineText = lines[clickedLine]
            var charOffset = 0
            var minDiff = Int.MAX_VALUE
            for (i in 0..lineText.length) {
                val w = fm.stringWidth(lineText.substring(0, i))
                val diff = abs(w - localX)
                if (diff < minDiff) {
                    minDiff = diff
                    charOffset = i
                } else break
            }
            cursorIndex = starts[clickedLine] + charOffset
            selectionStartIndex = cursorIndex // Reset zaznaczenia przy kliknięciu
        }
        return isFocused
    }
    
    // Obsługa przeciągania myszką (zaznaczanie tekstu)
    override fun onDrag(dragX: Int, dragY: Int) {
        if (isFocused && lastFontMetrics != null) {
            val fm = lastFontMetrics!!
            val (lines, starts) = getLineInfo()

            val localY = dragY - (y + 5)
            val clickedLine = (localY / fm.height).coerceIn(0, lines.size - 1)

            val localX = dragX - (x + padding) + currentScrollOffset.toInt()
            val lineText = lines[clickedLine]
            var charOffset = 0
            var minDiff = Int.MAX_VALUE
            for (i in 0..lineText.length) {
                val w = fm.stringWidth(lineText.substring(0, i))
                val diff = abs(w - localX)
                if (diff < minDiff) {
                    minDiff = diff
                    charOffset = i
                } else break
            }
            cursorIndex = starts[clickedLine] + charOffset
            // Nie resetujemy selectionStartIndex podczas przeciągania
        }
    }

    override fun onKey(e: KeyEvent): Boolean {
        if (!isFocused) return false

        val isCtrl = e.isControlDown
        val isShift = e.isShiftDown

        if (e.id == KeyEvent.KEY_PRESSED) {
            when (e.keyCode) {
                KeyEvent.VK_ENTER -> {
                    if (isShift || isCtrl) {
                        deleteSelection()
                        text = text.substring(0, cursorIndex) + "\n" + text.substring(cursorIndex)
                        cursorIndex++
                        selectionStartIndex = cursorIndex
                        return true
                    }
                }
                KeyEvent.VK_LEFT -> {
                    if (cursorIndex > 0) cursorIndex--
                    if (!isShift) selectionStartIndex = cursorIndex
                    return true
                }
                KeyEvent.VK_UP -> {
                    val (lines, starts) = getLineInfo()
                    val (line, offset) = getCursorLineAndOffset(cursorIndex, lines, starts)
                    if (line > 0) {
                        val targetLine = line - 1
                        val newOffset = min(offset, lines[targetLine].length)
                        cursorIndex = starts[targetLine] + newOffset
                    } else cursorIndex = 0
                    if (!isShift) selectionStartIndex = cursorIndex
                    return true
                }
                KeyEvent.VK_DOWN -> {
                    val (lines, starts) = getLineInfo()
                    val (line, offset) = getCursorLineAndOffset(cursorIndex, lines, starts)
                    if (line < lines.size - 1) {
                        val targetLine = line + 1
                        val newOffset = min(offset, lines[targetLine].length)
                        cursorIndex = starts[targetLine] + newOffset
                    } else cursorIndex = text.length
                    if (!isShift) selectionStartIndex = cursorIndex
                    return true
                }
                KeyEvent.VK_RIGHT -> {
                    if (cursorIndex < text.length) cursorIndex++
                    if (!isShift) selectionStartIndex = cursorIndex
                    return true
                }
                KeyEvent.VK_HOME -> {
                    cursorIndex = 0
                    if (!isShift) selectionStartIndex = cursorIndex
                    return true
                }
                KeyEvent.VK_END -> {
                    cursorIndex = text.length
                    if (!isShift) selectionStartIndex = cursorIndex
                    return true
                }
                KeyEvent.VK_BACK_SPACE -> {
                    val (start, end) = getSelectionBounds()
                    if (start != end) {
                        deleteSelection()
                    } else if (cursorIndex > 0) {
                        cursorIndex--
                        text = text.removeRange(cursorIndex, cursorIndex + 1)
                        selectionStartIndex = cursorIndex
                    }
                    return true
                }
                KeyEvent.VK_DELETE -> {
                    val (start, end) = getSelectionBounds()
                    if (start != end) {
                        deleteSelection()
                    } else if (cursorIndex < text.length) {
                        text = text.removeRange(cursorIndex, cursorIndex + 1)
                        // kursor zostaje w miejscu
                        selectionStartIndex = cursorIndex
                    }
                    return true
                }
                KeyEvent.VK_A -> {
                    if (isCtrl) {
                        selectionStartIndex = 0
                        cursorIndex = text.length
                        return true
                    }
                }
                KeyEvent.VK_C -> {
                    if (isCtrl) {
                        val (start, end) = getSelectionBounds()
                        if (start != end) {
                            val selectedText = text.substring(start, end)
                            val selection = StringSelection(selectedText)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        }
                        return true
                    }
                }
                KeyEvent.VK_V -> {
                    if (isCtrl) {
                        try {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                                val pasteText = clipboard.getData(DataFlavor.stringFlavor) as String
                                deleteSelection()
                                // Limit długości tekstu (np. 30 znaków jak w oryginale)
                                val spaceLeft = 32 - text.length
                                val toInsert = if (pasteText.length > spaceLeft) pasteText.substring(0, spaceLeft) else pasteText
                                
                                if (toInsert.isNotEmpty()) {
                                    text = text.substring(0, cursorIndex) + toInsert + text.substring(cursorIndex)
                                    cursorIndex += toInsert.length
                                    selectionStartIndex = cursorIndex
                                }
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                        return true
                    }
                }
                KeyEvent.VK_X -> {
                    if (isCtrl) {
                        val (start, end) = getSelectionBounds()
                        if (start != end) {
                            val selectedText = text.substring(start, end)
                            val selection = StringSelection(selectedText)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                            deleteSelection()
                        }
                        return true
                    }
                }
            }
        } else if (e.id == KeyEvent.KEY_TYPED) {
            val char = e.keyChar
            if (!isCtrl && char.code >= 32 && char.code != 127) {
                deleteSelection()
                if (text.length < 32) {
                    text = text.substring(0, cursorIndex) + char + text.substring(cursorIndex)
                    cursorIndex++
                    selectionStartIndex = cursorIndex
                }
                return true
            }
        }
        return false
    }
}

class UIDropdown(
    val game: KapeLuz,
    x: Int, y: Int, width: Int, height: Int,
    var options: MutableList<String>,
    var selectedIndex: Int = 0,
    var onSelectionChanged: (Int) -> Unit
) : UIComponent() {
    var isExpanded = false
    private var scrollY = 0
    private val itemHeight = 35
    private val maxVisibleItems = 5
    private var isDraggingScrollbar = false
    private var dragStartY = 0
    private var initialScrollY = 0
    private var initialSelectedIndex = 0
    private var initialOptions = mutableListOf<String>()

    init {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
        this.initialSelectedIndex = selectedIndex
        this.initialOptions = options.toMutableList()
    }

    override fun saveInitialState() {
        super.saveInitialState()
        initialSelectedIndex = selectedIndex
        initialOptions = options.toMutableList() // Tworzymy kopię listy
    }

    override fun restoreInitialState() {
        super.restoreInitialState()
        selectedIndex = initialSelectedIndex
        options.clear()
        options.addAll(initialOptions) // Przywracamy zawartość z kopii
        isExpanded = false
    }

    private fun getDynamicMaxVisibleItems(): Int {
        val availableSpace = game.uiReferenceHeight - (y + height + 10)
        val itemsSpace = availableSpace / itemHeight
        return itemsSpace.coerceIn(0, maxVisibleItems)
    }

    private fun getListHeight(): Int {
        val maxItems = getDynamicMaxVisibleItems()
        if (maxItems <= 0) return 0
        return min(options.size, maxItems) * itemHeight
    }

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        // 1. Rysowanie głównego przycisku
        val isHovered = isMouseOver(mouseX, mouseY) && isEnabled
        g.color = if (isHovered) Color(0x808080) else Color(0x6d6d6d)
        g.fillRect(x, y, width, height)
        g.color = if (isExpanded) Color.YELLOW else Color.WHITE
        g.drawRect(x, y, width, height)

        g.font = game.fpsFont.deriveFont(24f)
        val fm = g.fontMetrics
        val text = if (options.isNotEmpty()) options[selectedIndex] else "Empty"
        g.color = Color.WHITE
        g.drawString(text, x + 10, y + (height + fm.ascent) / 2 - 2)

        // Strzałka w dół
        val arrowSize = 6
        val ax = x + width - 20
        val ay = y + height / 2
        if (isExpanded) {
            g.fillPolygon(intArrayOf(ax - arrowSize, ax + arrowSize, ax), intArrayOf(ay + arrowSize / 2, ay + arrowSize / 2, ay - arrowSize / 2), 3)
        } else {
            g.fillPolygon(intArrayOf(ax - arrowSize, ax + arrowSize, ax), intArrayOf(ay - arrowSize / 2, ay - arrowSize / 2, ay + arrowSize / 2), 3)
        }
    }

    fun renderList(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isExpanded) return

        val listHeight = getListHeight()
        if (listHeight <= 0) return
        val totalListHeight = options.size * itemHeight
        val lx = x
        val ly = y + height + 2

        // Tło listy
        g.color = Color(30, 30, 30, 240)
        g.fillRect(lx, ly, width, listHeight)
        g.color = Color.WHITE
        g.drawRect(lx, ly, width, listHeight)

        val oldClip = g.clip
        g.clipRect(lx, ly, width, listHeight)

        for (i in options.indices) {
            val itemY = ly + (i * itemHeight) - scrollY
            if (itemY + itemHeight < ly || itemY > ly + listHeight) continue

            val isItemHovered = mouseX >= lx && mouseX <= lx + width && mouseY >= itemY && mouseY <= itemY + itemHeight
            val isSelected = i == selectedIndex

            if (isItemHovered) {
                g.color = Color(255, 255, 255, 40)
                g.fillRect(lx, itemY, width, itemHeight)
            }

            g.color = if (isSelected) Color.WHITE else Color.GRAY
            g.font = game.fpsFont.deriveFont(if (isSelected) 20f else 18f)
            g.drawString(options[i], lx + 10, itemY + 24)
        }

        // Pasek przewijania jeśli potrzebny
        if (options.size * itemHeight > listHeight) {
            val scrollBarW = 6
            val viewRatio = listHeight.toDouble() / totalListHeight.toDouble()
            val handleH = (listHeight * viewRatio).toInt()
            val scrollRatio = scrollY.toDouble() / (totalListHeight - listHeight).toDouble()
            val handleY = ly + (scrollRatio * (listHeight - handleH)).toInt()
            
            g.color = Color(100, 100, 100)
            g.fillRect(lx + width - scrollBarW - 2, ly + 2, scrollBarW, listHeight - 4)
            g.color = Color.WHITE
            g.fillRect(lx + width - scrollBarW - 2, handleY, scrollBarW, handleH)
        }

        g.clip = oldClip
    }

    override fun onClick(clickX: Int, clickY: Int): Boolean {
        if (isExpanded) {
            val listHeight = getListHeight()
            val ly = y + height + 2
            // 1. Kliknięcie wewnątrz otwartej listy opcji
            if (clickX >= x && clickX <= x + width && clickY >= ly && clickY <= ly + listHeight) {
                val clickedIdx = ((clickY - ly + scrollY) / itemHeight).coerceIn(0, options.size - 1)
                selectedIndex = clickedIdx
                onSelectionChanged(selectedIndex)
                isExpanded = false
                return true
            }
            
            // 2. Kliknięcie w nagłówek (główny przycisk), gdy lista jest otwarta -> zamknij
            if (isMouseOver(clickX, clickY)) {
                isExpanded = false
                return true
            }

            // 3. Kliknięcie gdziekolwiek indziej, gdy lista jest otwarta -> zamknij i skonsumuj klik
            isExpanded = false
            return true
        }

        // 4. Kliknięcie w nagłówek, gdy lista jest zamknięta -> otwórz
        if (isMouseOver(clickX, clickY)) {
            val listHeight = getListHeight()
            if (listHeight > 0) {
                isExpanded = true
                // CENTROWANIE: Próbuj ustawić zaznaczony element na 3. miejscu (index 2)
                val totalHeight = options.size * itemHeight
                val maxScroll = max(0, totalHeight - listHeight)
                scrollY = ((selectedIndex - 2) * itemHeight).coerceIn(0, maxScroll)
                return true
            }
        }

        return false
    }

    override fun onScroll(amount: Int) {
        val listHeight = getListHeight()
        val totalHeight = options.size * itemHeight
        
        if (isExpanded && totalHeight > listHeight) {
            scrollY = (scrollY + amount * 20).coerceIn(0, totalHeight - listHeight)
        }
    }

    override fun onDrag(dragX: Int, dragY: Int) {
        if (isDraggingScrollbar) {
            val listHeight = getListHeight()
            val totalHeight = options.size * itemHeight
            val deltaY = dragY - dragStartY

            val viewRatio = listHeight.toDouble() / totalHeight.toDouble()
            val handleH = (listHeight * viewRatio).coerceAtLeast(10.0)
            val trackHeight = listHeight - handleH

            if (trackHeight > 0) {
                val scrollPerPixel = (totalHeight - listHeight).toDouble() / trackHeight
                scrollY = (initialScrollY + deltaY * scrollPerPixel).toInt().coerceIn(0, totalHeight - listHeight)
            }
        }
    }

    override fun onRelease(x: Int, y: Int) {
        isDraggingScrollbar = false
    }

    override fun onPress(x: Int, y: Int): Boolean {
        if (isExpanded) {
            val listHeight = getListHeight()
            val totalHeight = options.size * itemHeight
            if (totalHeight > listHeight) {
                val scrollBarW = 6
                val lx = this.x
                val ly = this.y + height + 2
                val viewRatio = listHeight.toDouble() / totalHeight.toDouble()
                val handleH = (listHeight * viewRatio).toInt()
                val scrollRatio = scrollY.toDouble() / (totalHeight - listHeight).toDouble()
                val handleY = ly + (scrollRatio * (listHeight - handleH)).toInt()

                if (x >= lx + width - scrollBarW - 2 && x <= lx + width - 2 &&
                    y >= handleY && y <= handleY + handleH) {
                    isDraggingScrollbar = true
                    dragStartY = y
                    initialScrollY = scrollY
                    return true
                }
            }
            // Jeśli lista jest rozwinięta, przechwytujemy kliknięcie, by nie "przestrzeliło" pod spód
            val ly = y + height + 2
            if (x >= this.x && x <= this.x + width && y >= ly && y <= ly + getListHeight()) return true
        }
        return isMouseOver(x, y)
    }
}

class UICheckbox(
    x: Int, y: Int, width: Int, height: Int,
    initialText: String = "Checkbox",
    var checked: Boolean = false,
    var fontSize: Float = 24f,
    var onToggle: (Boolean) -> Unit = {}
) : UIComponent() {
    val editor = TextEditorState(initialText)
    var text: String
        get() = editor.text
        set(v) { editor.text = v }

    private var savedText: String = ""
    private var initialFontSize: Float = 24f
    private var initialChecked: Boolean = false

    init {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
        this.savedText = text
        this.initialChecked = checked
        this.initialFontSize = fontSize
    }

    override fun saveInitialState() {
        super.saveInitialState()
        savedText = text
        initialChecked = checked
        initialFontSize = fontSize
    }

    override fun restoreInitialState() {
        super.restoreInitialState()
        editor.reset(savedText)
        checked = initialChecked
        fontSize = initialFontSize
    }

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return
        val isHovered = isMouseOver(mouseX, mouseY) && isEnabled

        // Tło jak w przycisku
        g.color = if (!isEnabled) Color(0x2c2c2c) else if (isHovered) Color(0x808080) else Color(0x6d6d6d)
        g.fillRect(x, y, width, height)

        // Etykieta tekstowa
        g.font = game.fpsFont.deriveFont(fontSize)
        val fm = g.fontMetrics
        val textHeight = fm.ascent
        val drawX = x + 10
        val drawY = y + (height + textHeight) / 2 - 2

        // Rysowanie zaznaczenia tekstu
        if (editor.isEditing && editor.selectionStartIndex != editor.cursorIndex) {
            val start = min(editor.selectionStartIndex, editor.cursorIndex)
            val end = max(editor.selectionStartIndex, editor.cursorIndex)
            val selX = drawX + fm.stringWidth(editor.text.substring(0, start))
            val selW = fm.stringWidth(editor.text.substring(start, end))
            g.color = Color(0, 120, 215, 150)
            g.fillRect(selX, drawY - textHeight, selW, fm.height)
        }

        g.color = Color.WHITE
        g.drawString(text, drawX, drawY)

        // Rysowanie kursora podczas edycji
        if (editor.isEditing && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val cursorPosX = drawX + fm.stringWidth(editor.text.substring(0, editor.cursorIndex))
            g.color = Color.WHITE
            g.fillRect(cursorPosX, drawY - textHeight, 2, fm.height)
        }

        // Wizualizacja suwaka (Toggle Switch) po prawej
        val swWidth = 45
        val swHeight = 18
        val swX = x + width - swWidth - 15
        val swY = y + (height - swHeight) / 2

        // 1. Podstawa suwaka (Ciemnoszary / Zielony)
        g.color = if (checked) Color(0x2ecc71) else Color(0x333333)
        g.fillRect(swX, swY, swWidth, swHeight)
        g.color = Color.BLACK
        g.drawRect(swX, swY, swWidth, swHeight)

        // 2. Sam suwak (Jasnoszary, wyższy i cieńszy)
        val slWidth = 10
        val slHeight = swHeight + 8
        val slX = if (checked) swX + swWidth - slWidth else swX
        val slY = swY - (slHeight - swHeight) / 2

        g.color = Color(0xcccccc)
        g.fillRect(slX, slY, slWidth, slHeight)
        g.color = Color.WHITE
        g.drawRect(slX, slY, slWidth, slHeight)
    }

    override fun onClick(clickX: Int, clickY: Int): Boolean {
        if (isVisible && isEnabled && isMouseOver(clickX, clickY)) {
            checked = !checked
            onToggle(checked)
            return true
        }
        return false
    }

    override fun onKey(e: KeyEvent): Boolean = editor.handleKey(e)
}

enum class ProgressBarMode {
    NONE, STEP_10, STEP_25
}

class UIProgressBar(
    x: Int, y: Int, width: Int, height: Int,
    var progress: Float = 0.5f, // 0.0 to 1.0
    initialText: String = "",
    var fillColor: Color = Color(0x2ecc71), // Zielony
    var mode: ProgressBarMode = ProgressBarMode.NONE,
    var fontSize: Float = 20f,
    var progressProvider: (() -> Float)? = null,
    var textProvider: (() -> String)? = null
) : UIComponent() {
    val editor = TextEditorState(initialText)
    var text: String
        get() = editor.text
        set(v) { editor.text = v }

    private var savedText = ""
    private var initialProgress = 0.5f
    private var initialFillColor = Color.GREEN
    private var initialMode = ProgressBarMode.NONE
    private var initialFontSize = 20f

    init {
        this.x = x; this.y = y; this.width = width; this.height = height
        saveInitialState()
    }

    override fun saveInitialState() {
        super.saveInitialState()
        initialProgress = progress
        savedText = text
        initialFillColor = fillColor
        initialMode = mode
        initialFontSize = fontSize
    }

    override fun restoreInitialState() {
        super.restoreInitialState()
        progress = initialProgress
        editor.reset(savedText)
        fillColor = initialFillColor
        mode = initialMode
        fontSize = initialFontSize
    }

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        // Pobieranie danych z gry, jeśli dostawcy są ustawieni
        val currentProgress = progressProvider?.invoke() ?: progress
        val displayText = textProvider?.invoke() ?: text

        // Tło paska (Ciemnoszary)
        g.color = Color(45, 45, 45)
        g.fillRect(x, y, width, height)
        
        // Wypełnienie
        val fillW = (width * currentProgress.coerceIn(0f, 1f)).toInt()
        g.color = fillColor
        g.fillRect(x, y, fillW, height)

        // Przedziałki
        g.color = Color(100, 100, 100, 150)
        g.stroke = BasicStroke(1f)
        when (mode) {
            ProgressBarMode.STEP_10 -> {
                for (i in 1..9) {
                    val lx = x + (width * i / 10)
                    g.drawLine(lx, y, lx, y + height)
                }
            }
            ProgressBarMode.STEP_25 -> {
                for (i in 1..3) {
                    val lx = x + (width * i / 4)
                    g.drawLine(lx, y, lx, y + height)
                }
            }
            else -> {}
        }

        // Obramowanie
        g.color = Color.WHITE
        g.drawRect(x, y, width, height)

        // Napis na środku
        if (displayText.isNotEmpty() || editor.isEditing) {
            g.font = game.fpsFont.deriveFont(fontSize)
            val fm = g.fontMetrics
            val tw = fm.stringWidth(displayText)
            val tx = x + (width - tw) / 2
            val ty = y + (height + fm.ascent) / 2 - 2
            
            g.color = Color.WHITE
            g.drawString(displayText, tx, ty)

            if (editor.isEditing && (System.currentTimeMillis() / 500) % 2 == 0L) {
                val cursorX = tx + fm.stringWidth(displayText.substring(0, editor.cursorIndex))
                g.fillRect(cursorX, ty - fm.ascent, 2, fm.height)
            }
        }
    }
    override fun onKey(e: KeyEvent): Boolean = editor.handleKey(e)
}

class UISlider(
    x: Int, y: Int, width: Int, height: Int,
    var minVal: Float = 0f,
    var maxVal: Float = 100f,
    var currentVal: Float = 50f,
    initialText: String = "Slider",
    var fontSize: Float = 20f,
    var onValueChanged: (Float) -> Unit = {}
) : UIComponent() {
    val editor = TextEditorState(initialText)
    var text: String
        get() = editor.text
        set(v) { editor.text = v }

    private var savedText = ""
    private var initialVal = 0f
    private var initialFontSize = 20f

    init {
        this.x = x; this.y = y; this.width = width; this.height = height
        saveInitialState()
    }

    override fun saveInitialState() {
        super.saveInitialState()
        savedText = text
        initialVal = currentVal
        initialFontSize = fontSize
    }

    override fun restoreInitialState() {
        super.restoreInitialState()
        editor.reset(savedText)
        currentVal = initialVal
        fontSize = initialFontSize
    }

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        // 1. Tło (Ciemnoszary)
        g.color = Color(60, 60, 60)
        g.fillRect(x, y, width, height)

        // 2. Suwak (Jasnoszary, wąski a wysoki)
        val progress = ((currentVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
        val handleW = 10
        val handleX = x + (progress * (width - handleW)).toInt()

        g.color = Color(200, 200, 200)
        g.fillRect(handleX, y, handleW, height)
        g.color = Color.WHITE
        g.drawRect(handleX, y, handleW, height)

        // 3. Obramowanie całego obiektu
        g.color = Color.WHITE
        g.drawRect(x, y, width, height)

        // 4. Napis na środku
        g.font = game.fpsFont.deriveFont(fontSize)
        val fm = g.fontMetrics
        val displayText = if (editor.isEditing) text else "$text: ${currentVal.toInt()}"
        val tw = fm.stringWidth(displayText)
        val tx = x + (width - tw) / 2
        val ty = y + (height + fm.ascent) / 2 - 2

        g.color = Color.WHITE
        g.drawString(displayText, tx, ty)

        if (editor.isEditing && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val cursorX = tx + fm.stringWidth(text.substring(0, editor.cursorIndex))
            g.fillRect(cursorX, ty - fm.ascent, 2, fm.height)
        }
    }

    private fun updateValueFromMouse(mouseX: Int) {
        val progress = ((mouseX - x).toFloat() / width.toFloat()).coerceIn(0f, 1f)
        currentVal = minVal + progress * (maxVal - minVal)
        onValueChanged(currentVal)
    }

    override fun onClick(clickX: Int, clickY: Int): Boolean {
        if (isMouseOver(clickX, clickY) && isEnabled) {
            updateValueFromMouse(clickX)
            return true
        }
        return false
    }

    override fun onPress(x: Int, y: Int): Boolean {
        if (isMouseOver(x, y) && isEnabled) {
            updateValueFromMouse(x)
            return true
        }
        return false
    }

    override fun onDrag(x: Int, y: Int) {
        if (isEnabled) updateValueFromMouse(x)
    }

    override fun onKey(e: KeyEvent): Boolean = editor.handleKey(e)
}

class UIScrollPanel(
    x: Int, y: Int, width: Int, height: Int
) : UIComponent() {
    val children = CopyOnWriteArrayList<UIComponent>()
    var scrollY = 0
    private var contentHeight = 0
    private val scrollBarWidth = 15

    private var isDraggingScrollbar = false
    private var dragStartY = 0
    private var initialScrollY = 0

    init {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }

    fun addChild(component: UIComponent) {
        children.add(component)
        contentHeight = kotlin.math.max(contentHeight, component.y + component.height)
    }

    fun clear() {
        children.clear()
        contentHeight = 0
        scrollY = 0
    }

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        g.color = Color(0, 0, 0, 100)

        val originalClip = g.clip
        g.clipRect(x, y, width - scrollBarWidth, height)

        val gContent = g.create() as Graphics2D
        gContent.translate(x, y - scrollY)

        val relativeMouseX = mouseX - x
        val relativeMouseY = mouseY - (y - scrollY)

        for (child in children) {
            if (child.y + child.height > scrollY && child.y < scrollY + height) {
                child.render(gContent, game, relativeMouseX, relativeMouseY)
            }
        }
        gContent.dispose()
        g.clip = originalClip

        if (contentHeight > height) {
            g.color = Color(50, 50, 50)
            g.fillRect(x + width - scrollBarWidth, y, scrollBarWidth, height)

            val viewRatio = height.toDouble() / contentHeight.toDouble()
            val handleHeight = (height * viewRatio).coerceAtLeast(20.0).toInt()
            val maxScroll = contentHeight - height
            val scrollRatio = scrollY.toDouble() / maxScroll.toDouble()
            val handleY = y + (scrollRatio * (height - handleHeight)).toInt()

            g.color = if (isDraggingScrollbar) Color.WHITE else Color.LIGHT_GRAY
            g.fillRect(x + width - scrollBarWidth + 2, handleY, scrollBarWidth - 4, handleHeight)
        }
    }

    override fun onClick(clickX: Int, clickY: Int): Boolean {
        if (!isVisible || !isMouseOver(clickX, clickY)) return false

        if (clickX > x + width - scrollBarWidth && contentHeight > height) {
            return true
        }

        val relativeX = clickX - x
        val relativeY = clickY - y + scrollY

        var handled = false
        for (child in children) {
            // Iterujemy po wszystkich, aby zaktualizować focus (np. odznaczyć inne pola)
            if (child.onClick(relativeX, relativeY)) handled = true
        }
        return handled
    }

    override fun onScroll(amount: Int) {
        if (contentHeight <= height) return
        val scrollSpeed = 20
        scrollY = (scrollY + amount * scrollSpeed).coerceIn(0, contentHeight - height)
    }

    override fun onPress(clickX: Int, clickY: Int): Boolean {
        if (!isVisible) return false
        if (clickX >= x + width - scrollBarWidth && clickX <= x + width &&
            clickY >= y && clickY <= y + height && contentHeight > height) {

            isDraggingScrollbar = true
            dragStartY = clickY
            initialScrollY = scrollY
            return true
        }
        return false
    }

    override fun onRelease(x: Int, y: Int) {
        isDraggingScrollbar = false
    }

    override fun onDrag(dragX: Int, dragY: Int) {
        if (isDraggingScrollbar && contentHeight > height) {
            val deltaY = dragY - dragStartY
            val viewRatio = height.toDouble() / contentHeight.toDouble()
            val handleHeight = (height * viewRatio).coerceAtLeast(20.0)
            val trackHeight = height - handleHeight
            val scrollPerPixel = (contentHeight - height) / trackHeight
            scrollY = (initialScrollY + deltaY * scrollPerPixel).toInt().coerceIn(0, contentHeight - height)
        }
    }

    override fun onHover(hoverX: Int, hoverY: Int) {
        if (!isVisible || !isMouseOver(hoverX, hoverY)) return
        val relativeX = hoverX - x
        val relativeY = hoverY - y + scrollY
        for (child in children) {
            child.onHover(relativeX, relativeY)
        }
    }

    override fun onKey(e: KeyEvent): Boolean {
        if (!isVisible) return false
        return children.any { it.onKey(e) }
    }
}

class UIBackground(var color: Color) : UIComponent() {
    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        g.color = color
        g.fillRect(0, 0, game.referenceWidth, game.referenceHeight)
    }
}

class UIFpsCounter : UIComponent() {
    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        val font = game.fpsFont.deriveFont(30f)
        g.font = font
        g.color = Color.YELLOW
        val fpsText = "${game.fps}"
        val fm = g.fontMetrics
        g.drawString(fpsText, game.uiReferenceWidth - fm.stringWidth(fpsText) - 10, fm.ascent + 10)
    }
}

class UIPlayerList : UIComponent() {
    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!game.showPlayerList) return

        val fm = g.fontMetrics

        var myId = ""
        if (game.myPlayerId == "") myId = "host" else myId = game.myPlayerId

        val PlayerCount = "Players (${game.remotePlayers.size+1}):"
        val SessionID = ":  : ${myId} (${floor((game.camX + (game.cubeSize/2))/2).toInt()}, ${floor((game.camY + (game.cubeSize/2))/2).toInt()+5}, ${floor((game.camZ + (game.cubeSize/2)) /2).toInt()})"
        // Obliczenia dla wyśrodkowania listy (-200 do +200 od środka)
        val centerX = game.uiReferenceWidth / 2
        val listWidth = 400
        val startX = centerX - 200
        val textX = startX + 10 // Padding 10 od lewej krawędzi listy

        val oldClip = g.clip
        g.clipRect(startX, 0, listWidth, game.referenceHeight)

        g.color = Color(0.82f, 0.82f, 0.82f, 0.25f)
        g.fillRect(startX, 15, listWidth, fm.ascent)
        g.fillRect(startX, fm.ascent + 15, listWidth, fm.ascent)
        
        g.color = Color.WHITE
        g.drawString(PlayerCount, textX, fm.ascent + 10)
        g.drawString(SessionID, textX, fm.ascent*2 + 10)

        var i = 0
        game.remotePlayers.forEach { (netId, player) ->
            val playerText = ":  : $netId (${(floor((player.x + (game.cubeSize/2))/2)).toSmartString()}, ${(floor((player.y + (game.cubeSize/2))/2)+5).toSmartString()}, ${(floor((player.z + (game.cubeSize/2))/2)).toSmartString()})"

            g.color = Color(0.82f, 0.82f, 0.82f, 0.25f)
            g.fillRect(startX, fm.ascent * (2 + i) + 15, listWidth, fm.ascent)

            g.color = Color.WHITE
            g.drawString(playerText, textX, fm.ascent * (3 + i) + 10)
            i++
        }

        g.clip = oldClip
    }
}

class UIDebugInfo : UIComponent() {
    private var smoothedUsedMem: Long = 0

    // Zmienne do przechowywania wartości wyświetlanych, aktualizowanych co 0.5s
    private var lastMemUpdateTime: Long = 0
    private var displayUsedMem: Long = 0
    private var displayTotalMem: Long = 0
    private var displayDirectMem: Long = 0
    private var displayMetaMem: Long = 0

    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        if (!game.showChunkBorders) return
        
        g.font = game.fpsFont.deriveFont(27f) // Stały rozmiar dla debuga
        val fm = g.fontMetrics
        val currentChunkX = floor(game.camX / 32.0).toInt()
        val currentChunkZ = floor(game.camZ / 32.0).toInt()
        val chunkText = "Chunk: c_${currentChunkX}_${currentChunkZ}.dat"
        val posText = "Position: (${floor((game.camX + (game.cubeSize/2))/2).toInt()}, ${floor((game.camY + (game.cubeSize/2))/2).toInt()+5}, ${floor((game.camZ + (game.cubeSize/2)) /2).toInt()}) [${game.localDimension}]"
        val timeText = String.format("Time: %.2fzł (Intensity: %.2f) (Day ${game.dayCounter})", game.gameTime, game.globalSunIntensity)
        val seedText = "Seed: ${game.seed}"

        // --- Ciągłe wygładzanie wartości w tle ---
        val currentUsedMem = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / (1024 * 1024)
        if (smoothedUsedMem == 0L) smoothedUsedMem = currentUsedMem
        else smoothedUsedMem += ((currentUsedMem - smoothedUsedMem) * 0.05).toLong()

        // --- Aktualizacja wyświetlanych wartości 2 razy na sekundę ---
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMemUpdateTime > 500) { // 500ms
            lastMemUpdateTime = currentTime
            val mb = 1024 * 1024

            displayUsedMem = smoothedUsedMem
            displayTotalMem = ManagementFactory.getMemoryMXBean().heapMemoryUsage.committed / mb
            displayMetaMem = ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage.used / mb

            val bufferPools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java)
            displayDirectMem = (bufferPools.find { it.name == "direct" }?.memoryUsed ?: 0) / mb
        }
        
        // Wyświetlamy: Heap (Obiekty) + Direct (Grafika/Bufory) + NonHeap (JVM)
        val memText = "Heap: ${displayUsedMem / 2}/${displayTotalMem}MB | Direct: ${displayDirectMem}MB | Meta: ${displayMetaMem}MB"

        g.color = Color(0.82f, 0.82f, 0.82f, 0.75f)
        g.fillRect(5, 15, fm.stringWidth(chunkText) + 10, fm.ascent)
        g.fillRect(5, fm.ascent + 15, fm.stringWidth(posText) + 10, fm.ascent)
        g.fillRect(5, fm.ascent*2 + 15, fm.stringWidth(timeText) + 10, fm.ascent)
        g.fillRect(5, fm.ascent*3 + 15, fm.stringWidth(seedText) + 10, fm.ascent)
        g.fillRect(5, fm.ascent*4 + 15, fm.stringWidth(memText) + 10, fm.ascent)

        g.color = Color.WHITE
        g.drawString(chunkText, 10, fm.ascent + 10)
        g.drawString(posText, 10, fm.ascent*2 + 10)
        g.drawString(timeText, 10, fm.ascent*3 + 10)
        g.drawString(seedText, 10, fm.ascent*4 + 10)
        g.drawString(memText, 10, fm.ascent*5 + 10)

        // Informacje o bloku, na który patrzy gracz
        val hit = game.getTargetBlock()
        if (hit != null) {
            val target = hit.blockPos
            val blockColor = game.getBlock(target.x, target.y, target.z)

            // Obliczamy pozycję sąsiada w zależności od ściany, na którą patrzymy
            var nx = target.x; var ny = target.y; var nz = target.z
            when(hit.faceIndex) {
                0 -> nz-- // Front (Z-) -> sąsiad Z-1
                1 -> nz++ // Back (Z+) -> sąsiad Z+1
                2 -> nx-- // Left (X-) -> sąsiad X-1
                3 -> nx++ // Right (X+) -> sąsiad X+1
                4 -> ny++ // Top (Y+) -> sąsiad Y+1
                5 -> ny-- // Bottom (Y-) -> sąsiad Y-1
            }
            val rawLight = game.getLight(nx, ny, nz)
            val skyLight = (rawLight shr 4) and 0xF
            val blockLight = rawLight and 0xF
            val effectiveLight = maxOf(skyLight * game.globalSunIntensity, blockLight.toDouble()).toInt()

            val colorHex = if (blockColor != null) String.format("#%06X", (0xFFFFFF and blockColor.rgb)) else "N/A"
            val targetText = "Target: [${target.x}, ${target.y}, ${target.z}] Color: $colorHex Face: ${hit.faceIndex} Light: $effectiveLight"

            g.color = Color(0.82f, 0.82f, 0.82f, 0.75f)
            g.fillRect(5, fm.ascent*5 + 15, fm.stringWidth(targetText) + 5, fm.ascent)

            g.color = Color.WHITE
            g.drawString(targetText, 10, fm.ascent*6 + 10)
        }
    }
}

class UICrosshair : UIComponent() {
    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        g.color = Color.WHITE
        val crossSize = 5
        val centerX = game.referenceWidth / 2
        val centerY = game.referenceHeight / 2
        g.stroke = BasicStroke(2f)
        g.drawLine(centerX - crossSize, centerY, centerX + crossSize, centerY)
        g.drawLine(centerX, centerY - crossSize, centerX, centerY + crossSize)
    }
}

class UIInventory : UIComponent() {
    override fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        val slotSize = 50
        val padding = 5
        val totalWidth = 9 * (slotSize + padding) - padding
        val startX = (game.referenceWidth - totalWidth) / 2
        val startY = game.referenceHeight - slotSize - 20

        for (i in 0 until 9) {
            val x = startX + i * (slotSize + padding)
            val y = startY

            if (i == game.selectedSlot) {
                g.color = Color(255, 255, 255, 180)
                g.stroke = BasicStroke(3f)
            } else {
                g.color = Color(0, 0, 0, 150)
                g.stroke = BasicStroke(1f)
            }

            g.fillRect(x, y, slotSize, slotSize)
            g.color = if (i == game.selectedSlot) Color.YELLOW else Color.GRAY
            g.drawRect(x, y, slotSize, slotSize)

            val stack = game.inventory[i]
            if (stack != null) {
                val color = game.blockIdColors[stack.color] ?: stack.color
                drawIsometricBlock(g, x + slotSize / 2, y + slotSize / 2 + 5, slotSize - 20, Color(color))

                g.color = Color.WHITE
                g.font = game.hotbarFont
                val countStr = stack.count.toString()
                val strW = g.fontMetrics.stringWidth(countStr)
                g.drawString(countStr, x + slotSize - strW - 3, y + slotSize - 3)
            }
        }
    }

    private fun drawIsometricBlock(g2d: Graphics2D, cx: Int, cy: Int, size: Int, color: Color) {
        val scale = size * 0.4

        val p = arrayOf(
            Vector3d(-1.0, 1.0, -1.0), Vector3d(1.0, 1.0, -1.0),
            Vector3d(1.0, 1.0, 1.0), Vector3d(-1.0, 1.0, 1.0),
            Vector3d(-1.0, -1.0, -1.0), Vector3d(1.0, -1.0, -1.0),
            Vector3d(1.0, -1.0, 1.0), Vector3d(-1.0, -1.0, 1.0)
        )

        val yaw = Math.toRadians(45.0)
        val pitch = Math.asin(1.0 / Math.sqrt(3.0))

        val cosYaw = cos(yaw); val sinYaw = sin(yaw)
        val cosPitch = cos(pitch); val sinPitch = sin(pitch)

        fun project(v: Vector3d): Point {
            var x = v.x * cosYaw - v.z * sinYaw
            var y = v.y
            var z = v.z * cosYaw + v.x * sinYaw
            val y2 = y * cosPitch - z * sinPitch
            return Point((cx + x * scale).toInt(), (cy - y2 * scale).toInt())
        }

        val pts = p.map { project(it) }

        // Top (Y+)
        g2d.color = color.brighter()
        g2d.fillPolygon(intArrayOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x), intArrayOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y), 4)

        // Right (X+)
        g2d.color = color.darker()
        g2d.fillPolygon(intArrayOf(pts[1].x, pts[5].x, pts[6].x, pts[2].x), intArrayOf(pts[1].y, pts[5].y, pts[6].y, pts[2].y), 4)

        // Left (Z+)
        g2d.color = color
        g2d.fillPolygon(intArrayOf(pts[3].x, pts[2].x, pts[6].x, pts[7].x), intArrayOf(pts[3].y, pts[2].y, pts[6].y, pts[7].y), 4)
    }
}

class UIManager(val game: KapeLuz) {
    val panels = mutableMapOf<GameState, UIPanel>()
    var isEditorMode = false
    var isEditorButtonVisible = false
    private var lastClickTime = 0L
    private var lastClickedComponent: UIComponent? = null

    private val editorToggleButton: UIButton = UIButton(15, 15, 50, 50, "ED", tooltip = "UI Editor") {
        toggleEditor()
    }

    private var selectedComponent: UIComponent? = null
    private var isDragging = false
    private var isResizingLeft = false
    private var isResizingRight = false
    private var isResizingTop = false
    private var isResizingBottom = false
    private var dragStartX = 0
    private var dragStartY = 0
    private var initialCompX = 0
    private var initialCompY = 0
    private var initialCompW = 0
    private var initialCompH = 0

    private var dropdownJustOpened = false
    var activeDropdown: UIDropdown? = null
    private var contextMenuVisible = false
    private var contextMenuX = 0
    private var contextMenuY = 0
    private var spawnX = 0
    private var spawnY = 0


    private val editorToolbar = mutableListOf<UIComponent>()
    private val contextMenuButtons = mutableListOf<UIButton>()
    private var exportField: UITextField? = null
    private var exportWindowVisible = false
    private val inspectorFontSize = 18f
    private val inspectorPanel = UIScrollPanel(game.uiReferenceWidth - 260, 0, 260, game.uiReferenceHeight)

    init {
        for (state in GameState.values()) {
            panels[state] = UIPanel()
        }

        // Narzędzie: Export (Prawy góra)
        editorToolbar.add(UIButton(game.uiReferenceWidth - 190, 10, 180, 50, "Export Code", fontSize = 24f) {
            exportCode()
        })

        exportField = UITextField(game.uiReferenceWidth / 4, game.uiReferenceHeight / 4, game.uiReferenceWidth / 2, game.uiReferenceHeight / 2, "", "Generated Code:")

        // Przyciski Menu Kontekstowego
        val btnW = 150
        val btnH = 40
        contextMenuButtons.add(UIButton(0, 0, btnW, btnH, "Add Button", fontSize = 20f) {
            addNewComponent(UIButton(spawnX, spawnY, 200, 50, "New Button") {})
            contextMenuVisible = false
        })
        contextMenuButtons.add(UIButton(0, btnH, btnW, btnH, "Add Input", fontSize = 20f) {
            addNewComponent(UITextField(spawnX, spawnY, 200, 40, "Text...", "Enter text..."))
            contextMenuVisible = false
        })
        contextMenuButtons.add(UIButton(0, btnH * 2, btnW, btnH, "Add Text", fontSize = 20f) {
            addNewComponent(UIText(spawnX, spawnY, "New Text", 24f, Color.WHITE))
            contextMenuVisible = false
        })
        contextMenuButtons.add(UIButton(0, btnH * 3, btnW, btnH, "Add Dropdown", fontSize = 20f) {
            addNewComponent(UIDropdown(game, spawnX, spawnY, 200, 40, mutableListOf("Opt 1", "Opt 2", "Opt 3"), 0) {})
            contextMenuVisible = false
        })
        contextMenuButtons.add(UIButton(0, btnH * 4, btnW, btnH, "Add Checkbox", fontSize = 20f) {
            addNewComponent(UICheckbox(spawnX, spawnY, 200, 40, "New Checkbox"))
            contextMenuVisible = false
        })
        contextMenuButtons.add(UIButton(0, btnH * 5, btnW, btnH, "Add Progress", fontSize = 20f) {
            addNewComponent(UIProgressBar(spawnX, spawnY, 200, 30))
            contextMenuVisible = false
        })
        contextMenuButtons.add(UIButton(0, btnH * 6, btnW, btnH, "Add Slider", fontSize = 20f) {
            addNewComponent(UISlider(spawnX, spawnY, 200, 30))
            contextMenuVisible = false
        })
        inspectorPanel.isVisible = false
    }

    private fun clearEditState(comp: UIComponent?) {
        when (comp) {
            is UIButton -> comp.editor.isEditing = false
            is UIText -> comp.editor.isEditing = false
            is UITextField -> comp.isFocused = false
            is UIDropdown -> comp.isExpanded = false
            is UICheckbox -> comp.editor.isEditing = false
            is UIProgressBar -> comp.editor.isEditing = false
            is UISlider -> comp.editor.isEditing = false
        }
    }

    private fun addNewComponent(comp: UIComponent) {
        val currentPanel = panels[game.gameState]
        if (currentPanel != null) {
            clearEditState(selectedComponent) // Wyczyść stary fokus przed dodaniem nowego
            comp.isTemporary = true
            comp.saveInitialState()
            currentPanel.add(comp)
            selectedComponent = comp // Od razu zaznacz nowo postawiony obiekt
            inspectorPanel.isVisible = false // Nie pokazuj panelu przy samym dodawaniu
        }
    }

    fun toggleEditor() {
        isEditorMode = !isEditorMode
        if (isEditorMode) {
            panels.values.forEach { panel ->
                panel.components.forEach { it.saveInitialState() }
            }
        } else {
            panels.values.forEach { panel ->
                panel.components.removeIf { it.isTemporary }
                panel.components.forEach { it.restoreInitialState() }
            }
            exportWindowVisible = false
            contextMenuVisible = false
            clearEditState(selectedComponent)
            selectedComponent = null
            activeDropdown = null
            inspectorPanel.isVisible = false
        }
    }

    private fun rebuildInspector() {
        val comp = selectedComponent ?: return
        inspectorPanel.clear()
        inspectorPanel.isVisible = true
        val labelColor = Color.YELLOW
        var currY = 10

        fun addHeader(text: String) {
            inspectorPanel.addChild(UIText(10, currY, text, 18f, labelColor))
            currY += 25
        }

        fun addField(label: String, initial: String, setter: (String) -> Unit) {
            inspectorPanel.addChild(UIText(10, currY, "$label:", 14f, Color.LIGHT_GRAY))
            val field = UITextField(10, currY + 18, 230, 30, initial, fontSize = inspectorFontSize)
            field.onTextChanged = { setter(it) }
            inspectorPanel.addChild(field)
            currY += 55
        }

        // --- WSPÓLNE POLA ---
        addHeader("Properties: ${comp.javaClass.simpleName}")
        addField("X", comp.x.toString()) { comp.x = it.toIntOrNull() ?: comp.x }
        addField("Y", comp.y.toString()) { comp.y = it.toIntOrNull() ?: comp.y }
        addField("Width", comp.width.toString()) { comp.width = it.toIntOrNull() ?: comp.width }
        addField("Height", comp.height.toString()) { comp.height = it.toIntOrNull() ?: comp.height }

        // --- POLA ZALEŻNE OD TYPU ---
        when (comp) {
            is UIButton -> {
                addField("Text", comp.text) { comp.text = it }
                addField("HEX Color", String.format("#%06X", comp.textColor.rgb and 0xFFFFFF)) {
                    try { comp.textColor = Color.decode(it) } catch (e: Exception) {}
                }
            }
            is UIText -> {
                addField("Text", comp.text) { comp.text = it }
                addField("Font Size", comp.fontSize.toString()) { comp.fontSize = it.toFloatOrNull() ?: comp.fontSize }
                addField("HEX Color", String.format("#%06X", comp.color.rgb and 0xFFFFFF)) {
                    try { comp.color = Color.decode(it) } catch (e: Exception) {}
                }
            }
            is UITextField -> {
                addField("Value", comp.text) { comp.text = it }
                addField("Placeholder", comp.placeholder) { comp.placeholder = it }
            }
            is UICheckbox -> {
                addField("Label", comp.text) { comp.text = it }
                addField("Font Size", comp.fontSize.toString()) { comp.fontSize = it.toFloatOrNull() ?: comp.fontSize }
                inspectorPanel.addChild(UIButton(10, currY, 230, 30, "State: ${comp.checked}") {
                    comp.checked = !comp.checked
                    rebuildInspector() // Odśwież napis na przycisku
                }.apply { fontSize = 16f })
                currY += 40
            }
            is UIProgressBar -> {
                addField("Label", comp.text) { comp.text = it }
                addField("Font Size", comp.fontSize.toString()) { comp.fontSize = it.toFloatOrNull() ?: comp.fontSize }
                addField("Progress (0.0-1.0)", comp.progress.toString()) { comp.progress = it.toFloatOrNull() ?: comp.progress }
                addField("HEX Fill", String.format("#%06X", comp.fillColor.rgb and 0xFFFFFF)) {
                    try { comp.fillColor = Color.decode(it) } catch (e: Exception) {}
                }
                inspectorPanel.addChild(UIButton(10, currY, 230, 30, "Mode: ${comp.mode}") {
                    comp.mode = ProgressBarMode.values()[(comp.mode.ordinal + 1) % ProgressBarMode.values().size]
                    rebuildInspector()
                }.apply { fontSize = 16f })
                currY += 40
            }
            is UISlider -> {
                addField("Label", comp.text) { comp.text = it }
                addField("Font Size", comp.fontSize.toString()) { comp.fontSize = it.toFloatOrNull() ?: comp.fontSize }
                addField("Min", comp.minVal.toString()) { comp.minVal = it.toFloatOrNull() ?: comp.minVal }
                addField("Max", comp.maxVal.toString()) { comp.maxVal = it.toFloatOrNull() ?: comp.maxVal }
                addField("Value", comp.currentVal.toString()) {
                    comp.currentVal = (it.toFloatOrNull() ?: comp.currentVal).coerceIn(comp.minVal, comp.maxVal)
                }
            }
            is UIDropdown -> {
                addField("Selected Index", comp.selectedIndex.toString()) { 
                    comp.selectedIndex = (it.toIntOrNull() ?: comp.selectedIndex).coerceIn(0, comp.options.size - 1)
                }
                addHeader("Options Management:")
                
                comp.options.forEachIndexed { index, opt ->
                    val rowY = currY
                    // Pole nazwy opcji
                    val optField = UITextField(10, rowY, 130, 25, opt, fontSize = inspectorFontSize)
                    optField.onTextChanged = { comp.options[index] = it }
                    inspectorPanel.addChild(optField)
                    
                    // Przyciski kontrolne (Góra, Dół, Usuń)
                    inspectorPanel.addChild(UIButton(145, rowY, 25, 25, "^") {
                        if (index > 0) {
                            java.util.Collections.swap(comp.options, index, index - 1)
                            rebuildInspector()
                        }
                    }.apply { fontSize = 14f })
                    
                    inspectorPanel.addChild(UIButton(175, rowY, 25, 25, "v") {
                        if (index < comp.options.size - 1) {
                            java.util.Collections.swap(comp.options, index, index + 1)
                            rebuildInspector()
                        }
                    }.apply { fontSize = 14f })
                    
                    inspectorPanel.addChild(UIButton(205, rowY, 25, 25, "X", textColor = Color.RED) {
                        if (comp.options.size > 1) {
                            comp.options.removeAt(index)
                            comp.selectedIndex = comp.selectedIndex.coerceIn(0, comp.options.size - 1)
                            rebuildInspector()
                        }
                    }.apply { fontSize = 14f })
                    
                    currY += 30
                }
                
                inspectorPanel.addChild(UIButton(10, currY, 230, 30, "+ Add Option") {
                    comp.options.add("New Option")
                    rebuildInspector()
                }.apply { fontSize = 16f })
                currY += 40
            }
        }
    }

    private fun renderInspector(g: Graphics2D, mouseX: Int, mouseY: Int) {
        if (!isEditorMode || selectedComponent == null || !inspectorPanel.isVisible) return

        // Tło panelu
        g.color = Color(40, 40, 40, 240)
        g.fillRect(inspectorPanel.x, 0, inspectorPanel.width, game.uiReferenceHeight)
        g.color = Color.BLACK
        g.drawRect(inspectorPanel.x, 0, inspectorPanel.width, game.uiReferenceHeight)

        // Linia oddzielająca
        g.color = Color.YELLOW
        g.stroke = BasicStroke(2f)
        g.drawLine(inspectorPanel.x, 0, inspectorPanel.x, game.uiReferenceHeight)

        inspectorPanel.render(g, game, mouseX, mouseY)
    }

    private fun exportCode() {
        val sb = StringBuilder()
        val currentPanel = panels[game.gameState]
        if (currentPanel != null) {
            currentPanel.components.forEach { comp ->
                if (comp is UIButton) {
                    sb.append(exportButtonCode(comp))
                } else if (comp is UIText) {
                    sb.append("UIText(${comp.x}, ${comp.y}, \"${comp.text}\", ${comp.fontSize}f, Color(${comp.color.red}, ${comp.color.green}, ${comp.color.blue}), centered = ${comp.centered})\n")
                } else if (comp is UITextField) {
                    sb.append("UITextField(${comp.x}, ${comp.y}, ${comp.width}, ${comp.height}, \"${comp.text}\", \"${comp.placeholder}\")\n")
                } else if (comp is UIDropdown) {
                    sb.append("UIDropdown(this, ${comp.x}, ${comp.y}, ${comp.width}, ${comp.height}, mutableListOf(${comp.options.joinToString { "\"$it\"" }}), ${comp.selectedIndex}) { }\n")
                } else if (comp is UICheckbox) {
                    sb.append("UICheckbox(${comp.x}, ${comp.y}, ${comp.width}, ${comp.height}, \"${comp.text}\", ${comp.checked}, fontSize = ${comp.fontSize}f) { }\n")
                } else if (comp is UIProgressBar) {
                    sb.append("UIProgressBar(${comp.x}, ${comp.y}, ${comp.width}, ${comp.height}, progress = ${comp.progress}f, text = \"${comp.text}\", fillColor = Color(${comp.fillColor.rgb}), mode = ProgressBarMode.${comp.mode}, fontSize = ${comp.fontSize}f)\n")
                } else if (comp is UISlider) {
                    sb.append("UISlider(${comp.x}, ${comp.y}, ${comp.width}, ${comp.height}, minVal = ${comp.minVal}f, maxVal = ${comp.maxVal}f, currentVal = ${comp.currentVal}f, text = \"${comp.text}\", fontSize = ${comp.fontSize}f) { val -> }\n")
                }
            }
        }
        exportField?.text = sb.toString()
        exportWindowVisible = true
    }

    private fun exportButtonCode(comp: UIButton): String {
        val xVal = comp.x
        val wVal = comp.width
        val xStr = if (kotlin.math.abs((xVal + wVal / 2) - game.uiReferenceWidth / 2) < 5) {
            "uiReferenceWidth/2 - ${wVal / 2}"
        } else {
            "$xVal"
        }
        val colorStr = if (comp.textColor != Color.WHITE) {
            ", textColor = Color(${comp.textColor.red}, ${comp.textColor.green}, ${comp.textColor.blue})"
        } else {
            ""
        }
        return "UIButton($xStr, ${comp.y}, $wVal, ${comp.height}, \"${comp.text}\"$colorStr) { }\n"
    }

    private fun renderEditorUI(g: Graphics2D, mouseX: Int, mouseY: Int) {
        val panel = panels[game.gameState] ?: return
        
        for (comp in panel.components) {
            val isSelected = comp == selectedComponent
            val isHovered = comp.isMouseOver(mouseX, mouseY)

            if (isSelected || isHovered) {
                g.color = if (isSelected) Color.YELLOW else Color(255, 255, 255, 150)
                g.stroke = BasicStroke(if (isSelected) 2f else 1f)
                
                val vx = comp.visualX
                val vy = comp.visualY
                g.drawRect(vx - 1, vy - 1, comp.width + 2, comp.height + 2)

                // Rysowanie uchwytów (handles) na rogach i krawędziach
                val hSize = 8
                val hOffset = hSize / 2
                val corners = arrayOf(
                    Point(vx, vy), // TL
                    Point(vx + comp.width, vy), // TR
                    Point(vx, vy + comp.height), // BL
                    Point(vx + comp.width, vy + comp.height), // BR
                    Point(vx + comp.width / 2, vy), // T
                    Point(vx + comp.width / 2, vy + comp.height), // B
                    Point(vx, vy + comp.height / 2), // L
                    Point(vx + comp.width, vy + comp.height / 2) // R
                )

                g.color = if (isSelected) Color.YELLOW else Color.WHITE
                for (p in corners) {
                    g.fillRect(p.x - hOffset, p.y - hOffset, hSize, hSize)
                    g.color = Color.BLACK
                    g.drawRect(p.x - hOffset, p.y - hOffset, hSize, hSize)
                    g.color = if (isSelected) Color.YELLOW else Color.WHITE
                }
            } else {
                g.color = Color(255, 0, 0, 50)
                g.stroke = BasicStroke(1f)
                g.drawRect(comp.visualX, comp.visualY, comp.width, comp.height)
            }
        }

        editorToolbar.forEach { it.render(g, game, mouseX, mouseY) }

        if (contextMenuVisible) {
            g.translate(contextMenuX, contextMenuY)
            contextMenuButtons.forEach { it.render(g, game, mouseX - contextMenuX, mouseY - contextMenuY) }
            g.color = Color(0, 255, 255)
            g.drawRect(0, 0, 150, contextMenuButtons.size * 40) // Podświetlenie obramowania menu
            g.translate(-contextMenuX, -contextMenuY)
        }

        if (exportWindowVisible) {
            g.color = Color(0, 0, 0, 230)
            g.fillRect(0, 0, game.uiReferenceWidth, game.uiReferenceHeight)
            exportField?.render(g, game, mouseX, mouseY)
            // Przycisk zamknięcia okna eksportu
            g.color = Color.RED
            g.fillRect(game.uiReferenceWidth * 3 / 4 - 30, game.uiReferenceHeight / 4 - 30, 30, 30)
            g.color = Color.WHITE
            g.drawString("X", game.uiReferenceWidth * 3 / 4 - 22, game.uiReferenceHeight / 4 - 8)
        }
    }

    fun getPanel(state: GameState): UIPanel = panels[state]!!

    fun render(g: Graphics2D, mouseX: Int, mouseY: Int) {
        val currentPanel = panels[game.gameState]

        // 1. Rysowanie głównych komponentów (tylko jeśli panel istnieje dla tego stanu)
        currentPanel?.render(g, game, mouseX, mouseY)

        // 2. Przycisk przełącznika (jeśli aktywowany F6)
        if (isEditorButtonVisible) editorToggleButton.render(g, game, mouseX, mouseY)

        if (isEditorMode) {
            renderEditorUI(g, mouseX, mouseY)
        }
        
        // 3. Rysowanie aktywnej listy dropdown (musi być pod Inspektorem w trybie edycji)
        activeDropdown?.renderList(g, game, mouseX, mouseY)

        // 4. Inspector (Prawy panel)
        if (isEditorMode && selectedComponent != null && inspectorPanel.isVisible) {
            renderInspector(g, mouseX, mouseY)
        }
        
        // 5. Rysowanie Tooltipów (Zawsze na samym wierzchu)
        if (currentPanel != null) {
            renderTooltipLayer(g, currentPanel, mouseX, mouseY)
        }
    }

    private fun renderTooltipLayer(g: Graphics2D, panel: UIPanel, mouseX: Int, mouseY: Int) {
        // Szukamy komponentu (również wewnątrz scroll paneli), który ma tooltip i jest pod myszką
        var hoveredText: String? = null

        // Przeszukujemy komponenty od końca (te na wierzchu są ostatnie na liście)
        for (comp in panel.components.asReversed()) {
            if (comp is UIScrollPanel) {
                // Specjalna obsługa dla scroll panelu (współrzędne relatywne)
                if (comp.isMouseOver(mouseX, mouseY)) {
                    val relX = mouseX - comp.x
                    val relY = mouseY - comp.y + comp.scrollY
                    for (child in comp.children.asReversed()) {
                        if (child.tooltipText != null && child.isMouseOver(relX, relY)) {
                            hoveredText = child.tooltipText
                            break
                        }
                    }
                }
            } else if (comp.tooltipText != null && comp.isMouseOver(mouseX, mouseY)) {
                hoveredText = comp.tooltipText
            }
            if (hoveredText != null) break
        }

        hoveredText?.let { drawTooltipWindow(g, it, mouseX, mouseY) }
    }

    private fun drawTooltipWindow(g: Graphics2D, text: String, mouseX: Int, mouseY: Int) {
        g.font = game.fpsFont.deriveFont(18f)
        val fm = g.fontMetrics
        val padding = 8
        val textW = fm.stringWidth(text)
        val textH = fm.ascent

        val rectW = textW + padding * 2
        val rectH = textH + padding * 2

        var tx = mouseX + 15
        var ty = mouseY + 15

        if (tx + rectW > game.referenceWidth) tx = mouseX - rectW - 5
        if (ty + rectH > game.referenceHeight) ty = mouseY - rectH - 5

        g.color = Color(0, 0, 0, 200)
        g.fillRect(tx, ty, rectW, rectH)
        g.color = Color.WHITE
        g.drawRect(tx, ty, rectW, rectH)
        g.drawString(text, tx + padding, ty + fm.ascent + padding - 2)
    }

    // Pozostałe metody delegujące bez zmian...
    fun handleClick(x: Int, y: Int): Boolean {
        // Przycisk ED ma zawsze priorytet, jeśli jest widoczny
        if (isEditorButtonVisible && editorToggleButton.onClick(x, y)) return true

        // Inspektor ma priorytet kliknięć nad Dropdownem w trybie edycji
        if (isEditorMode && inspectorPanel.isVisible && x >= inspectorPanel.x) {
            return inspectorPanel.onClick(x, y)
        }

        // Jeśli dropdown jest otwarty, ma on priorytet nad wszystkim
        activeDropdown?.let {
            if (dropdownJustOpened) {
                dropdownJustOpened = false
                return true 
            }

            val handled = it.onClick(x, y)
            if (!it.isExpanded) activeDropdown = null
            if (handled) return true
        }

        if (isEditorMode) {
            if (contextMenuVisible) {
                for (btn in contextMenuButtons) {
                    if (btn.onClick(x - contextMenuX, y - contextMenuY)) return true
                }
                contextMenuVisible = false // Zamknij menu jeśli kliknięto obok
            }

            if (exportWindowVisible) {
                if (x >= game.uiReferenceWidth * 3 / 4 - 30 && x <= game.uiReferenceWidth * 3 / 4 &&
                    y >= game.uiReferenceHeight / 4 - 30 && y <= game.uiReferenceHeight / 4) {
                    exportWindowVisible = false
                    return true
                }
                return exportField?.onClick(x, y) ?: false
            }
            for (tool in editorToolbar) {
                if (tool.onClick(x, y)) return true
            }
            
            // Pozwól zaznaczonemu Dropdownowi otworzyć się po pojedynczym kliknięciu
            if (selectedComponent is UIDropdown && selectedComponent!!.isMouseOver(x, y)) {
                if (selectedComponent!!.onClick(x, y)) {
                    if ((selectedComponent as UIDropdown).isExpanded) {
                        activeDropdown = selectedComponent as UIDropdown
                        dropdownJustOpened = true // Zapobiegaj natychmiastowemu zamknięciu przy zwolnieniu myszy
                    }
                    return true
                }
            }

            // W trybie edycji blokujemy standardowe kliknięcia komponentów panelu
            return true
        }
        
        val panelHandled = panels[game.gameState]?.handleClick(x, y) ?: false
        
        // Jeśli kliknięcie w panel spowodowało otwarcie jakiegoś dropdowna, zapisz go jako aktywny
        if (panelHandled && activeDropdown == null) {
            activeDropdown = panels[game.gameState]?.components?.find { it is UIDropdown && it.isExpanded } as? UIDropdown
        }
        
        return panelHandled
    }

    fun handleHover(x: Int, y: Int) {
        if (isEditorButtonVisible) editorToggleButton.onHover(x, y)
        
        if (isEditorMode && inspectorPanel.isVisible && x >= inspectorPanel.x) {
            inspectorPanel.onHover(x, y)
            return // Jeśli myszka jest nad inspektorem, nie hoverujemy elementów pod spodem
        }

        if (isEditorMode) {
            if (contextMenuVisible) contextMenuButtons.forEach { it.onHover(x - contextMenuX, y - contextMenuY) }
            editorToolbar.forEach { it.onHover(x, y) }
            if (exportWindowVisible) exportField?.onHover(x, y)
        }
        panels[game.gameState]?.handleHover(x, y)
    }

    fun handleScroll(amount: Int) {
        if (isEditorMode && inspectorPanel.isVisible && game.inputManager.windowPos.x >= inspectorPanel.x) {
            inspectorPanel.onScroll(amount)
        } else {
            activeDropdown?.onScroll(amount)
        }
        if (isEditorMode && exportWindowVisible) exportField?.onScroll(amount)
        panels[game.gameState]?.handleScroll(amount)
    }

    fun handlePress(x: Int, y: Int): Boolean {
        if (isEditorButtonVisible && editorToggleButton.isMouseOver(x, y)) return editorToggleButton.onPress(x, y)

        // 1. PRIORYTET: Inspektor (jeśli widoczny i myszka nad nim)
        if (isEditorMode && inspectorPanel.isVisible && x >= inspectorPanel.x) {
            return inspectorPanel.onPress(x, y)
        }

        // 2. PRIORYTET: Aktywny Dropdown (jeśli nie klikamy w nagłówek w trybie edycji)
        // Warstwa popup (aktywny dropdown) ma priorytet
        activeDropdown?.let {
            // W trybie edycji pozwalamy "przebić się" przez nagłówek aktywnego dropdowna, 
            // aby performComponentSelection mogło wykryć dwuklik dla Inspektora.
            val isHeaderClick = it.isMouseOver(x, y)
            if (!(isEditorMode && isHeaderClick)) {
                if (it.onPress(x, y)) return true
            }
        }

        if (isEditorMode) {
            // Obsługa prawego przycisku myszy (Otwieranie Context Menu)
            if (game.inputManager.isRightMouseDown) {
                contextMenuVisible = true
                contextMenuX = x
                contextMenuY = y
                // Zabezpieczenie by menu nie wyszło poza ekran
                if (contextMenuX + 150 > game.uiReferenceWidth) contextMenuX -= 150
                if (contextMenuY + 120 > game.uiReferenceHeight) contextMenuY -= 120
                
                spawnX = x
                spawnY = y
                return true
            }

            if (contextMenuVisible) return false // Blokuj inne akcje gdy menu jest otwarte

            if (exportWindowVisible) return exportField?.onPress(x, y) ?: false
            
            val panel = panels[game.gameState]
            
            // Toolbar Press
            for (tool in editorToolbar) if (tool.onPress(x, y)) return true

            if (panel != null) {
                val margin = 15

                // 1. PRIORYTET: Sprawdź najpierw aktualnie zaznaczony komponent (aby nie złapać sąsiada)
                selectedComponent?.let { comp ->
                    val vx = comp.visualX
                    val vy = comp.visualY
                    val isNear = x >= vx - margin && x <= vx + comp.width + margin &&
                                 y >= vy - margin && y <= vy + comp.height + margin
                    if (isNear) {
                        performComponentSelection(comp, x, y, margin)
                        return true
                    }
                }

                // 2. Jeśli nie trafiono w zaznaczony, szukaj nowego w reszcie komponentów
                for (comp in panel.components.asReversed()) {
                    if (comp == selectedComponent) continue

                    val vx = comp.visualX
                    val vy = comp.visualY
                    val isNear = x >= vx - margin && x <= vx + comp.width + margin &&
                                 y >= vy - margin && y <= vy + comp.height + margin

                    if (isNear) {
                        performComponentSelection(comp, x, y, margin)
                        return true
                    }
                }
            }
            
            // Kliknięcie w puste miejsce - zdejmij fokus/edycję z poprzedniego komponentu
            clearEditState(selectedComponent)
            selectedComponent = null
            activeDropdown = null
            inspectorPanel.isVisible = false
        }
        
        // Jeśli nie jesteśmy w trybie edycji, kliknięcie w panel może otworzyć dropdown
        return panels[game.gameState]?.handlePress(x, y) ?: false
    }

    private fun performComponentSelection(comp: UIComponent, x: Int, y: Int, margin: Int) {
        val currentTime = System.currentTimeMillis()
        val isDoubleClick = (currentTime - lastClickTime < 300) && (lastClickedComponent == comp)
        lastClickTime = currentTime
        lastClickedComponent = comp

        // Jeśli zmieniamy zaznaczenie, wyłącz tryb edycji tekstu na starym komponencie
        if (selectedComponent != comp) {
            clearEditState(selectedComponent)
        }

        // Przenieś komponent na sam wierzch warstwy (na koniec listy)
        moveComponentToFront(comp)

        selectedComponent = comp

        if (isDoubleClick) {
            // Pokazujemy panel TYLKO przy dwukliku
            rebuildInspector()

            when (comp) {
                is UIButton -> {
                    comp.editor.isEditing = true
                    comp.editor.cursorIndex = comp.text.length
                    comp.editor.selectionStartIndex = if (comp.text == "New Button") 0 else comp.editor.cursorIndex
                }
                is UIText -> {
                    comp.editor.isEditing = true
                    comp.editor.cursorIndex = comp.text.length
                    comp.editor.selectionStartIndex = if (comp.text == "New Text") 0 else comp.editor.cursorIndex
                }
                is UITextField -> {
                    comp.isFocused = true
                    comp.selectionStartIndex = 0
                    comp.cursorIndex = comp.text.length
                }
                is UIDropdown -> {
                    // Dwuklik otwiera tylko Inspektor (rebuildInspector() wywołane wyżej).
                    // Ekspansja listy jest obsługiwana przez pojedynczy klik w handleClick.
                }
                is UICheckbox -> {
                    comp.editor.isEditing = true
                    comp.editor.cursorIndex = comp.text.length
                }
                is UIProgressBar -> {
                    comp.editor.isEditing = true
                    comp.editor.cursorIndex = comp.text.length
                }
                is UISlider -> {
                    comp.editor.isEditing = true
                    comp.editor.cursorIndex = comp.text.length
                }
            }
        } else {
            // Przy pojedynczym kliknięciu (zaznaczenie/przesuwanie) panel zawsze znika
            inspectorPanel.isVisible = false
        }

        dragStartX = x; dragStartY = y
        initialCompX = comp.x; initialCompY = comp.y
        initialCompW = comp.width; initialCompH = comp.height

        val vx = comp.visualX
        val vy = comp.visualY

        isResizingLeft = kotlin.math.abs(x - vx) < margin
        isResizingRight = kotlin.math.abs(x - (vx + comp.width)) < margin
        isResizingTop = kotlin.math.abs(y - vy) < margin
        isResizingBottom = kotlin.math.abs(y - (vy + comp.height)) < margin

        isDragging = !isResizingLeft && !isResizingRight && !isResizingTop && !isResizingBottom
    }

    private fun moveComponentToFront(comp: UIComponent) {
        panels[game.gameState]?.components?.let { components ->
            if (components.isNotEmpty() && components.last() != comp) {
                if (components.remove(comp)) {
                    components.add(comp)
                }
            }
        }
    }

    fun handleRelease(x: Int, y: Int) {
        if (isEditorButtonVisible) editorToggleButton.onRelease(x, y)
        activeDropdown?.onRelease(x, y)
        
        if (isEditorMode && inspectorPanel.isVisible) inspectorPanel.onRelease(x, y)

        if (isEditorMode) {
            isDragging = false
            isResizingLeft = false; isResizingRight = false
            isResizingTop = false; isResizingBottom = false
            
            editorToolbar.forEach { it.onRelease(x, y) }
            if (exportWindowVisible) exportField?.onRelease(x, y)
        }
        panels[game.gameState]?.handleRelease(x, y)
    }

    fun handleDrag(x: Int, y: Int) {
        if (isEditorButtonVisible) editorToggleButton.onDrag(x, y)
        activeDropdown?.onDrag(x, y)

        if (isEditorMode && inspectorPanel.isVisible && x >= inspectorPanel.x) {
            inspectorPanel.onDrag(x, y)
            return
        }

        if (isEditorMode) {
            if (exportWindowVisible) { exportField?.onDrag(x, y); return }
            
            selectedComponent?.let { comp ->
                val dx = x - dragStartX
                val dy = y - dragStartY
                
                if (isDragging) {
                    comp.x = initialCompX + dx
                    comp.y = initialCompY + dy
                } else {
                    // Skalowanie poziome
                    if (isResizingLeft) {
                        val maxDx = initialCompW - 20 // Nie pozwól zmniejszyć poniżej 20px
                        val clampedDx = kotlin.math.min(dx, maxDx)
                        comp.x = initialCompX + clampedDx
                        comp.width = initialCompW - clampedDx
                    } else if (isResizingRight) {
                        comp.width = kotlin.math.max(20, initialCompW + dx)
                    }

                    // Skalowanie pionowe
                    if (isResizingTop) {
                        val maxDy = initialCompH - 20
                        val clampedDy = kotlin.math.min(dy, maxDy)
                        comp.y = initialCompY + clampedDy
                        comp.height = initialCompH - clampedDy
                    } else if (isResizingBottom) {
                        comp.height = kotlin.math.max(20, initialCompH + dy)
                    }
                }
            }
            editorToolbar.forEach { it.onDrag(x, y) }
        }
        panels[game.gameState]?.handleDrag(x, y)
    }

    fun handleKey(e: KeyEvent): Boolean {
        if (isEditorMode) {
            if (exportWindowVisible) return exportField?.onKey(e) ?: false
            // Jeśli edytujemy tekst w wybranym komponencie
            if (inspectorPanel.onKey(e)) return true
            
            if (selectedComponent?.onKey(e) == true) return true
        }
        return panels[game.gameState]?.handleKey(e) ?: false
    }
}

class UIPanel {
    val components = CopyOnWriteArrayList<UIComponent>()

    fun add(component: UIComponent) = components.add(component)
    fun clear() = components.clear()

    fun render(g: Graphics2D, game: KapeLuz, mouseX: Int, mouseY: Int) {
        for (component in components) {
            component.render(g, game, mouseX, mouseY)
        }
    }

    fun handleClick(x: Int, y: Int): Boolean {
        var handled = false
        for (component in components) {
            // Iterujemy po wszystkich komponentach, aby każdy mógł zaktualizować swój stan focusa
            if (component.onClick(x, y)) handled = true
        }
        return handled
    }

    fun handleHover(x: Int, y: Int) = components.forEach { it.onHover(x, y) }
    fun handleScroll(amount: Int) = components.forEach { it.onScroll(amount) }
    fun handlePress(x: Int, y: Int): Boolean = components.any { it.onPress(x, y) }
    fun handleRelease(x: Int, y: Int) = components.forEach { it.onRelease(x, y) }
    fun handleDrag(x: Int, y: Int) = components.forEach { it.onDrag(x, y) }
    fun handleKey(e: KeyEvent): Boolean = components.any { it.onKey(e) }
}