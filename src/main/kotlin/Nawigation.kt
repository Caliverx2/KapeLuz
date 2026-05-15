package org.lewapnoob.TDoA

import java.awt.*
import java.awt.event.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import javax.swing.*

class MultilaterationSim : JPanel(), Runnable {
    // Dane symulacji
    private var transmitter: Point2D.Double? = Point2D.Double(400.0, 300.0)
    private val receivers = mutableListOf<Point2D.Double>()
    private var waveRadius = 0.0
    private var isRunning = false
    private var useToA = false // Flaga trybu: false = TDoA (hiperbole), true = ToA (okręgi)

    // Logika TDOA
    private val detectionTimes = mutableMapOf<Point2D.Double, Double>()
    private val speedOfWave = 2.0 // Prędkość "światła" w px/frame

    private var draggedPoint: Point2D.Double? = null

    init {
        receivers.add(Point2D.Double(200.0, 200.0))
        receivers.add(Point2D.Double(600.0, 200.0))
        receivers.add(Point2D.Double(400.0, 500.0))

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val p = Point2D.Double(e.x.toDouble(), e.y.toDouble())
                draggedPoint = findNear(p)
                if (draggedPoint == null && SwingUtilities.isRightMouseButton(e)) {
                    receivers.add(p)
                } else if (draggedPoint != null && SwingUtilities.isMiddleMouseButton(e)) {
                    if (draggedPoint != transmitter) receivers.remove(draggedPoint)
                    draggedPoint = null
                }
            }
            override fun mouseReleased(e: MouseEvent) { draggedPoint = null }
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                draggedPoint?.setLocation(e.x.toDouble(), e.y.toDouble())
            }
        })

        Thread(this).start()
    }

    private fun findNear(p: Point2D.Double): Point2D.Double? {
        if (transmitter?.distance(p) ?: Double.MAX_VALUE < 15) return transmitter
        return receivers.find { it.distance(p) < 15 }
    }

    override fun run() {
        while (true) {
            if (isRunning) {
                waveRadius += speedOfWave
                // Sprawdź detekcję fali przez odbiorniki
                receivers.forEach { r ->
                    val dist = transmitter?.distance(r) ?: 0.0
                    if (waveRadius >= dist && !detectionTimes.containsKey(r)) {
                        detectionTimes[r] = waveRadius
                    }
                }
                // Reset fali po przejściu przez wszystko
                if (waveRadius > 1000) {
                    waveRadius = 0.0
                    detectionTimes.clear()
                }
            }
            repaint()
            Thread.sleep(16)
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Tło
        g2.color = Color.BLACK
        g2.fillRect(0, 0, width, height)

        if (isRunning) {
            // Rysuj falę
            transmitter?.let {
                g2.color = Color(0, 150, 255, 100)
                g2.draw(Ellipse2D.Double(it.x - waveRadius, it.y - waveRadius, waveRadius * 2, waveRadius * 2))
            }

            // Rysuj logikę lokalizacji
            if (receivers.size >= 2 && detectionTimes.isNotEmpty()) {
                if (useToA) {
                    drawToACircles(g2)
                } else if (detectionTimes.size >= 2) {
                    drawHyperbolas(g2)
                }
            }
        }

        // Rysuj Odbiorniki
        receivers.forEach {
            g2.color = if (detectionTimes.containsKey(it)) Color.GREEN else Color.WHITE
            g2.fillOval(it.x.toInt() - 6, it.y.toInt() - 6, 12, 12)
            g2.drawOval(it.x.toInt() - 10, it.y.toInt() - 10, 20, 20)
        }

        // Rysuj Nadajnik
        transmitter?.let {
            g2.color = Color.RED
            g2.fillOval(it.x.toInt() - 8, it.y.toInt() - 8, 16, 16)
        }

        g2.color = Color.WHITE
        val modeText = if (useToA) "TRYB: ToA (Okręgi)" else "TRYB: TDoA (Hiperbole)"
        g2.drawString("$modeText | LPM: Przesuń | PPM: Dodaj | ŚPM: Usuń", 10, height - 20)
    }

    /**
     * PRZYKŁAD LOGIKI ToA (Time of Arrival)
     * Tutaj nie odejmujemy czasów od siebie, tylko porównujemy dystans
     * punktu na mapie bezpośrednio z zapisanym "czasem" (promieniem) detekcji.
     */
    private fun drawToACircles(g2: Graphics2D) {
        val tolerance = 2.0
        val step = 3
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                var matchCount = 0
                receivers.forEach { r ->
                    val recordedDist = detectionTimes[r] ?: return@forEach
                    val pointDist = r.distance(x.toDouble(), y.toDouble())
                    
                    if (Math.abs(pointDist - recordedDist) < tolerance) {
                        matchCount++
                    }
                }
                
                if (matchCount >= receivers.size && receivers.size > 0) {
                    g2.color = Color.YELLOW
                    g2.fillRect(x, y, step, step)
                } else if (matchCount > 0) {
                    // Rysuje fragmenty okręgów dla każdego odbiornika
                    g2.color = Color(255, 255, 0, 50)
                    g2.fillRect(x, y, step, step)
                }
            }
        }
    }

    private fun drawHyperbolas(g2: Graphics2D) {
        if (receivers.size < 2 || detectionTimes.size < 2) return

        val baseRec = receivers[0]
        val t0 = detectionTimes[baseRec] ?: return

        val tolerance = 3.0 // Bazowy błąd pomiaru
        val step = 3

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val d0 = baseRec.distance(x.toDouble(), y.toDouble())
                var totalError = 0.0
                var inAllZones = true

                for (i in 1 until receivers.size) {
                    val rN = receivers[i]
                    val tn = detectionTimes[rN] ?: continue
                    val deltaDistActual = tn - t0
                    val deltaDistPoint = rN.distance(x.toDouble(), y.toDouble()) - d0

                    val diff = Math.abs(deltaDistPoint - deltaDistActual)

                    if (diff > tolerance) {
                        inAllZones = false
                    }
                    totalError += diff
                }

                if (inAllZones) {
                    // OBSZAR PRECYZYJNY (Cyjan) - tu przecinają się wszystkie pasy
                    g2.color = Color(0, 255, 255, 180)
                    g2.fillRect(x, y, step, step)
                } else {
                    // OBSZAR NIEPEWNOŚCI (Gradient fioletowy)
                    // Pokazuje pasy poszczególnych par, co uwidoczni ich "równoległość"
                    val avgError = totalError / (receivers.size - 1)
                    if (avgError < tolerance * 2.5) {
                        val alpha = (150 - (avgError * 20)).toInt().coerceIn(0, 100)
                        g2.color = Color(200, 0, 255, alpha)
                        g2.fillRect(x, y, step, step)
                    }
                }
            }
        }
    }

    fun toggleSimulation() {
        isRunning = !isRunning
        waveRadius = 0.0
        detectionTimes.clear()
    }

    fun toggleMode() {
        useToA = !useToA
        repaint()
    }
}

fun main() {
    val frame = JFrame("Lokalizator: ToA vs TDoA")
    val sim = MultilaterationSim()
    
    val controls = JPanel()
    val btnSim = JButton("Start/Stop Fali")
    val btnMode = JButton("Przełącz ToA/TDoA")
    
    btnSim.addActionListener { sim.toggleSimulation() }
    btnMode.addActionListener { sim.toggleMode() }

    controls.add(btnSim)
    controls.add(btnMode)

    frame.layout = BorderLayout()
    frame.add(sim, BorderLayout.CENTER)
    frame.add(controls, BorderLayout.SOUTH)
    frame.size = Dimension(800, 600)
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true
}