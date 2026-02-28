package org.lewapnoob.KapeLuz

import java.nio.ByteBuffer
import kotlin.math.PI

/**
 * Wysokowydajny protokół binarny.
 * Little Endian jest zazwyczaj szybszy na procesorach x86/ARM (native order).
 */
object NetworkProtocol {

    // Nagłówki pakietów
    const val PACKET_PLAYER_POS: Byte = 0x01
    const val PACKET_BLOCK_SET: Byte = 0x02
    const val PACKET_CHUNK_DATA: Byte = 0x03
    const val PACKET_WORLD_DATA: Byte = 0x04
    const val PACKET_CHUNK_REQUEST: Byte = 0x05
    const val PACKET_PLAYER_LIST: Byte = 0x06
    const val PACKET_KEEP_ALIVE: Byte = 0x07
    const val PACKET_DISCONNECT: Byte = 0x08
    const val PACKET_CHUNK_SECTION: Byte = 0x09
    const val PACKET_PLAYER_DIMENSION: Byte = 0x0A
    const val PACKET_ENTITY_SPAWN: Byte = 0x0B
    const val PACKET_ENTITY_MOVE: Byte = 0x0C
    const val PACKET_ENTITY_DESTROY: Byte = 0x0D
    const val PACKET_DROP_ITEM_REQUEST: Byte = 0x0E
    const val PACKET_ADD_ITEM: Byte = 0x0F

    // --- HELPERY (Dla wygody) ---
    fun ByteBuffer.putBool(value: Boolean) = this.put(if (value) 1.toByte() else 0.toByte())
    fun ByteBuffer.getBool(): Boolean = this.get() == 1.toByte()

    // --- World Data ---
    /**
     * HOST
     */
    fun encodeWorldData(game: KapeLuz, targetPlayerId: Byte, playerDimension: String): ByteBuffer {
        val dimBytes = playerDimension.toByteArray(Charsets.UTF_8)
        val estimatedSize = 64 + (game.oreColors.size * 4) + 4 + dimBytes.size
        val buffer = ByteBuffer.allocate(estimatedSize)
        buffer.put(PACKET_WORLD_DATA)

        // --- LISTA ZMIENNYCH ---
        buffer.putInt(game.seed)
        buffer.putDouble(game.gameTime)
        buffer.putInt(game.dayCounter)
        buffer.put(targetPlayerId)
        buffer.putBool(game.gameFrozen)

        // Dimension
        buffer.putInt(dimBytes.size)
        buffer.put(dimBytes)

        buffer.putInt(game.oreColors.size) // Zapisujemy liczbę elementów
        for (color in game.oreColors) {
            buffer.putInt(color)           // Zapisujemy każdy kolor jako Int
        }

        buffer.flip()
        return buffer
    }

    /**
     * CLIENT
     */
    fun decodeWorldData(buffer: ByteBuffer, game: KapeLuz) {
        buffer.get() // Skip header

        val newSeed = buffer.int
        if (game.seed != newSeed) {
            game.seed = newSeed
        }

        val serverTime = buffer.double
        if (kotlin.math.abs(game.gameTime - serverTime) > 0.5) game.gameTime = serverTime

        game.dayCounter = buffer.int

        val assignedId = if (buffer.hasRemaining()) buffer.get() else 0
        if (assignedId != 0.toByte()) game.myPlayerId = assignedId.toString()

        game.gameFrozen = if (buffer.hasRemaining()) buffer.getBool() else false

        // Dimension
        if (buffer.hasRemaining()) {
            val dimLen = buffer.int
            val dimBytes = ByteArray(dimLen)
            buffer.get(dimBytes)
            val serverDim = String(dimBytes, Charsets.UTF_8)

            if (game.localDimension != serverDim) {
                if (game.localDimension.isEmpty()) {
                    game.localDimension = serverDim
                } else {
                    game.changeDimension(serverDim)
                }
            }
        }

        // Deserializacja oreColors:
        if (buffer.hasRemaining()) {
            val colorCount = buffer.int
            val newColors = mutableListOf<Int>()
            for (i in 0 until colorCount) {
                newColors.add(buffer.int)
            }

            // Logika aktualizacji: Czyścimy i dodajemy nowe, jeśli zbiór się zmienił
            if (game.oreColors.size != newColors.size || !game.oreColors.containsAll(newColors)) {
                game.oreColors.clear()
                game.oreColors.addAll(newColors)
            }
        }
    }


