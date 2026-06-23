package org.lewapnoob.KapeLuz

import java.awt.Color
import java.util.Random
import kotlin.math.floor
import kotlin.math.sqrt

data class Biome(
    val name: String,
    val surfaceColor: Int,
    val subsurfaceColor: Int,
    val baseHeight: Double,
    val heightVariation: Double,
    val treeDensity: Double,
    val rarityThreshold: Double = 0.0,
    val sizeScaleModifier: Double = 1.0
)

data class CaveBiome(
    val name: String,
    val wallColor: Int,
    val floorColor: Int,
    val minZone: Int = 0,    // 0: dół, 1: środek, 2: góra
    val maxZone: Int = 2,
    val rarity: Double = 0.5, // 0.0 - 1.0 (im więcej, tym częściej)
    val scale: Double = 1.0   // 1.0 to standard, <1.0 to większe biomy
)

object Biomes {
    private val GRASS_TEMPERATE = Color(0x59A608).rgb
    private val GRASS_COLD = Color(0x739C67).rgb
    private val GRASS_SAVANNA = Color(0xBFB75E).rgb
    private val DIRT = Color(0x6c3c0c).rgb
    private val SAND = Color(0xDBCE9E).rgb
    private val SNOW = Color(0xFFFFFF).rgb
    private val STONE = Color(0x8EA3A1).rgb

    // Biomy Jaskiniowe
    val DEFAULT_CAVE = CaveBiome("Deep Caves", STONE, STONE, 0, 2, 1.0, 1.0)
    val LUSH_CAVE = CaveBiome("Lush Caves", Color(0x3B5905).rgb, Color(0x59A608).rgb, 1, 2, 0.4, 1.2)
    val DRIPSTONE_CAVE = CaveBiome("Dripstone", Color(0x4D3826).rgb, Color(0x4D3826).rgb, 0, 1, 0.4, 1.0)
    val DARK_CAVE = CaveBiome("Dark Cave", Color(0x240A34).rgb , Color(0x240A34).rgb, 0, 0, 0.6, 0.4) // Tylko dół, skala 0.4 = wielkie obszary

    // 10 Głównych biomów
    val SNOWY_TUNDRA = Biome("Snowy Tundra", SNOW, DIRT, 58.0, 2.0, 0.0001, rarityThreshold = 0.0)
    val SNOWY_TAIGA = Biome("Snowy Taiga", SNOW, DIRT, 62.0, 5.0, 0.02, rarityThreshold = 0.0)
    val MOUNTAIN_MEADOW = Biome("Mountain Meadow", GRASS_COLD, DIRT, 78.0, 12.0, 0.001, rarityThreshold = 0.1)
    val MOUNTAIN_TAIGA = Biome("Mountain Taiga", GRASS_COLD, DIRT, 82.0, 15.0, 0.025, rarityThreshold = 0.15)
    val PLAINS = Biome("Plains", GRASS_TEMPERATE, DIRT, 57.0, 3.0, 0.0005, rarityThreshold = 0.0)
    val FOREST = Biome("Forest", GRASS_TEMPERATE, DIRT, 60.0, 6.0, 0.03, rarityThreshold = 0.0)
    val DESERT = Biome("Desert", SAND, SAND, 56.0, 2.0, 0.0, rarityThreshold = 0.0)
    val SAVANNA = Biome("Savanna", GRASS_SAVANNA, DIRT, 63.0, 7.0, 0.005, rarityThreshold = 0.0)
    val BEACH = Biome("Beach", SAND, SAND, 51.0, 1.0, 0.0, rarityThreshold = 0.0)
    val OCEAN = Biome("Ocean", DIRT, DIRT, 38.0, 4.0, 0.0, rarityThreshold = 0.0)

    val MINI_FOREST = Biome("Mini Forest", GRASS_TEMPERATE, DIRT, 60.0, 5.0, 0.05, rarityThreshold = 0.4, sizeScaleModifier = 2.5)
    val RARE_MOUNTAIN_PEAK = Biome("Rare Mountain Peak", STONE, STONE, 95.0, 8.0, 0.0, rarityThreshold = 0.8, sizeScaleModifier = 1.5)
}

class BiomeProvider(val seed: Int) {
    private val temperatureNoise = PerlinNoise(seed + 10)
    private val moistureNoise = PerlinNoise(seed + 20)
    private val continentalnessNoise = PerlinNoise(seed + 30)
    private val rarityNoise = PerlinNoise(seed + 40)

    private val baseClimateScale = 0.001
    private val baseContinentalScale = 0.001
    private val baseRarityScale = 0.0014

    private val caveBiomeNoise = PerlinNoise(seed + 50)
    private val caveClimateScale = 0.02

