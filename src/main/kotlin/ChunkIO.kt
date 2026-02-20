package org.lewapnoob.KapeLuz

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Zwraca listę nazw światów (folderów) znajdujących się w katalogu zapisu gry.
 */
fun listWorlds(): List<String> {
    val savesDir = File(gameDir, "saves")
    if (!savesDir.exists() || !savesDir.isDirectory) {
        return emptyList()
    }
    return savesDir.listFiles { file -> file.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
}

data class WorldData(
    val seed: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double,
    val pitch: Double,
    val debugNoclip: Boolean = false,
    val debugFly: Boolean = false,
    val debugFullbright: Boolean = false,
    val showChunkBorders: Boolean = false,
    val debugXray: Boolean = false,
    val gameTime: Double = 12.0,
    val dayCounter: Int = 0,
    val localDimension: String = "overworld"
)

open class ChunkIO(worldName: String) {
    companion object {
        private const val SAVE_MAGIC_HEADER = 0x4C574150 // "LWAP" w HEX - unikalny identyfikator
        private const val CURRENT_VERSION = 2 // Podbijamy wersję dla nowego formatu
        private const val REGION_SIZE = 32 // 32x32 chunki w regionie

        // Typy danych do dynamicznego zapisu (Tag System)
        private const val TAG_END: Byte = 0
        private const val TAG_BOOLEAN: Byte = 1
        private const val TAG_INT: Byte = 2
        private const val TAG_DOUBLE: Byte = 3
        private const val TAG_STRING: Byte = 4
    }

    // Używamy scentralizowanego folderu gry zdefiniowanego w KapeLuzModAPI.kt
    val saveDir = File(gameDir, "saves/$worldName").apply { mkdirs() }

    // Cache otwartych plików regionów (opcjonalne, dla wydajności przy częstym zapisie)
    // W tej implementacji otwieramy/zamykamy plik przy każdej operacji dla bezpieczeństwa danych.

    open fun saveChunk(chunk: Chunk, dimension: String = "overworld") {
        try {
            // Obliczamy koordynaty regionu
            val regionX = chunk.x shr 5 // Dzielenie przez 32 (bit shift)
            val regionZ = chunk.z shr 5

            val regionDir = File(saveDir, "dimensions/$dimension/regions")
            regionDir.mkdirs()
            val regionFile = File(regionDir, "r_${regionX}_${regionZ}.rgn")

            // Kompresja chunka do pamięci RAM (Palette + RLE)
            val compressedData = ChunkCompressor.compress(chunk)

            RandomAccessFile(regionFile, "rw").use { raf ->
                // FIX: Inicjalizacja nagłówka, jeśli plik jest nowy/pusty
                // Nagłówek dla 1024 chunków zajmuje 8192 bajty (1024 * 8)
                if (raf.length() < 8192) {
                    raf.seek(0)
                    raf.write(ByteArray(8192)) // Wypełniamy zerami
                }

                // Obliczamy lokalny indeks chunka w regionie (0..1023)
                // x & 31 to modulo 32
                val localX = if (chunk.x < 0) (chunk.x % 32 + 32) % 32 else chunk.x % 32
                val localZ = if (chunk.z < 0) (chunk.z % 32 + 32) % 32 else chunk.z % 32
                val chunkIndex = localX + (localZ * 32)

                // Nagłówek regionu: 1024 wpisy * 8 bajtów (4 bajty offset + 4 bajty długość)
                // Offset to pozycja w pliku, gdzie zaczynają się dane chunka.
                val headerOffset = chunkIndex * 8
                raf.seek(headerOffset.toLong())
                
                val currentOffset = raf.readInt()
                val currentLength = raf.readInt()

                val newDataLength = compressedData.size

                // Strategia zapisu:
                // 1. Jeśli chunk nie istniał (offset 0) -> Dopisz na koniec pliku.
                // 2. Jeśli chunk istniał, ale nowe dane są większe niż stare miejsce -> Dopisz na koniec pliku (stare miejsce staje się "śmieciem").
                // 3. Jeśli chunk istniał i nowe dane się mieszczą -> Nadpisz w starym miejscu.

                var writeOffset = currentOffset

                if (currentOffset == 0 || newDataLength > currentLength) {
                    // Dopisz na koniec
                    writeOffset = raf.length().toInt()
                    // Aktualizuj nagłówek
                    raf.seek(headerOffset.toLong())
                    raf.writeInt(writeOffset)
                    raf.writeInt(newDataLength)
                } else {
                    // Nadpisz (aktualizuj tylko długość w nagłówku, offset bez zmian)
                    raf.seek((headerOffset + 4).toLong())
                    raf.writeInt(newDataLength)
                }

                // Zapisz właściwe dane
                raf.seek(writeOffset.toLong())
                raf.write(compressedData)
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    open fun loadChunk(cx: Int, cz: Int, dimension: String = "overworld"): Chunk? {
        val regionX = cx shr 5
        val regionZ = cz shr 5
        val regionFile = File(saveDir, "dimensions/$dimension/regions/r_${regionX}_${regionZ}.rgn")

        if (!regionFile.exists()) return null

        try {
            RandomAccessFile(regionFile, "r").use { raf ->
                // FIX: Jeśli plik jest krótszy niż nagłówek, to jest nieprawidłowy/pusty
                if (raf.length() < 8192) return null

                val localX = if (cx < 0) (cx % 32 + 32) % 32 else cx % 32
                val localZ = if (cz < 0) (cz % 32 + 32) % 32 else cz % 32
                val chunkIndex = localX + (localZ * 32)

                raf.seek((chunkIndex * 8).toLong())
                val offset = raf.readInt()
                val length = raf.readInt()

                if (offset == 0 || length <= 0) return null // Chunk nie istnieje w tym regionie

                val data = ByteArray(length)
                raf.seek(offset.toLong())
                raf.readFully(data)

                return ChunkCompressor.decompress(data, cx, cz)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    // --- Helper Object do Kompresji (Palette + RLE) ---
    object ChunkCompressor {
        fun compress(chunk: Chunk): ByteArray {
            val baos = ByteArrayOutputStream(4096)
            DataOutputStream(baos).use { dos ->
                // 1. Tworzenie Palety
                // Mapa: Kolor Bloku -> Indeks w palecie
                val paletteMap = LinkedHashMap<Int, Int>()
                val paletteList = ArrayList<Int>()
                
                // Dodajemy powietrze jako indeks 0 (zawsze)
                paletteMap[0] = 0
                paletteList.add(0)

                // Skanujemy chunk w poszukiwaniu unikalnych bloków
                for (block in chunk.blocks) {
                    if (!paletteMap.containsKey(block)) {
                        paletteMap[block] = paletteList.size
                        paletteList.add(block)
                    }
                }

                // Zapisujemy Paletę
                dos.writeInt(paletteList.size)
                for (color in paletteList) {
                    dos.writeInt(color)
                }

                // 2. Zapisujemy Bloki używając RLE na indeksach palety
                // Format RLE: [Count (Byte)][PaletteIndex (Short)]
                // Używamy Short dla indeksu, bo paleta może mieć > 256 kolorów (rzadko, ale możliwe)
                var currentRunCount = 0
                var currentPaletteIndex = -1

                for (block in chunk.blocks) {
                    val index = paletteMap[block]!!
                    
                    if (index == currentPaletteIndex && currentRunCount < 255) {
                        currentRunCount++
                    } else {
                        if (currentRunCount > 0) {
                            dos.writeByte(currentRunCount)
                            dos.writeShort(currentPaletteIndex)
                        }
                        currentRunCount = 1
                        currentPaletteIndex = index
                    }
                }
                // Zapisz ostatnią serię
                if (currentRunCount > 0) {
                    dos.writeByte(currentRunCount)
                    dos.writeShort(currentPaletteIndex)
                }

                // 3. Zapisujemy Metadane (Proste RLE)
                // Format: [Count (Byte)][MetaValue (Byte)]
                var currentMetaCount = 0
                var currentMetaValue = (-1).toByte()

                for (meta in chunk.metadata) {
                    if (meta == currentMetaValue && currentMetaCount < 255) {
                        currentMetaCount++
                    } else {
                        if (currentMetaCount > 0) {
                            dos.writeByte(currentMetaCount)
                            dos.writeByte(currentMetaValue.toInt())
                        }
                        currentMetaCount = 1
                        currentMetaValue = meta
                    }
                }
                if (currentMetaCount > 0) {
                    dos.writeByte(currentMetaCount)
                    dos.writeByte(currentMetaValue.toInt())
                }
            }
            return baos.toByteArray()
        }

        fun decompress(data: ByteArray, cx: Int, cz: Int): Chunk {
            val chunk = Chunk(cx, cz)
            DataInputStream(ByteArrayInputStream(data)).use { dis ->
                // 1. Wczytaj Paletę
                val paletteSize = dis.readInt()
                val palette = IntArray(paletteSize)
                for (i in 0 until paletteSize) {
                    palette[i] = dis.readInt()
                }

                // 2. Wczytaj Bloki (RLE)
                var blockIdx = 0
                val totalBlocks = chunk.blocks.size
                while (blockIdx < totalBlocks && dis.available() > 0) {
                    // Sprawdzamy czy to już sekcja metadanych?
                    // W tym prostym formacie zakładamy, że RLE bloków wypełni całą tablicę blocks.
                    // Jeśli dis.available() > 0 po wypełnieniu bloków, to reszta to metadane.
                    
                    val count = dis.readUnsignedByte()
                    val paletteIndex = dis.readShort().toInt() and 0xFFFF
                    val color = if (paletteIndex < paletteSize) palette[paletteIndex] else 0

                    for (i in 0 until count) {
                        if (blockIdx < totalBlocks) {
                            chunk.blocks[blockIdx++] = color
                        }
                    }
                }

                // 3. Wczytaj Metadane (RLE)
                var metaIdx = 0
                val totalMeta = chunk.metadata.size
                while (metaIdx < totalMeta && dis.available() > 0) {
                    val count = dis.readUnsignedByte()
                    val metaValue = dis.readByte()

                    for (i in 0 until count) {
                        if (metaIdx < totalMeta) {
                            chunk.metadata[metaIdx++] = metaValue
                        }
                    }
                }
            }
            chunk.modified = false
            // Ustaw flagę hasBlocks jeśli chunk nie jest pusty
            for(b in chunk.blocks) {
                if(b != 0) {
                    chunk.hasBlocks = true
                    break
                }
            }
            return chunk
        }
    }

    fun saveWorldData(data: WorldData) {
        try {
            val file = File(saveDir, "world.dat")
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { dos ->
                dos.writeInt(SAVE_MAGIC_HEADER)
                dos.writeInt(CURRENT_VERSION)
                dos.writeInt(data.seed)
                dos.writeDouble(data.x)
                dos.writeDouble(data.y)
                dos.writeDouble(data.z)
                dos.writeDouble(data.yaw)
                dos.writeDouble(data.pitch)

                // Funkcja pomocnicza do zapisu pola z nagłówkiem (Typ + Nazwa)
                fun writeField(name: String, type: Byte, value: Any) {
                    dos.writeByte(type.toInt())
                    dos.writeUTF(name)
                    when (value) {
                        is Boolean -> dos.writeBoolean(value)
                        is Int -> dos.writeInt(value)
                        is Double -> dos.writeDouble(value)
                        is String -> dos.writeUTF(value)
                    }
                }

                // Zapisujemy pola w dowolnej kolejności. Każde pole jest "otagowane".
                writeField("debugNoclip", TAG_BOOLEAN, data.debugNoclip)
                writeField("debugFly", TAG_BOOLEAN, data.debugFly)
                writeField("debugFullbright", TAG_BOOLEAN, data.debugFullbright)
                writeField("showChunkBorders", TAG_BOOLEAN, data.showChunkBorders)
                writeField("debugXray", TAG_BOOLEAN, data.debugXray)
                writeField("gameTime", TAG_DOUBLE, data.gameTime)
                writeField("dayCounter", TAG_INT, data.dayCounter)
                writeField("localDimension", TAG_STRING, data.localDimension)

                dos.writeByte(TAG_END.toInt()) // Znacznik końca danych
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun loadWorldData(): WorldData? {
        val file = File(saveDir, "world.dat")
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { dis ->
                // Sprawdzamy czy plik ma nowy nagłówek
                val firstInt = dis.readInt()
                var seed = 0
                var version = 0

                if (firstInt == SAVE_MAGIC_HEADER) {
                    version = dis.readInt()
                    seed = dis.readInt()
                } else {
                    seed = firstInt // To był seed, stary format (Legacy)
                }

                val x = dis.readDouble()
                val y = dis.readDouble()
                val z = dis.readDouble()
                val yaw = dis.readDouble()
                val pitch = dis.readDouble()

                var debugNoclip = false
                var debugFly = false
                var debugFullbright = false
                var showChunkBorders = false
                var debugXray = false
                var gameTime = 12.0
                var dayCounter = 0
                var localDimension = "overworld"

                try {
                    if (version >= 2) {
                        // Nowy system: Czytamy tagi w pętli
                        while (true) {
                            // Czytamy typ danych. Jeśli plik się urwie, poleci EOFException i pętla się skończy (bezpiecznie)
                            val type = dis.readByte()
                            if (type == TAG_END) break // Koniec danych

                            val name = dis.readUTF()

                            // Wczytujemy wartość zależnie od zapisanego typu
                            // Dzięki temu, nawet jak nie znamy pola "name", wiemy ile bajtów przeczytać, żeby nie zgubić pozycji w pliku!
                            val value: Any = when (type) {
                                TAG_BOOLEAN -> dis.readBoolean()
                                TAG_INT -> dis.readInt()
                                TAG_DOUBLE -> dis.readDouble()
                                TAG_STRING -> dis.readUTF()
                                else -> throw IOException("Nieznany typ danych: $type")
                            }

                            // Przypisujemy do zmiennych tylko to, co znamy
                            when (name) {
                                "debugNoclip" -> debugNoclip = value as Boolean
                                "debugFly" -> debugFly = value as Boolean
                                "debugFullbright" -> debugFullbright = value as Boolean
                                "showChunkBorders" -> showChunkBorders = value as Boolean
                                "debugXray" -> debugXray = value as Boolean
                                "gameTime" -> gameTime = value as Double
                                "dayCounter" -> dayCounter = value as Int
                                "localDimension" -> localDimension = value as String
                            }
                        }
                    } else {
                        debugNoclip = dis.readBoolean()
                    }
                } catch (e: EOFException) {
                    // Koniec pliku - normalne zachowanie
                }

                WorldData(seed, x, y, z, yaw, pitch, debugNoclip, debugFly, debugFullbright, showChunkBorders, debugXray, gameTime, dayCounter, localDimension)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}