    // --- ENKODERY (Wysyłanie) ---
    /**
     * Pakuje pozycję gracza do 16 bajtów (1 Header + 1 ID + 12 XYZ + 2 Rot).
     */
    fun encodePlayerPosition(playerId: Byte, x: Double, y: Double, z: Double, yaw: Double, pitch: Double): ByteBuffer {
        val buffer = ByteBuffer.allocate(16)
        buffer.put(PACKET_PLAYER_POS)
        buffer.put(playerId)

        // Konwersja Double -> Float (4 bajty vs 8 bajtów)
        buffer.putFloat(x.toFloat())
        buffer.putFloat(y.toFloat())
        buffer.putFloat(z.toFloat())

        // Kompresja kątów do 1 bajta (0-255)
        val yawNorm = (yaw % (2 * PI))
        val yawByte = ((if (yawNorm < 0) yawNorm + 2 * PI else yawNorm) / (2 * PI) * 255).toInt().toByte()
        val pitchByte = (((pitch + (PI / 2)) / PI) * 255).toInt().coerceIn(0, 255).toByte()

        buffer.put(yawByte)
        buffer.put(pitchByte)

        buffer.flip()
        return buffer
    }

    /**
     * Pakuje zmianę wymiaru gracza.
     */
    fun encodePlayerDimension(playerId: Byte, dimension: String): ByteBuffer {
        val bytes = dimension.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 1 + 4 + bytes.size) // Header + ID + Length + String
        buffer.put(PACKET_PLAYER_DIMENSION)
        buffer.put(playerId)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }

    /**
     * Pakuje zmianę bloku do 14 bajtów (1 Header + 8 XZ + 1 Y + 4 Color).
     */
    fun encodeBlockChange(x: Int, y: Int, z: Int, color: Int, metadata: Byte): ByteBuffer {
        val buffer = ByteBuffer.allocate(15)
        buffer.put(PACKET_BLOCK_SET)
        buffer.putInt(x)
        buffer.putInt(z)
        buffer.put(y.toByte()) // Y mieści się w 0-127
        buffer.putInt(color)
        buffer.put(metadata)

        buffer.flip()
        return buffer
    }

    /**
     * Pakuje sekcję chunka (16x16x16) używając RLE.
     */
    fun encodeChunkSection(cx: Int, cz: Int, sectionY: Int, chunkBlocks: IntArray, chunkMeta: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(65536)
        buffer.put(PACKET_CHUNK_SECTION)
        buffer.putInt(cx)
        buffer.putInt(cz)
        buffer.put(sectionY.toByte())

        val sectionSize = 16 * 16 * 16
        val startIdx = sectionY * sectionSize
        val endIdx = startIdx + sectionSize

        // 1. Kompresja Bloków (Int)
        var i = startIdx
        while (i < endIdx) {
            if (buffer.position() > 60000) break
            val currentBlock = chunkBlocks[i]
            var count = 1
            while (i + count < endIdx && chunkBlocks[i + count] == currentBlock && count < 255) {
                count++
            }
            buffer.put(count.toByte())
            buffer.putInt(currentBlock)
            i += count
        }

        // 2. Kompresja Metadanych (Byte)
        i = startIdx
        while (i < endIdx) {
            if (buffer.position() > 65000) break
            val currentMeta = chunkMeta[i]
            var count = 1
            while (i + count < endIdx && chunkMeta[i + count] == currentMeta && count < 255) {
                count++
            }
            buffer.put(count.toByte())
            buffer.put(currentMeta)
            i += count
        }

        buffer.flip()
        return buffer
    }

    /**
     * Pakuje CAŁY chunk (16x16x128) używając RLE.
     * To jest znacznie wydajniejsze niż wysyłanie 8 osobnych sekcji.
     */
    fun encodeChunk(cx: Int, cz: Int, chunkBlocks: IntArray, chunkMeta: ByteArray): ByteBuffer {
        // Alokujemy bezpieczny bufor (np. 128KB), ale zazwyczaj zużyjemy < 10KB
        val buffer = ByteBuffer.allocateDirect(131072)
        buffer.put(PACKET_CHUNK_DATA)
        buffer.putInt(cx)
        buffer.putInt(cz)

        val totalSize = 16 * 128 * 16 // 32768 bloków

        // 1. Kompresja Bloków (Int) - Cała tablica naraz
        var i = 0
        while (i < totalSize) {
            if (buffer.position() > 130000) break // Zabezpieczenie przed overflow
            val currentBlock = chunkBlocks[i]
            var count = 1

            // Zliczamy powtórzenia (max 255 w jednym bajcie)
            while (i + count < totalSize && chunkBlocks[i + count] == currentBlock && count < 255) {
                count++
            }
            buffer.put(count.toByte())
            buffer.putInt(currentBlock)
            i += count
        }

        // 2. Kompresja Metadanych (Byte) - Cała tablica naraz
        i = 0
        while (i < totalSize) {
            if (buffer.position() > 130000) break
            val currentMeta = chunkMeta[i]
            var count = 1
            while (i + count < totalSize && chunkMeta[i + count] == currentMeta && count < 255) {
                count++
            }
            buffer.put(count.toByte())
            buffer.put(currentMeta)
            i += count
        }

        buffer.flip()
        return buffer
    }

    fun encodeChunkRequest(cx: Int, cz: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(9)
        buffer.put(PACKET_CHUNK_REQUEST)
        buffer.putInt(cx)
        buffer.putInt(cz)
        buffer.flip()
        return buffer
    }

    /**
     * Pakuje listę graczy: [Header][Count][ID, X, Y, Z, Yaw, Pitch, DimensionString]...
     */
    fun encodePlayerList(players: Map<Byte, RemotePlayer>): ByteBuffer {
        // Obliczamy rozmiar bufora dynamicznie
        var size = 2 // Header + Count
        players.forEach { (_, p) ->
            size += 15 // ID + Pos + Rot
            size += 4 + p.dimension.toByteArray(Charsets.UTF_8).size // Length + String bytes
        }

        val buffer = ByteBuffer.allocate(size)
        buffer.put(PACKET_PLAYER_LIST)
        buffer.put(players.size.toByte())

        players.forEach { (id, p) ->
            buffer.put(id)
            buffer.putFloat(p.x.toFloat())
            buffer.putFloat(p.y.toFloat())
            buffer.putFloat(p.z.toFloat())

            val yawNorm = (p.yaw % (2 * PI))
            val yawByte = ((if (yawNorm < 0) yawNorm + 2 * PI else yawNorm) / (2 * PI) * 255).toInt().toByte()
            val pitchByte = (((p.pitch + (PI / 2)) / PI) * 255).toInt().coerceIn(0, 255).toByte()

            buffer.put(yawByte)
            buffer.put(pitchByte)

            val dimBytes = p.dimension.toByteArray(Charsets.UTF_8)
            buffer.putInt(dimBytes.size)
            buffer.put(dimBytes)
        }
        buffer.flip()
        return buffer
    }

    /**
     * Pakuje pojawienie się bytu (ItemEntity).
     */
    fun encodeEntitySpawn(entity: ItemEntity): ByteBuffer {
        val dimBytes = entity.dimension.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(65 + dimBytes.size)
        buffer.put(PACKET_ENTITY_SPAWN)
        buffer.putInt(entity.id)
        buffer.putDouble(entity.x)
        buffer.putDouble(entity.y)
        buffer.putDouble(entity.z)
        buffer.putDouble(entity.velX)
        buffer.putDouble(entity.velY)
        buffer.putDouble(entity.velZ)
        buffer.putInt(entity.itemStack.color)
        buffer.putInt(entity.itemStack.count)
        buffer.putInt(dimBytes.size)
        buffer.put(dimBytes)
        buffer.flip()
        return buffer
    }

    /**
     * Pakuje ruch bytu.
     */
    fun encodeEntityMove(id: Int, x: Double, y: Double, z: Double, velX: Double, velY: Double, velZ: Double, count: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(57) // 1 + 4 + 8*6 + 4
        buffer.put(PACKET_ENTITY_MOVE)
        buffer.putInt(id)
        buffer.putDouble(x)
        buffer.putDouble(y)
        buffer.putDouble(z)
        buffer.putDouble(velX)
        buffer.putDouble(velY)
        buffer.putDouble(velZ)
        buffer.putInt(count)
        buffer.flip()
        return buffer
    }

    fun encodeEntityDestroy(id: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(5)
        buffer.put(PACKET_ENTITY_DESTROY)
        buffer.putInt(id)
        buffer.flip()
        return buffer
    }

    fun encodeDropItemRequest(color: Int, count: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(9)
        buffer.put(PACKET_DROP_ITEM_REQUEST)
        buffer.putInt(color)
        buffer.putInt(count)
        buffer.flip()
        return buffer
    }

    fun encodeAddItem(color: Int, count: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(9)
        buffer.put(PACKET_ADD_ITEM)
        buffer.putInt(color)
        buffer.putInt(count)
        buffer.flip()
        return buffer
    }

    // --- DEKODERY ---

    data class DecodedEntitySpawn(val id: Int, val x: Double, val y: Double, val z: Double, val velX: Double, val velY: Double, val velZ: Double, val color: Int, val count: Int, val dimension: String)
    data class DecodedEntityMove(val id: Int, val x: Double, val y: Double, val z: Double, val velX: Double, val velY: Double, val velZ: Double, val count: Int)
    data class DecodedDropRequest(val color: Int, val count: Int)
    data class DecodedAddItem(val color: Int, val count: Int)

    fun decodeEntitySpawn(buffer: ByteBuffer): DecodedEntitySpawn {
        buffer.get() // Header
        val id = buffer.int
        val x = buffer.double; val y = buffer.double; val z = buffer.double
        val vx = buffer.double; val vy = buffer.double; val vz = buffer.double
        val color = buffer.int
        val count = buffer.int
        val dimLen = buffer.int
        val dimBytes = ByteArray(dimLen)
        buffer.get(dimBytes)
        return DecodedEntitySpawn(id, x, y, z, vx, vy, vz, color, count, String(dimBytes, Charsets.UTF_8))
    }

    fun decodeEntityMove(buffer: ByteBuffer): DecodedEntityMove {
        buffer.get() // Header
        val id = buffer.int
        val x = buffer.double; val y = buffer.double; val z = buffer.double
        val vx = buffer.double; val vy = buffer.double; val vz = buffer.double
        val count = if (buffer.hasRemaining()) buffer.int else 0
        return DecodedEntityMove(id, x, y, z, vx, vy, vz, count)
    }

    fun decodeDropRequest(buffer: ByteBuffer): DecodedDropRequest {
        buffer.get()
        val color = buffer.int
        val count = buffer.int
        return DecodedDropRequest(color, count)
    }

    fun decodeAddItem(buffer: ByteBuffer): DecodedAddItem {
        buffer.get()
        val color = buffer.int
        val count = buffer.int
        return DecodedAddItem(color, count)
    }

    fun encodeSimpleSignal(type: Byte): ByteBuffer {
        val buffer = ByteBuffer.allocate(1)
        buffer.put(type)
        buffer.flip()
        return buffer
    }

    fun encodeKeepAlive() = encodeSimpleSignal(PACKET_KEEP_ALIVE)
    fun encodeDisconnect() = encodeSimpleSignal(PACKET_DISCONNECT)

    // --- DEKODERY (Odbieranie) ---

    data class DecodedPosition(val playerId: Byte, val x: Double, val y: Double, val z: Double, val yaw: Double, val pitch: Double)
    data class DecodedDimension(val playerId: Byte, val dimension: String)
    data class DecodedBlock(val x: Int, val y: Int, val z: Int, val color: Int, val metadata: Byte)
    data class DecodedChunk(val cx: Int, val cz: Int, val blocks: IntArray, val metadata: ByteArray)
    data class DecodedChunkSection(val cx: Int, val cz: Int, val sectionY: Int, val blocks: IntArray, val metadata: ByteArray)
    data class DecodedChunkRequest(val cx: Int, val cz: Int)

    fun decodePacketType(buffer: ByteBuffer): Byte {
        if (buffer.remaining() == 0) return 0
        return buffer.get(0)
    }

    fun decodePlayerPosition(buffer: ByteBuffer): DecodedPosition {
        buffer.get() // Skip header
        val pid = buffer.get()

        val x = buffer.float.toDouble()
        val y = buffer.float.toDouble()
        val z = buffer.float.toDouble()

        val yawByte = buffer.get().toUByte().toInt()
        val pitchByte = buffer.get().toUByte().toInt()

        val yaw = (yawByte / 255.0) * (2 * PI)
        val pitch = (pitchByte / 255.0) * PI - (PI / 2)

        return DecodedPosition(pid, x, y, z, yaw, pitch)
    }

    fun decodePlayerDimension(buffer: ByteBuffer): DecodedDimension {
        buffer.get() // Skip header
        val pid = buffer.get()
        val len = buffer.int
        val bytes = ByteArray(len)
        buffer.get(bytes)
        return DecodedDimension(pid, String(bytes, Charsets.UTF_8))
    }

    fun decodeBlockChange(buffer: ByteBuffer): DecodedBlock {
        buffer.get() // Skip header
        val x = buffer.int
        val z = buffer.int
        val y = buffer.get().toInt() and 0xFF
        val color = buffer.int
        val metadata = if (buffer.hasRemaining()) buffer.get() else 0

        return DecodedBlock(x, y, z, color, metadata)
    }

    fun decodeChunk(buffer: ByteBuffer): DecodedChunk {
        buffer.get() // Skip header
        val cx = buffer.int
        val cz = buffer.int

        val totalSize = 16 * 128 * 16
        val blocks = IntArray(totalSize)
        val metadata = ByteArray(totalSize)

        var index = 0
        while (buffer.hasRemaining() && index < totalSize) {
            if (buffer.remaining() < 5) break
            val count = buffer.get().toInt() and 0xFF
            val color = buffer.int
            for (i in 0 until count) {
                if (index < totalSize) blocks[index++] = color
            }
        }

        index = 0
        while (buffer.hasRemaining() && index < totalSize) {
            if (buffer.remaining() < 2) break
            val count = buffer.get().toInt() and 0xFF
            val meta = buffer.get()
            for (i in 0 until count) {
                if (index < totalSize) metadata[index++] = meta
            }
        }

        return DecodedChunk(cx, cz, blocks, metadata)
    }

    fun decodeChunkSection(buffer: ByteBuffer): DecodedChunkSection {
        buffer.get() // Skip header
        val cx = buffer.int
        val cz = buffer.int
        val sectionY = buffer.get().toInt() and 0xFF

        val sectionSize = 16 * 16 * 16
        val blocks = IntArray(sectionSize)
        val metadata = ByteArray(sectionSize)

        var index = 0
        while (buffer.hasRemaining() && index < sectionSize) {
            if (buffer.remaining() < 5) break
            val count = buffer.get().toInt() and 0xFF
            val color = buffer.int
            for (k in 0 until count) {
                if (index < sectionSize) blocks[index++] = color
            }
        }

        index = 0
        while (buffer.hasRemaining() && index < sectionSize) {
            if (buffer.remaining() < 2) break
            val count = buffer.get().toInt() and 0xFF
            val meta = buffer.get()
            for (k in 0 until count) {
                if (index < sectionSize) metadata[index++] = meta
            }
        }
        return DecodedChunkSection(cx, cz, sectionY, blocks, metadata)
    }

    fun decodeChunkRequest(buffer: ByteBuffer): DecodedChunkRequest {
        buffer.get() // Skip header
        val cx = buffer.int
        val cz = buffer.int
        return DecodedChunkRequest(cx, cz)
    }

    fun decodePlayerList(buffer: ByteBuffer): Map<Byte, RemotePlayer> {
        buffer.get() // Skip header
        val count = buffer.get().toInt() and 0xFF
        val map = HashMap<Byte, RemotePlayer>()

        for (i in 0 until count) {
            val id = buffer.get()
            val x = buffer.float.toDouble()
            val y = buffer.float.toDouble()
            val z = buffer.float.toDouble()
            val yawByte = buffer.get().toUByte().toInt()
            val pitchByte = buffer.get().toUByte().toInt()

            val yaw = (yawByte / 255.0) * (2 * PI)
            val pitch = (pitchByte / 255.0) * PI - (PI / 2)

            val dimLen = buffer.int
            val dimBytes = ByteArray(dimLen)
            buffer.get(dimBytes)
            val dimension = String(dimBytes, Charsets.UTF_8)

            map[id] = RemotePlayer(x, y, z, yaw, pitch, System.currentTimeMillis(), System.currentTimeMillis(), dimension)
        }
        return map
    }
}