    fun getBiome(wx: Int, wz: Int): Biome {
        val globalTemp = temperatureNoise.noise(wx * baseClimateScale, wz * baseClimateScale)
        val globalMoisture = moistureNoise.noise(wx * baseClimateScale, wz * baseClimateScale)
        val globalElevation = continentalnessNoise.noise(wx * baseContinentalScale, wz * baseContinentalScale)
        val globalRarity = rarityNoise.noise(wx * baseRarityScale, wz * baseRarityScale)

        if (globalElevation < -0.4) return Biomes.OCEAN
        if (globalElevation < -0.3) return Biomes.BEACH

        if (globalElevation > 0.4 && (globalRarity + 0.5) / 1.0 > Biomes.RARE_MOUNTAIN_PEAK.rarityThreshold) {
            return Biomes.RARE_MOUNTAIN_PEAK
        }

        return when {
            globalTemp < -0.3 -> {
                if (globalMoisture > 0.0) Biomes.SNOWY_TAIGA else Biomes.SNOWY_TUNDRA
            }
            globalTemp < 0.0 -> {
                val normalizedRarity = (globalRarity + 0.7) / 1.4
                if (normalizedRarity > Biomes.MOUNTAIN_TAIGA.rarityThreshold && globalMoisture > -0.1) {
                    Biomes.MOUNTAIN_TAIGA
                } else if (normalizedRarity > Biomes.MOUNTAIN_MEADOW.rarityThreshold) {
                    Biomes.MOUNTAIN_MEADOW
                } else {
                    Biomes.SNOWY_TUNDRA
                }
            }
            globalTemp < 0.4 -> {
                val normalizedRarity = (globalRarity + 0.7) / 1.4
                if (normalizedRarity > Biomes.MINI_FOREST.rarityThreshold) {
                    val localScale = baseClimateScale * Biomes.MINI_FOREST.sizeScaleModifier
                    val localMoisture = moistureNoise.noise(wx * localScale, wz * localScale)
                    if (localMoisture > 0.1) return Biomes.MINI_FOREST
                }
                if (globalMoisture > 0.0) Biomes.FOREST else Biomes.PLAINS
            }
            else -> {
                if (globalMoisture > -0.1) Biomes.SAVANNA else Biomes.DESERT
            }
        }
    }

    fun getCaveBiome(wx: Int, wy: Int, wz: Int, zone: Int): CaveBiome {
        val candidates = listOf(Biomes.DARK_CAVE, Biomes.LUSH_CAVE, Biomes.DRIPSTONE_CAVE)
            .filter { zone >= it.minZone && zone <= it.maxZone }

        var bestBiome = Biomes.DEFAULT_CAVE
        var maxScore = -1.0

        for (biome in candidates) {
            // Używamy skali biomu - mniejsza skala = rzadsze zmiany = większy biom
            val n = caveBiomeNoise.noise(wx * caveClimateScale * biome.scale, wy * caveClimateScale * biome.scale, wz * caveClimateScale * biome.scale)
            
            val threshold = 1.0 - (biome.rarity * 2.0) // Przeliczamy rzadkość na próg szumu
            if (n > threshold) {
                val score = n - threshold
                if (score > maxScore) {
                    maxScore = score
                    bestBiome = biome
                }
            }
        }
        return bestBiome
    }
}

