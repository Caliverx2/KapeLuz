import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.cos
import kotlin.math.sin

// Główna klasa wizualizera
class Minecraft3DVisualizer : JFrame("Minecraft 3D Skin Visualizer (Custom Engine)") {
    private val renderPanel = Skin3DRenderPanel()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(600, 700)
        setLocationRelativeTo(null)
        layout = BorderLayout()

        val btnLoad = JButton("Importuj Skin (PNG)").apply {
            addActionListener { chooseAndLoadSkin() }
        }

        add(renderPanel, BorderLayout.CENTER)
        add(btnLoad, BorderLayout.SOUTH)
    }

    private fun chooseAndLoadSkin() {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val img = ImageIO.read(chooser.selectedFile)
            if (img.width == 64 && img.height == 64) {
                renderPanel.setSkin(img)
            } else {
                JOptionPane.showMessageDialog(this, "Wymagany format 64x64!", "Błąd", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
}

// Klasa reprezentująca punkt w przestrzeni 3D
data class Point3D(val x: Double, val y: Double, val z: Double) {
    // Obrót wokół osi Y (lewo-prawo) i osi X (góra-dół)
    fun rotate(angleY: Double, angleX: Double): Point3D {
        // Obrót Y
        val cosY = cos(angleY)
        val sinY = sin(angleY)
        val x1 = x * cosY - z * sinY
        val z1 = x * sinY + z * cosY

        // Obrót X
        val cosX = cos(angleX)
        val sinX = sin(angleX)
        val y2 = y * cosX - z1 * sinX
        val z2 = y * sinX + z1 * cosX

        return Point3D(x1, y2, z2)
    }
}

// Klasa definiująca pojedynczą ściankę 3D modelu (Face)
data class CubeFace(
    val pTL: Point3D, val pTR: Point3D, val pBL: Point3D, val pBR: Point3D,
    val textureX: Int, val textureY: Int, val texW: Int, val texH: Int,
    val isOverlay: Boolean = false,
    val color: Color? = null // Jeśli ustawiony, używamy koloru zamiast tekstury dla wydajności/szczelności
)

class Skin3DRenderPanel : JPanel() {
    private var skinTexture: BufferedImage? = null
    private var angleY = 0.6  // Początkowy obrót
    private var angleX = -0.2

    // Parametry wizualne
    private var overlayOffset = 0.75 // Grubość 3D ubrań
    private var lastMouseX = 0
    private var lastMouseY = 0
    private val scale = 12.0 // Skala renderowania wielkości postaci
    private var zBuffer = DoubleArray(0)
    private var pixelData = IntArray(0)

    init {
        background = Color(40, 44, 52)

        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                lastMouseX = e.x
                lastMouseY = e.y
            }

            override fun mouseDragged(e: MouseEvent) {
                val dx = e.x - lastMouseX
                val dy = e.y - lastMouseY
                angleY += dx * 0.01
                angleX += dy * 0.01
                lastMouseX = e.x
                lastMouseY = e.y
                repaint()
            }
        }
        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
    }

    fun setSkin(img: BufferedImage) {
        this.skinTexture = img
        repaint()
    }

    private fun createCube(
        cx: Double, cy: Double, cz: Double,
        w: Double, h: Double, d: Double,
        texX: Int, texY: Int,
        tw: Int, th: Int, td: Int,
        isOverlay: Boolean = false
    ): List<CubeFace> {
        // Delta sprawia, że druga warstwa "puchnie", domyka model i zapobiega Z-fightingowi
        val delta = if (isOverlay) overlayOffset else 0.0
        val x0 = cx - delta; val x1 = cx + w + delta
        val y0 = cy - delta; val y1 = cy + h + delta
        val z0 = cz - delta; val z1 = cz + d + delta

        // Wierzchołki kostki
        val v0 = Point3D(x0, y0, z0)
        val v1 = Point3D(x1, y0, z0)
        val v2 = Point3D(x1, y1, z0)
        val v3 = Point3D(x0, y1, z0)
        val v4 = Point3D(x0, y0, z1)
        val v5 = Point3D(x1, y0, z1)
        val v6 = Point3D(x1, y1, z1)
        val v7 = Point3D(x0, y1, z1)

        // Kolejność punktów: Top-Left, Top-Right, Bottom-Left, Bottom-Right dla każdej ściany
        return listOf(
            CubeFace(v0, v1, v3, v2, texX + td, texY + td, tw, th, isOverlay),           // Przód
            CubeFace(v5, v4, v6, v7, texX + td * 2 + tw, texY + td, tw, th, isOverlay),   // Tył
            CubeFace(v4, v5, v0, v1, texX + td, texY, tw, td, isOverlay),               // Góra
            CubeFace(v6, v7, v2, v3, texX + td + tw, texY, tw, td, isOverlay),          // Dół (Lustro góra/dół)
            CubeFace(v1, v5, v2, v6, texX + td + tw, texY + td, td, th, isOverlay),      // Lewo
            CubeFace(v4, v0, v7, v3, texX, texY + td, td, th, isOverlay)                // Prawo
        )
    }

    /**
     * Generuje warstwę 3D (voxele) adaptacyjnie dla każdej ściany.
     * Każdy piksel zamieniany jest w bryłę o 5 widocznych ścianach,
     * skierowaną normalną na zewnątrz danej części ciała.
     */
    private fun createVoxelizedLayer(
        img: BufferedImage,
        cx: Double, cy: Double, cz: Double,
        w: Int, h: Int, d: Int,
        texX: Int, texY: Int,
        tw: Int, th: Int, td: Int
    ): List<CubeFace> {
        val faces = mutableListOf<CubeFace>()
        val off = overlayOffset

        fun getC(tx: Int, ty: Int): Color? {
            if (tx < 0 || ty < 0 || tx >= img.width || ty >= img.height) return null
            val argb = img.getRGB(tx, ty)
            if (((argb shr 24) and 0xff) < 10) return null
            return Color(argb, true)
        }

        // Definicje 6 głównych płaszczyzn modelu.
        // Każda zawiera: UV start, wymiary (W, H) oraz funkcję tworzącą wierzchołki lokalne.
        val configs = listOf(
            // FRONT: Patrzy w stronę -Z
            VoxelSideConfig(texX + td, texY + td, tw, th) { lx, ly, z -> Point3D(cx + lx, cy + ly, cz - z) },
            // BACK: Patrzy w stronę +Z (X odwrócony)
            VoxelSideConfig(texX + td * 2 + tw, texY + td, tw, th) { lx, ly, z -> Point3D(cx + w - lx, cy + ly, cz + d + z) },
            // TOP: Patrzy w stronę -Y (Z odwrócony)
            VoxelSideConfig(texX + td, texY, tw, td) { lx, ly, z -> Point3D(cx + lx, cy - z, cz + td - ly) },
            // BOTTOM: Patrzy w stronę +Y (Obrócony o 180 stopni dla zgodności z formatem skina)
            VoxelSideConfig(texX + td + tw, texY, tw, td) { lx, ly, z -> Point3D(cx + lx, cy + h + z, cz + td - ly) },

            // RIGHT: Patrzy w stronę -X
            VoxelSideConfig(texX, texY + td, td, th) { lx, ly, z -> Point3D(cx - z, cy + ly, cz + td - lx) },
            // LEFT: Patrzy w stronę +X
            VoxelSideConfig(texX + td + tw, texY + td, td, th) { lx, ly, z -> Point3D(cx + w + z, cy + ly, cz + lx) }
        )

        for (cfg in configs) {
            for (lx in 0 until cfg.w) {
                for (ly in 0 until cfg.h) {
                    val color = getC(cfg.tx + lx, cfg.ty + ly) ?: continue

                    val tX = cfg.tx + lx
                    val tY = cfg.ty + ly

                    // Wierzchołki Zewnętrzne (Outer)
                    val o00 = cfg.pos(lx.toDouble(), ly.toDouble(), off)
                    val o10 = cfg.pos(lx + 1.0, ly.toDouble(), off)
                    val o01 = cfg.pos(lx.toDouble(), ly + 1.0, off)
                    val o11 = cfg.pos(lx + 1.0, ly + 1.0, off)

                    // Wierzchołki Bazowe (Base - stykające się z modelem)
                    val b00 = cfg.pos(lx.toDouble(), ly.toDouble(), 0.0)
                    val b10 = cfg.pos(lx + 1.0, ly.toDouble(), 0.0)
                    val b01 = cfg.pos(lx.toDouble(), ly + 1.0, 0.0)
                    val b11 = cfg.pos(lx + 1.0, ly + 1.0, 0.0)

                    // 1. Ścianka zewnętrzna (Front voxela)
                    faces.add(CubeFace(o00, o10, o01, o11, tX, tY, 1, 1, true, color))

                    // 2. Ścianka wewnętrzna (Base voxela - zamyka go od strony ciała)
                    faces.add(CubeFace(b10, b00, b11, b01, tX, tY, 1, 1, true, color))

                    // 3. Lewy bok (patrząc na voxel z zewnątrz)
                    faces.add(CubeFace(b00, o00, b01, o01, tX, tY, 1, 1, true, color))

                    // 4. Prawy bok
                    faces.add(CubeFace(o10, b10, o11, b11, tX, tY, 1, 1, true, color))

                    // 5. Góra
                    faces.add(CubeFace(b00, b10, o00, o10, tX, tY, 1, 1, true, color))

                    // 6. Dół
                    faces.add(CubeFace(o01, o11, b01, b11, tX, tY, 1, 1, true, color))
                }
            }
        }
        return faces
    }

    // Klasa pomocnicza dla definicji ściany
    private data class VoxelSideConfig(
        val tx: Int, val ty: Int, val w: Int, val h: Int,
        val pos: (Double, Double, Double) -> Point3D
    )

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val img = skinTexture ?: return
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Inicjalizacja buforów obrazu
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val bufferImg = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        pixelData = (bufferImg.raster.dataBuffer as DataBufferInt).data
        if (zBuffer.size != w * h) {
            zBuffer = DoubleArray(w * h)
        }
        zBuffer.fill(Double.POSITIVE_INFINITY)

        val centerX = width / 2
        val centerY = height / 2 - 50

        val faces = mutableListOf<CubeFace>()

        // --- WARSTWA BAZOWA ---
        faces.addAll(createCube(-4.0, -12.0, -4.0, 8.0, 8.0, 8.0, 0, 0, 8, 8, 8))
        faces.addAll(createCube(-4.0, -4.0, -2.0, 8.0, 12.0, 4.0, 16, 16, 8, 12, 4))
        faces.addAll(createCube(-8.0, -4.0, -2.0, 4.0, 12.0, 4.0, 40, 16, 4, 12, 4))
        faces.addAll(createCube(4.0, -4.0, -2.0, 4.0, 12.0, 4.0, 32, 48, 4, 12, 4))
        faces.addAll(createCube(-4.0, 8.0, -2.0, 4.0, 12.0, 4.0, 0, 16, 4, 12, 4))
        faces.addAll(createCube(0.0, 8.0, -2.0, 4.0, 12.0, 4.0, 16, 48, 4, 12, 4))

        // --- WARSTWA OVERLAY ---
        faces.addAll(createVoxelizedLayer(img, -4.0, -12.0, -4.0, 8, 8, 8, 32, 0, 8, 8, 8))   // Czapka
        faces.addAll(createVoxelizedLayer(img, -4.0, -4.0, -2.0, 8, 12, 4, 16, 32, 8, 12, 4)) // Kurtka
        faces.addAll(createVoxelizedLayer(img, -8.0, -4.0, -2.0, 4, 12, 4, 40, 32, 4, 12, 4)) // Rękaw P
        faces.addAll(createVoxelizedLayer(img, 4.0, -4.0, -2.0, 4, 12, 4, 48, 48, 4, 12, 4))  // Rękaw L
        faces.addAll(createVoxelizedLayer(img, -4.0, 8.0, -2.0, 4, 12, 4, 0, 32, 4, 12, 4))   // Nogawka P
        faces.addAll(createVoxelizedLayer(img, 0.0, 8.0, -2.0, 4, 12, 4, 0, 48, 4, 12, 4))    // Nogawka L

        // Renderowanie z użyciem Z-Buffer
        for (face in faces) {
            val p1 = face.pTL.rotate(angleY, angleX)
            val p2 = face.pTR.rotate(angleY, angleX)
            val p3 = face.pBL.rotate(angleY, angleX)
            val p4 = face.pBR.rotate(angleY, angleX)

            // Rozbicie czworokąta na dwa trójkąty dla rasteryzacji
            drawTriangle(p1, p2, p3, face, img, w, h, centerX, centerY, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0)
            drawTriangle(p2, p4, p3, face, img, w, h, centerX, centerY, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        }

        g2d.drawImage(bufferImg, 0, 0, null)
    }

    private fun drawTriangle(
        p1: Point3D, p2: Point3D, p3: Point3D,
        face: CubeFace, img: BufferedImage,
        sw: Int, sh: Int, cx: Int, cy: Int,
        u1: Double, v1: Double, u2: Double, v2: Double, u3: Double, v3: Double
    ) {
        // Ekranowe punkty 2D
        val x1 = cx + p1.x * scale; val y1 = cy + p1.y * scale
        val x2 = cx + p2.x * scale; val y2 = cy + p2.y * scale
        val x3 = cx + p3.x * scale; val y3 = cy + p3.y * scale

        // Backface Culling
        if ((x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1) < 0) return

        // Bounding box trójkąta
        val minX = maxOf(0, minOf(x1, x2, x3).toInt())
        val maxX = minOf(sw - 1, maxOf(x1, x2, x3).toInt())
        val minY = maxOf(0, minOf(y1, y2, y3).toInt())
        val maxY = minOf(sh - 1, maxOf(y1, y2, y3).toInt())

        val det = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3)

        for (py in minY..maxY) {
            for (px in minX..maxX) {
                val l1 = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / det
                val l2 = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / det
                val l3 = 1.0 - l1 - l2

                if (l1 >= 0 && l2 >= 0 && l3 >= 0) {
                    // Interpolacja Z (głębi)
                    val z = l1 * p1.z + l2 * p2.z + l3 * p3.z
                    val idx = py * sw + px
                    
                    // Przesunięcie dla overlay, żeby uniknąć Z-fighting na styku warstw
                    val finalZ = if (face.isOverlay) z - 0.05 else z

                    if (finalZ < zBuffer[idx]) {
                        zBuffer[idx] = finalZ
                        
                        val color = if (face.color != null) {
                            face.color.rgb
                        } else {
                            // Interpolacja UV dla tekstury
                            val u = (l1 * u1 + l2 * u2 + l3 * u3) * (face.texW - 0.001)
                            val v = (l1 * v1 + l2 * v2 + l3 * v3) * (face.texH - 0.001)
                            val tx = face.textureX + u.toInt()
                            val ty = face.textureY + v.toInt()
                            
                            if (tx in 0 until img.width && ty in 0 until img.height) {
                                val c = img.getRGB(tx, ty)
                                if ((c shr 24 and 0xFF) < 10) {
                                    continue
                                }
                                c
                            } else 0
                        }
                        
                        // Dodanie cieniowania (prosty Lambert)
                        pixelData[idx] = applySimpleLighting(color, face.isOverlay)
                    }
                }
            }
        }
    }

    private fun applySimpleLighting(rgb: Int, isOverlay: Boolean): Int {
        val a = rgb shr 24 and 0xFF
        var r = rgb shr 16 and 0xFF
        var g = rgb shr 8 and 0xFF
        var b = rgb and 0xFF
        
        // Prosty efekt głębi (ciemniej to co dalej)
        val factor = if (isOverlay) 1.0 else 0.9
        r = (r * factor).toInt(); g = (g * factor).toInt(); b = (b * factor).toInt()
        
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

fun main() {
    SwingUtilities.invokeLater {
        Minecraft3DVisualizer().isVisible = true
    }
}