open class ChunkGenerator(
    val seed: Int,
    val oreColors: MutableSet<Int>
) {
    val noise = PerlinNoise(seed)
    val biomeProvider = BiomeProvider(seed)

    val BLOCK_ID_AIR = 0
    val BLOCK_ID_LIGHT = 2
    val BLOCK_ID_LAVA = 3
    val BLOCK_ID_WATER = 4
    val SEA_LEVEL = 50

    val treeModel = treeModelData
    val DungeonModel = DungeonModelData
    val IglooModel = IglooModelData

    open fun generate(cx: Int, cz: Int): Chunk {
        val chunk = Chunk(cx, cz)

        // 1. GENERACJA POWIERZCHNI
        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz
                val biome = biomeProvider.getBiome(wx, wz)
                val h = getTerrainHeight(wx, wz)

                chunk.setBlock(lx, 0, lz, Color.BLACK.rgb)
                for (y in 1..127) {
                    val block = getSurfaceBlock(y, h, biome)
                    if (block != BLOCK_ID_AIR) {
                        chunk.setBlock(lx, y, lz, block)
                    }
                }
            }
        }

        // 2. GENERACJA JASKIŃ (Nowy silnik czysto algorytmiczny)
        carveCaves(chunk, cx, cz)

        // 3. Generowanie dodatków
        generateOres(chunk, cx, cz)
        generateLavaLakes(chunk, cx, cz)
        generateBiomeStructures(chunk, cx, cz)
        generateStructureType(chunk, cx, cz, DungeonModel, 0.0001, 0, 30, Color(0x8EA3A1).rgb, 1, true, listOf(0, 90, 180, 270), false)
        generateStructureType(chunk, cx, cz, IglooModel, 0.00005, 52, 80, Biomes.SNOWY_TUNDRA.surfaceColor, 0, false, listOf(0, 90, 180, 270), true)

        chunk.modified = false
        return chunk
    }

    open fun getTerrainHeight(wx: Int, wz: Int): Int {
        val radius = 8
        var totalBaseHeight = 0.0
        var totalHeightVariation = 0.0
        var weightSum = 0.0

        for (dx in -radius..radius step radius) {
            for (dz in -radius..radius step radius) {
                val b = biomeProvider.getBiome(wx + dx, wz + dz)
                val weight = 1.0 / (sqrt((dx * dx + dz * dz).toDouble()) + 1.0)
                totalBaseHeight += b.baseHeight * weight
                totalHeightVariation += b.heightVariation * weight
                weightSum += weight
            }
        }

        val blendedBaseHeight = totalBaseHeight / weightSum
        val blendedVariation = totalHeightVariation / weightSum

        val n = noise.noise(wx * 0.02, wz * 0.02)
        val calculatedHeight = blendedBaseHeight + (n * blendedVariation)
        return calculatedHeight.toInt().coerceIn(0, 127)
    }

    private fun getSurfaceBlock(wy: Int, terrainHeight: Int, biome: Biome): Int {
        if (wy > terrainHeight) {
            return if (wy <= SEA_LEVEL) BLOCK_ID_WATER else BLOCK_ID_AIR
        }
        val stoneDepth = 4
        return when {
            wy == terrainHeight -> if (terrainHeight >= SEA_LEVEL) biome.surfaceColor else biome.subsurfaceColor
            wy > terrainHeight - stoneDepth -> biome.subsurfaceColor
            else -> Color(0x8EA3A1).rgb
        }
    }

    /**
     * Zwiększamy promień wyszukiwania jaskiń do 7 chunków (standard dla dobrej topologii),
     * aby tunele miały odpowiedni bufor na naturalne zakończenie biegu.
     */
    private fun carveCaves(chunk: Chunk, cx: Int, cz: Int) {
        val range = 5 // Balans: wystarczająco dużo, by widzieć systemy, ale nie całą mapę

        for (szumX in cx - range..cx + range) {
            for (szumZ in cz - range..cz + range) {
                val rand = java.util.Random((szumX * 341873128712L + szumZ * 132897987541L + seed).hashCode().toLong())

                // Zwiększamy nieco szansę na start kolumny jaskiń
                val numSystems = if (rand.nextInt(100) < 18) rand.nextInt(3) + 1 else 0

                repeat(numSystems) {
                    val anchorX = (szumX * 16 + rand.nextInt(16)).toDouble()
                    val anchorZ = (szumZ * 16 + rand.nextInt(16)).toDouble()

                    // Dynamika wysokości oparta na terenie
                    val surfaceH = getTerrainHeight(anchorX.toInt(), anchorZ.toInt())
                    val bedrockY = 8.0
                    val totalPlayableDepth = surfaceH - bedrockY
                    val zoneSize = totalPlayableDepth / 3.0

                    // Losujemy strefę dla tego konkretnego systemu
                    val zone = rand.nextInt(3) // 0: Bottom, 1: Middle, 2: Top
                    
                    val minY = bedrockY + (zone * zoneSize)
                    val maxY = minY + zoneSize
                    val anchorY = minY + rand.nextDouble() * (maxY - minY)

                    // --- PARAMETRYZACJA STREF ---
                    var cheeseChance = 0
                    var cheeseSizeMult = 1.0
                    var forceDownwards = false

                    when (zone) {
                        0 -> { cheeseChance = 7; cheeseSizeMult = 1.6 } // Dół: Bardzo duże i częste jaskinie
                        1 -> { cheeseChance = 3; cheeseSizeMult = 0.8 } // Środek: Mniejsze i rzadsze
                        2 -> { cheeseChance = 0; forceDownwards = true } // Góra: Brak wielkich komór, tylko tunele
                    }

                    // GENERACJA CHEESE CAVES (Duże komory)
                    if (cheeseChance > 0 && rand.nextInt(10) < cheeseChance) {
                        val baseRoomRadius = (rand.nextDouble() * 5.0 + 5.0) * cheeseSizeMult
                        val subSpheres = rand.nextInt(3) + 3

                        repeat(subSpheres) {
                            val dx = (rand.nextDouble() - 0.5) * baseRoomRadius * 0.7
                            val dy = (rand.nextDouble() - 0.5) * baseRoomRadius * 0.4
                            val dz = (rand.nextDouble() - 0.5) * baseRoomRadius * 0.7
                            val r = baseRoomRadius * (0.8 + rand.nextDouble() * 0.4)

                            carveStructuralSphere(chunk, cx, cz, anchorX + dx, anchorY + dy, anchorZ + dz, r, 1.35, zone)
                        }
                    }

                    // GENERACJA SPAGHETTI CAVES (Tunele)
                    val spaghettiCount = if (zone == 2) rand.nextInt(2) + 1 else rand.nextInt(2) + 1
                    repeat(spaghettiCount) {
                        // Jeśli to strefa górna, wymuszamy pitch w dół na starcie
                        val startPitch = if (forceDownwards) -0.5f - (rand.nextFloat() * 0.5f) else null
                        generateAndCarveTunnel(chunk, cx, cz, anchorX, anchorY, anchorZ, isNoodle = false, rand, range, startPitch, zone)
                    }
                }
            }
        }
    }

    /**
     * Zmodernizowana metoda wektorowa z wbudowaną kontrolą geometrii brzegowej.
     */
    private fun generateAndCarveTunnel(chunk: Chunk, cx: Int, cz: Int, startX: Double, startY: Double, startZ: Double, isNoodle: Boolean, rand: Random, range: Int, overridePitch: Float? = null, zone: Int) {
        var px = startX
        var py = startY
        var pz = startZ

        var yaw = rand.nextFloat() * Math.PI.toFloat() * 2.0f
        var pitch = overridePitch ?: ((rand.nextFloat() - 0.5f) * 0.25f)

        val steps = if (isNoodle) rand.nextInt(25) + 15 else rand.nextInt(50) + 40 // Skrócone tunele
        val baseRadius = if (isNoodle) 1.2 else 2.8 // Nieco węższe główne tunele
        val stepLength = if (isNoodle) 1.2 else 2.2 // Krótsze kroki to lepsza kontrola

        var prevX = px
        var prevY = py
        var prevZ = pz
        var prevR = baseRadius

        // Maksymalny bezpieczny dystans od źródła, zanim uderzymy w horyzont generowania chunków
        val maxAllowedDistance = (range - 1) * 16.0

        for (step in 0 until steps) {
            // 1. Bazowy promień z fluktuacją
            var currentRadius = baseRadius + (Math.sin(step * 0.25) * (if (isNoodle) 0.15 else 0.7))

            // 2. WYGŁADZANIE STARTU (Lejek dla skrzyżowań T-Kształtnych)
            if (step < 8) {
                val t = step / 8.0
                currentRadius *= (2.0 - t)
            }

            // 3. WYGŁADZANIE KOŃCA (Zabezpieczenie przed tępą ścianą na końcu pętli)
            if (step > steps - 8) {
                val t = (steps - step) / 8.0 // Płynne schodzenie od 1.0 do 0.0
                currentRadius *= t
            }

            // 4. BEZPIECZNIK CHUNKÓW (Zabezpieczenie przed brutalnym ucięciem na granicy zasięgu)
            val distFromOriginX = px - startX
            val distFromOriginZ = pz - startZ
            val currentDist = sqrt(distFromOriginX * distFromOriginX + distFromOriginZ * distFromOriginZ)
            val fadeBuffer = 12.0 // Mniejszy bufor wygaszania

            if (currentDist > maxAllowedDistance - fadeBuffer) {
                // Gdy tunel zbliża się do krawędzi zasięgu na odległość fadeBuffer bloków,
                // zaczynamy go drastycznie zwężać, wymuszając naturalne zamknięcie jaskini.
                val t = ((maxAllowedDistance - currentDist) / fadeBuffer).coerceIn(0.0, 1.0)
                currentRadius *= t
            }

            // Interpolacja sub-krokowa segmentu
            val segmentDist = sqrt((px - prevX) * (px - prevX) + (py - prevY) * (py - prevY) + (pz - prevZ) * (pz - prevZ))
            val subSteps = floor(segmentDist * 1.5).toInt().coerceAtLeast(1)

            for (i in 0 until subSteps) {
                val alpha = i.toDouble() / subSteps
                val ix = prevX + (px - prevX) * alpha
                val iy = prevY + (py - prevY) * alpha
                val iz = prevZ + (pz - prevZ) * alpha
                val ir = prevR + (currentRadius - prevR) * alpha

                if (ir > 0.1) { // Rzeźbimy tylko gdy promień ma sensowną wielkość
                    carveStructuralSphere(chunk, cx, cz, ix, iy, iz, ir, if (isNoodle) 1.0 else 1.2, zone)
                }
            }

            // NOODLE CAVES (Odnogi szczelinowe)
            if (!isNoodle && step > 12 && rand.nextInt(40) == 0 && currentRadius > 1.5) {
                generateAndCarveTunnel(chunk, cx, cz, px, py, pz, isNoodle = true, rand = rand, range = range, zone = zone)
            }

            // Przejście wektora
            prevX = px
            prevY = py
            prevZ = pz
            prevR = currentRadius

            val hDist = Math.cos(pitch.toDouble())
            px += Math.cos(yaw.toDouble()) * hDist * stepLength
            py += Math.sin(pitch.toDouble()) * stepLength
            pz += Math.sin(yaw.toDouble()) * hDist * stepLength

            pitch *= 0.6f // Szybsze prostowanie w pionie
            pitch += (rand.nextFloat() - rand.nextFloat()) * 0.25f
            yaw += (rand.nextFloat() - rand.nextFloat()) * 0.45f // Zwiększona krętość (było 0.35)

            if (py <= 5.0 || py >= 120.0) break
        }
    }

    /**
     * Rdzeń rzeźbiący kule/elipsoidy wewnątrz aktualnego chunka.
     * Szum podziemny jest próbkowany oszczędnie: raz na całą kolumnę pionową geometrii.
     */
    private fun carveStructuralSphere(chunk: Chunk, currentCx: Int, currentCz: Int, centerX: Double, centerY: Double, centerZ: Double, radius: Double, yFlattening: Double, zone: Int) {
        val stoneColor = Color(0x8EA3A1).rgb

        val minX = floor(centerX - radius).toInt() - currentCx * 16
        val maxX = floor(centerX + radius).toInt() - currentCx * 16
        val minY = floor(centerY - radius / yFlattening).toInt()
        val maxY = floor(centerY + radius / yFlattening).toInt()
        val minZ = floor(centerZ - radius).toInt() - currentCz * 16
        val maxZ = floor(centerZ + radius).toInt() - currentCz * 16

        val radiusSq = radius * radius

        for (lx in minX..maxX) {
            if (lx !in 0..15) continue
            val wx = currentCx * 16 + lx

            for (lz in minZ..maxZ) {
                if (lz !in 0..15) continue
                val wz = currentCz * 16 + lz

                val biome = biomeProvider.getBiome(wx, wz)
                val baseCaveBiome = biomeProvider.getCaveBiome(wx, centerY.toInt().coerceIn(1, 126), wz, zone)

                for (y in minY..maxY) {
                    if (y <= 0 || y >= 127) continue

                    val dx = (lx + currentCx * 16) - centerX
                    val dz = (lz + currentCz * 16) - centerZ
                    val dy = (y - centerY) * yFlattening

                    if (dx * dx + dy * dy + dz * dz < radiusSq) {
                        val currentBlock = chunk.getBlock(lx, y, lz)

                        val isCarvable = currentBlock == stoneColor ||
                                currentBlock == biome.surfaceColor ||
                                currentBlock == biome.subsurfaceColor ||
                                currentBlock == baseCaveBiome.floorColor ||
                                currentBlock == baseCaveBiome.wallColor ||
                                currentBlock == BLOCK_ID_AIR

                        if (isCarvable && currentBlock != BLOCK_ID_WATER) {
                            chunk.setBlock(lx, y, lz, BLOCK_ID_AIR)

                            // Podłoga (Zabezpieczenie przed malowaniem w pustej przestrzeni)
                            if (y > 1 && dx * dx + ((y - 1 - centerY) * yFlattening) * ((y - 1 - centerY) * yFlattening) + dz * dz >= radiusSq) {
                                val below = chunk.getBlock(lx, y - 1, lz)
                                if (below != BLOCK_ID_AIR && below != BLOCK_ID_WATER) {
                                    chunk.setBlock(lx, y - 1, lz, baseCaveBiome.floorColor)
                                }
                            }

                            // Sufit
                            if (y < 126 && dx * dx + ((y + 1 - centerY) * yFlattening) * ((y + 1 - centerY) * yFlattening) + dz * dz >= radiusSq) {
                                val above = chunk.getBlock(lx, y + 1, lz)
                                if (above != BLOCK_ID_AIR && above != BLOCK_ID_WATER) {
                                    if (baseCaveBiome == Biomes.DARK_CAVE) {
                                        // Determinystyczny szum dla rzadkich świecących nacieków na suficie
                                        val dripHash = (wx * 341873128712L + (y + 1) * 132897987541L + wz * 73856093L + seed).hashCode()
                                        val dripVal = if (dripHash < 0) -dripHash else dripHash
                                        
                                        if (dripVal % 1000 == 0) { // Ok. 0.8% szansy na blok sufitu
                                            chunk.setBlock(lx, y + 1, lz, 2)
                                        } else {
                                            chunk.setBlock(lx, y + 1, lz, baseCaveBiome.wallColor)
                                        }
                                    } else {
                                        chunk.setBlock(lx, y + 1, lz, baseCaveBiome.wallColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    open fun generateBiomeStructures(chunk: Chunk, cx: Int, cz: Int) {
        val margin = 10
        val modelCache = mutableMapOf<Int, List<ModelVoxel>>()

        for (lx in -margin..16 + margin) {
            for (lz in -margin..16 + margin) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz
                val biome = biomeProvider.getBiome(wx, wz)

                if (biome.treeDensity > 0.0 && isStructureAt(wx, wz, biome.treeDensity, treeModel.hashCode())) {
                    val h = getTerrainHeight(wx, wz)

                    if (h >= SEA_LEVEL && getSurfaceBlock(h, h, biome) == biome.surfaceColor) {
                        val rotation = 0
                        val finalModel = modelCache.getOrPut(rotation) { rotateModel(treeModel, rotation) }
                        placeStructure(chunk, lx, h + 1, lz, finalModel, false)
                    }
                }
            }
        }
    }

    open fun generateOres(chunk: Chunk, cx: Int, cz: Int) {
        val rand = Random((cx * 341873128712L + cz * 132897987541L + seed).hashCode().toLong())
        val targetBlock = Color(0x8EA3A1).rgb

        generateOreType(chunk, rand, targetBlock, Color(0x151716).rgb, 1, 10, 20, 64, 32)
        generateOreType(chunk, rand, targetBlock, Color(0xe3c0aa).rgb, 1, 5, 1, 50, 13)
        generateOreType(chunk, rand, targetBlock, Color(0x30ddeb).rgb, 1, 4, 1, 16, 5)
    }

    open fun generateOreType(chunk: Chunk, rand: Random, target: Int, color: Int, minSize: Int, maxSize: Int, minY: Int, maxY: Int, maxVeins: Int) {
        oreColors.add(color)

        val veinsCount = rand.nextInt(maxVeins + 1)
        for (i in 0 until veinsCount) {
            val startX = rand.nextInt(16)
            val startZ = rand.nextInt(16)
            val startY = minY + rand.nextInt(maxY - minY + 1)

            if (chunk.getBlock(startX, startY, startZ) == target) {
                val size = minSize + rand.nextInt(maxSize - minSize + 1)
                placeOreVein(chunk, rand, startX, startY, startZ, size, target, color, minY, maxY)
            }
        }
    }

    open fun placeOreVein(chunk: Chunk, rand: Random, x: Int, y: Int, z: Int, size: Int, target: Int, color: Int, minY: Int, maxY: Int) {
        val vein = java.util.ArrayList<BlockPos>()
        vein.add(BlockPos(x, y, z))
        chunk.setBlock(x, y, z, color)

        var currentSize = 1
        var attempts = 0
        while (currentSize < size && attempts < size * 4) {
            attempts++
            val source = vein[rand.nextInt(vein.size)]

            val dir = rand.nextInt(6)
            var dx = 0; var dy = 0; var dz = 0
            when(dir) {
                0 -> dx = 1; 1 -> dx = -1
                2 -> dy = 1; 3 -> dy = -1
                4 -> dz = 1; 5 -> dz = -1
            }

            val nx = source.x + dx
            val ny = source.y + dy
            val nz = source.z + dz

            if (nx in 0..15 && nz in 0..15 && ny in minY..maxY) {
                if (chunk.getBlock(nx, ny, nz) == target) {
                    chunk.setBlock(nx, ny, nz, color)
                    vein.add(BlockPos(nx, ny, nz))
                    currentSize++
                }
            }
        }
    }

    open fun generateLavaLakes(chunk: Chunk, cx: Int, cz: Int) {
        for (lx in -8..23) {
            for (lz in -8..23) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz

                if (isLakeCenter(wx, wz)) {
                    val rand = Random((wx * 341873128712L + wz * 132897987541L + seed).hashCode().toLong())
                    // Zwiększamy zakres poszukiwań pionowych, by jeziora częściej trafiały na dno jaskiń
                    val surfaceY = rand.nextInt(20) + 8
                    val radius = rand.nextDouble() * 2.5 + 2.0 // Nieco mniejsze, bardziej naturalne
                    val maxDepth = rand.nextDouble() * 1.5 + 1.5
                    placeLake(chunk, lx, surfaceY, lz, radius, maxDepth)
                }
            }
        }
    }

    open fun isLakeCenter(wx: Int, wz: Int): Boolean {
        val hash = (wx * 73856093 xor wz * 19349663 xor seed).toString().hashCode()
        val random = Random(hash.toLong())
        return random.nextDouble() < 0.0012 // Nieco częściej, bo niektóre zostaną anulowane przez brak gruntu
    }

    open fun placeLake(chunk: Chunk, centerLx: Int, surfaceY: Int, centerLz: Int, radius: Double, maxDepth: Double) {
        val stoneColor = Color(0x8EA3A1).rgb

        // 1. Grawitacja: szukamy podłoża dla jeziora
        val checkLx = centerLx.coerceIn(0, 15)
        val checkLz = centerLz.coerceIn(0, 15)
        var floorY = surfaceY

        // Skanujemy w dół w poszukiwaniu stałego bloku
        while (floorY > 5 && chunk.getBlock(checkLx, floorY, checkLz) == BLOCK_ID_AIR) {
            floorY--
        }

        // Jeśli jezioro wisi nad przepaścią lub trafiło na wodę - rezygnujemy z generacji
        val blockBelow = chunk.getBlock(checkLx, floorY, checkLz)
        if (blockBelow == BLOCK_ID_AIR || blockBelow == BLOCK_ID_WATER) return

        val lakeLevelY = floorY
        val margin = 4.0
        val minX = (centerLx - radius - margin).toInt().coerceIn(0, 15)
        val maxX = (centerLx + radius + margin).toInt().coerceIn(0, 15)
        val minZ = (centerLz - radius - margin).toInt().coerceIn(0, 15)
        val maxZ = (centerLz + radius + margin).toInt().coerceIn(0, 15)
        val minY = (lakeLevelY - maxDepth - 2).toInt().coerceIn(1, 127)
        val maxY = (lakeLevelY + 2).toInt().coerceIn(1, 127)

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val dx = x - centerLx
                val dz = z - centerLz
                val distSq = dx * dx + dz * dz
                val dist = sqrt(distSq.toDouble())

                val wx = chunk.x * 16 + x
                val wz = chunk.z * 16 + z

                // Bardziej postrzępiony kształt
                val shapeNoise = noise.noise(wx * 0.15, wz * 0.15) * 2.5
                val effectiveRadius = radius + shapeNoise
                val wallRadius = effectiveRadius + 1.2

                // Zróżnicowana głębokość dna
                val depthNoise = noise.noise(wx * 0.4, wz * 0.4)
                val localDepth = (maxDepth + depthNoise * 1.5).coerceAtLeast(1.0)

                for (y in minY..maxY) {
                    if (dist < effectiveRadius) {
                        // Wypełnienie lawą (płaska tafla na poziomie gruntu)
                        if (y <= lakeLevelY && y > lakeLevelY - localDepth) {
                            chunk.setBlock(x, y, z, BLOCK_ID_LAVA)
                            chunk.setMeta(x, y, z, 8)
                        }
                        // Mała nisza powietrzna nad taflą jeziora (by nie było "wmurowane")
                        else if (y > lakeLevelY && y <= lakeLevelY + 1) {
                            chunk.setBlock(x, y, z, BLOCK_ID_AIR)
                        }
                        // Uszczelnienie dna kamieniem
                        else if (y <= lakeLevelY - localDepth && y >= lakeLevelY - localDepth - 1) {
                            if (chunk.getBlock(x, y, z) != BLOCK_ID_AIR) chunk.setBlock(x, y, z, stoneColor)
                        }
                    }
                    // Brzegi i ściany jeziorka (wymuszenie kamienia w pustych przestrzeniach)
                    else if (dist < wallRadius) {
                        if (y <= lakeLevelY && y >= lakeLevelY - localDepth) {
                            if (chunk.getBlock(x, y, z) == BLOCK_ID_AIR) {
                                chunk.setBlock(x, y, z, stoneColor)
                            }
                        }
                    }
                }
            }
        }
    }

    open fun generateStructureType(chunk: Chunk, cx: Int, cz: Int, model: List<ModelVoxel>, density: Double, minH: Int, maxH: Int, targetBlock: Int, yOffset: Int, clearSpace: Boolean = false, allowedRotations: List<Int> = listOf(0), requireAirAbove: Boolean = true) {
        val margin = 10
        val modelCache = mutableMapOf<Int, List<ModelVoxel>>()

        for (lx in -margin..16 + margin) {
            for (lz in -margin..16 + margin) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz

                if (isStructureAt(wx, wz, density, model.hashCode())) {
                    val validYs = mutableListOf<Int>()
                    val startY = maxOf(minH, 0)
                    val endY = minOf(maxH, 127)

                    val biome = biomeProvider.getBiome(wx, wz)
                    val h = getTerrainHeight(wx, wz)

                    for (y in startY..endY) {
                        val isTarget = getSurfaceBlock(y, h, biome) == targetBlock
                        if (isTarget && (!requireAirAbove || getSurfaceBlock(y + 1, h, biome) == BLOCK_ID_AIR)) {
                            validYs.add(y)
                        }
                    }

                    if (validYs.isNotEmpty()) {
                        val hash = (wx * 73856093 xor wz * 19349663 xor seed xor model.hashCode()).toString().hashCode()
                        val random = Random(hash.toLong())
                        random.nextDouble()

                        val selectedY = validYs[random.nextInt(validYs.size)]
                        val rotation = if (allowedRotations.isNotEmpty()) allowedRotations[random.nextInt(allowedRotations.size)] else 0
                        val finalModel = modelCache.getOrPut(rotation) { rotateModel(model, rotation) }

                        placeStructure(chunk, lx, selectedY + yOffset, lz, finalModel, clearSpace)
                    }
                }
            }
        }
    }

    open fun rotateModel(model: List<ModelVoxel>, angle: Int): List<ModelVoxel> {
        var normAngle = angle % 360
        if (normAngle < 0) normAngle += 360
        val steps = (normAngle / 90) % 4
        if (steps == 0) return model
        return model.map { voxel ->
            var x = voxel.x
            var z = voxel.z
            repeat(steps) {
                val oldX = x
                val oldZ = z
                x = -oldZ
                z = oldX
            }
            ModelVoxel(x, voxel.y, z, voxel.color, voxel.isVoid)
        }
    }

    open fun isStructureAt(wx: Int, wz: Int, density: Double, salt: Int): Boolean {
        val hash = (wx * 73856093 xor wz * 19349663 xor seed xor salt).toString().hashCode()
        val random = Random(hash.toLong())
        return random.nextDouble() < density
    }

    open fun placeStructure(chunk: Chunk, rootLx: Int, rootY: Int, rootLz: Int, model: List<ModelVoxel>, clearSpace: Boolean) {
        if (clearSpace && model.isNotEmpty()) {
            val minX = model.minOf { it.x }
            val maxX = model.maxOf { it.x }
            val minY = model.minOf { it.y }
            val maxY = model.maxOf { it.y }
            val minZ = model.minOf { it.z }
            val maxZ = model.maxOf { it.z }

            val voxelMap = model.associate { Triple(it.x, it.y, it.z) to it }

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        val tx = rootLx + x
                        val ty = rootY + y
                        val tz = rootLz + z

                        if (tx in 0 until 16 && tz in 0 until 16 && ty in 0 until 128) {
                            val voxel = voxelMap[Triple(x, y, z)]
                            if (voxel != null) {
                                if (!voxel.isVoid) {
                                    chunk.setBlock(tx, ty, tz, voxel.color.rgb)
                                }
                            } else {
                                chunk.setBlock(tx, ty, tz, 0)
                            }
                        }
                    }
                }
            }
        } else {
            for (voxel in model) {
                val tx = rootLx + voxel.x
                val ty = rootY + voxel.y
                val tz = rootLz + voxel.z

                if (tx in 0 until 16 && tz in 0 until 16 && ty in 0 until 128) {
                    if (voxel.isVoid) {
                        chunk.setBlock(tx, ty, tz, 0)
                    } else {
                        chunk.setBlock(tx, ty, tz, voxel.color.rgb)
                    }
                }
            }
        }
    }
}

class PerlinNoise(seed: Int) {
    val p = IntArray(512)
    init {
        val random = Random(seed.toLong())
        val permutation = (0..255).toMutableList()
        permutation.shuffle(random)
        for (i in 0..255) {
            p[i] = permutation[i]
            p[i + 256] = permutation[i]
        }
    }

    fun noise(x: Double, y: Double): Double {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()

        val X = xi and 255
        val Y = yi and 255

        val xf = x - xi
        val yf = y - yi

        val u = fade(xf)
        val v = fade(yf)
        val aa = p[p[X] + Y]; val ab = p[p[X] + Y + 1]
        val ba = p[p[X + 1] + Y]; val bb = p[p[X + 1] + Y + 1]
        return lerp(v, lerp(u, grad(p[aa], xf, yf), grad(p[ba], xf - 1, yf)),
            lerp(u, grad(p[ab], xf, yf - 1), grad(p[bb], xf - 1, yf - 1)))
    }

    fun noise(x: Double, y: Double, z: Double): Double {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val zi = floor(z).toInt()

        val X = xi and 255
        val Y = yi and 255
        val Z = zi and 255

        val xf = x - xi
        val yf = y - yi
        val zf = z - zi

        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)

        val A = p[X] + Y
        val AA = p[A] + Z
        val AB = p[A + 1] + Z
        val B = p[X + 1] + Y
        val BA = p[B] + Z
        val BB = p[B + 1] + Z

        return lerp(w,
            lerp(v,
                lerp(u, grad(p[AA], xf, yf, zf), grad(p[BA], xf - 1, yf, zf)),
                lerp(u, grad(p[AB], xf, yf - 1, zf), grad(p[BB], xf - 1, yf - 1, zf))
            ),
            lerp(v,
                lerp(u, grad(p[AA + 1], xf, yf, zf - 1), grad(p[BA + 1], xf - 1, yf, zf - 1)),
                lerp(u, grad(p[AB + 1], xf, yf - 1, zf - 1), grad(p[BB + 1], xf - 1, yf - 1, zf - 1))
            )
        )
    }
    fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
    fun lerp(t: Double, a: Double, b: Double) = a + t * (b - a)
    fun grad(hash: Int, x: Double, y: Double): Double {
        val u = if (hash and 1 == 0) x else -x
        val v = if (hash and 2 == 0) y else -y
        return u + v
    }

    fun grad(hash: Int, x: Double, y: Double, z: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return (if (h and 1 != 0) -u else u) + if (h and 2 != 0) -v else v
    }
}