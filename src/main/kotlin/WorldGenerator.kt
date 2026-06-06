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
    val heightVariation: Double, // Jak bardzo wysokość może się różnić od baseHeight
    val treeDensity: Double, // Gęstość drzew w biomie (0.0 - 1.0)
    val rarityThreshold: Double = 0.0, // 0.0 = zawsze możliwy, 1.0 = ekstremalnie rzadki. Biom pojawia się tylko jeśli globalRarity > rarityThreshold
    val sizeScaleModifier: Double = 1.0 // Modyfikator skali szumów klimatycznych/kontynentalnych dla tego biomu. >1.0 dla mniejszych, bardziej poszarpanych łat (mini-biom)
)

data class CaveBiome(
    val name: String,
    val wallColor: Int,
    val floorColor: Int
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
    val DEFAULT_CAVE = CaveBiome("Deep Caves", STONE, STONE)
    val LUSH_CAVE = CaveBiome("Lush Caves", Color(0x3B5905).rgb, Color(0x59A608).rgb)
    val DRIPSTONE_CAVE = CaveBiome("Dripstone", Color(0x4D3826).rgb, Color(0x4D3826).rgb)

    // 10 Głównych biomów
    val SNOWY_TUNDRA = Biome("Snowy Tundra", SNOW, DIRT, 58.0, 2.0, 0.0001, rarityThreshold = 0.0)
    val SNOWY_TAIGA = Biome("Snowy Taiga", SNOW, DIRT, 62.0, 5.0, 0.02, rarityThreshold = 0.0)
    val MOUNTAIN_MEADOW = Biome("Mountain Meadow", GRASS_COLD, DIRT, 78.0, 12.0, 0.001, rarityThreshold = 0.1) // Lekko rzadsze góry
    val MOUNTAIN_TAIGA = Biome("Mountain Taiga", GRASS_COLD, DIRT, 82.0, 15.0, 0.025, rarityThreshold = 0.15) // Rzadsze góry
    val PLAINS = Biome("Plains", GRASS_TEMPERATE, DIRT, 57.0, 3.0, 0.0005, rarityThreshold = 0.0)
    val FOREST = Biome("Forest", GRASS_TEMPERATE, DIRT, 60.0, 6.0, 0.03, rarityThreshold = 0.0)
    val DESERT = Biome("Desert", SAND, SAND, 56.0, 2.0, 0.0, rarityThreshold = 0.0)
    val SAVANNA = Biome("Savanna", GRASS_SAVANNA, DIRT, 63.0, 7.0, 0.005, rarityThreshold = 0.0)
    val BEACH = Biome("Beach", SAND, SAND, 51.0, 1.0, 0.0, rarityThreshold = 0.0)
    val OCEAN = Biome("Ocean", DIRT, DIRT, 38.0, 4.0, 0.0, rarityThreshold = 0.0)

    // Przykładowe mini-biomy i rzadkie biomy
    val MINI_FOREST = Biome("Mini Forest", GRASS_TEMPERATE, DIRT, 60.0, 5.0, 0.05, rarityThreshold = 0.4, sizeScaleModifier = 2.5) // Małe, gęste lasy, rzadsze
    val RARE_MOUNTAIN_PEAK = Biome("Rare Mountain Peak", STONE, STONE, 95.0, 8.0, 0.0, rarityThreshold = 0.8, sizeScaleModifier = 1.5) // Bardzo rzadkie, wysokie szczyty
}

class BiomeProvider(val seed: Int) {
    private val temperatureNoise = PerlinNoise(seed + 10)
    private val moistureNoise = PerlinNoise(seed + 20)
    private val continentalnessNoise = PerlinNoise(seed + 30)
    private val rarityNoise = PerlinNoise(seed + 40) // Szum do globalnej rzadkości biomów

    // Bazowe skale szumów
    private val baseClimateScale = 0.001 // Zmniejszone biomy (częstsza zmiana)
    private val baseContinentalScale = 0.001
    private val baseRarityScale = 0.0014 // Skalowanie rzadkości dostosowane do wielkości biomów
    
    private val caveBiomeNoise = PerlinNoise(seed + 50)
    private val caveClimateScale = 0.02

    fun getBiome(wx: Int, wz: Int): Biome {
        // 1. Pobieramy globalne wartości szumów
        val globalTemp = temperatureNoise.noise(wx * baseClimateScale, wz * baseClimateScale)
        val globalMoisture = moistureNoise.noise(wx * baseClimateScale, wz * baseClimateScale)
        val globalElevation = continentalnessNoise.noise(wx * baseContinentalScale, wz * baseContinentalScale)
        val globalRarity = rarityNoise.noise(wx * baseRarityScale, wz * baseRarityScale)

        // 2. BIOMY INNE (Mogą sąsiadować z każdym, zależne od wysokości/rzadkości)
        if (globalElevation < -0.4) return Biomes.OCEAN
        if (globalElevation < -0.3) return Biomes.BEACH
        
        // Rzadki szczyt jako "biom inny" - pojawia się bardzo rzadko na wysokich terenach
        if (globalElevation > 0.4 && (globalRarity + 0.5) / 1.0 > Biomes.RARE_MOUNTAIN_PEAK.rarityThreshold) {
            return Biomes.RARE_MOUNTAIN_PEAK
        }

        // 3. PODZIAŁ NA STREFY (Gwarantuje poprawne sąsiedztwo klimatyczne)
        // Snowy (< -0.3) <-> Cold (-0.3 do 0.0) <-> Temperate (0.0 do 0.4) <-> Warm (> 0.4)
        
        return when {
            globalTemp < -0.3 -> { // --- BIOMY OŚNIEŻONE ---
                if (globalMoisture > 0.0) Biomes.SNOWY_TAIGA else Biomes.SNOWY_TUNDRA
            }
            
            globalTemp < 0.0 -> { // --- BIOMY ZIMNE ---
                // Tutaj sprawdzamy rzadkość występowania specyficznych gór
                val normalizedRarity = (globalRarity + 0.7) / 1.4
                if (normalizedRarity > Biomes.MOUNTAIN_TAIGA.rarityThreshold && globalMoisture > -0.1) {
                    Biomes.MOUNTAIN_TAIGA
                } else if (normalizedRarity > Biomes.MOUNTAIN_MEADOW.rarityThreshold) {
                    Biomes.MOUNTAIN_MEADOW
                } else {
                    // Jeśli góry są "zbyt rzadkie" w tym punkcie, dajemy biom przejściowy (np. tundra)
                    Biomes.SNOWY_TUNDRA 
                }
            }
            
            globalTemp < 0.4 -> { // --- BIOMY UMIARKOWANE ---
                // Obsługa MINI_BIOMU (rzadki i gęsty las)
                val normalizedRarity = (globalRarity + 0.7) / 1.4
                if (normalizedRarity > Biomes.MINI_FOREST.rarityThreshold) {
                    // Dla mini-biomów przeliczamy lokalną wilgotność z ich własną skalą
                    val localScale = baseClimateScale * Biomes.MINI_FOREST.sizeScaleModifier
                    val localMoisture = moistureNoise.noise(wx * localScale, wz * localScale)
                    if (localMoisture > 0.1) return Biomes.MINI_FOREST
                }
                
                if (globalMoisture > 0.0) Biomes.FOREST else Biomes.PLAINS
            }
            
            else -> { // --- BIOMY CIEPŁE ---
                if (globalMoisture > -0.1) Biomes.SAVANNA else Biomes.DESERT
            }
        }
    }

    fun getCaveBiome(wx: Int, wy: Int, wz: Int): CaveBiome {
        val n = caveBiomeNoise.noise(wx * caveClimateScale, wy * caveClimateScale, wz * caveClimateScale)
        return when {
            n > 0.3 -> Biomes.LUSH_CAVE
            n < -0.3 -> Biomes.DRIPSTONE_CAVE
            else -> Biomes.DEFAULT_CAVE
        }
    }
}

/**
 * Klasa pomocnicza do definiowania warunków dla biomu.
 * Upraszcza logikę wyboru biomu w BiomeProvider.
 */
data class BiomeCondition(
    val biome: Biome,
    val tempRange: ClosedRange<Double>,
    val moistureRange: ClosedRange<Double>,
    val elevationRange: ClosedRange<Double>? = null // Opcjonalny zakres wysokości (dla gór, dolin itp.)
) {
    fun matches(temp: Double, moisture: Double, elevation: Double): Boolean {
        return temp in tempRange && moisture in moistureRange && (elevationRange == null || elevation in elevationRange)
    }
}

open class ChunkGenerator(
    val seed: Int,
    val oreColors: MutableSet<Int>
) {
    val noise = PerlinNoise(seed)
    val caveNoise = PerlinNoise(seed + 1)
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

        // 1. GENERACJA POWIERZCHNI (Bez wpływu jaskiń)
        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz
                val biome = biomeProvider.getBiome(wx, wz)
                val h = getTerrainHeight(wx, wz)
                
                chunk.setBlock(lx, 0, lz, Color.BLACK.rgb) // Bedrock
                for (y in 1..127) {
                    val block = getSurfaceBlock(y, h, biome)
                    if (block != BLOCK_ID_AIR) {
                        chunk.setBlock(lx, y, lz, block)
                    }
                }
            }
        }

        // 2. GENERACJA JASKIŃ (Rzeźbienie w postawionym terenie) //zaraz naprawiamy to dziadostwo
        for (lx in 0 until 16) {
            for (lz in 0 until 16) {
                val wx = cx * 16 + lx
                val wz = cz * 16 + lz
                val h = getTerrainHeight(wx, wz)
                
                carveCaves(chunk, lx, lz, wx, wz, h)
            }
        }
        //chwilowe komplikacje nastały - globalne uziemienie kodu
        // 2. Generowanie rud
        generateOres(chunk, cx, cz)

        // 3. Generowanie jezior lawy
        generateLavaLakes(chunk, cx, cz)

        // 4. Generowanie struktur na bazie biomów i globalnych
        generateBiomeStructures(chunk, cx, cz)
        generateStructureType(chunk, cx, cz, DungeonModel, 0.0001, 0, 30, Color(0x8EA3A1).rgb, 1, true, listOf(0, 90, 180, 270))
        generateStructureType(chunk, cx, cz, IglooModel, 0.00005, 52, 80, Biomes.SNOWY_TUNDRA.surfaceColor, 0, false, listOf(0, 90, 180, 270))

        chunk.modified = false
        return chunk
    }

    open fun getTerrainHeight(wx: Int, wz: Int): Int {
        // Zwiększony promień i gęstsze próbkowanie (9 punktów zamiast 5)
        // To eliminuje "ściany" poprzez łagodniejsze mieszanie baseHeight
        val radius = 8 
        var totalBaseHeight = 0.0
        var totalHeightVariation = 0.0
        var weightSum = 0.0

        for (dx in -radius..radius step radius) {
            for (dz in -radius..radius step radius) {
                val b = biomeProvider.getBiome(wx + dx, wz + dz)
                // Odległość od środka jako waga (środek ma największy wpływ)
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

    // Czysta logika powierzchni
    private fun getSurfaceBlock(wy: Int, terrainHeight: Int, biome: Biome): Int {
        if (wy > terrainHeight) {
            return if (wy <= SEA_LEVEL) BLOCK_ID_WATER else BLOCK_ID_AIR
        }
        
        val stoneDepth = 4
        return when {
            wy == terrainHeight -> if (terrainHeight >= SEA_LEVEL) biome.surfaceColor else biome.subsurfaceColor
            wy > terrainHeight - stoneDepth -> biome.subsurfaceColor
            else -> Color(0x8EA3A1).rgb // Stone
        }
    }

    // Czysta logika jaskiń
    private fun carveCaves(chunk: Chunk, lx: Int, lz: Int, wx: Int, wz: Int, terrainHeight: Int) {
        // Zwiększamy częstotliwość dla mniejszych, bardziej "pokręconych" korytarzy
        val frequency = 0.05 
        // Próg dla "spaghetti" - im mniejszy, tym węższe tunele (efekt robaka)
        val tunnelThreshold = 0.14
        val surfaceOpeningResistance = 0.15

        for (y in 1..terrainHeight) {
            // Technika "Double Noise" dla jaskiń typu 1.12:
            // Tworzymy dwie "wstęgi" szumu. Tam gdzie się przecinają, powstaje tunel.
            val n1 = caveNoise.noise(wx * frequency, y * frequency, wz * frequency)
            val n2 = caveNoise.noise(wx * frequency + 100.0, y * frequency + 100.0, wz * frequency + 100.0)
            
            // Połączenie dwóch szumów tworzy okrągły przekrój tunelu
            val combinedNoise = Math.sqrt(n1 * n1 + n2 * n2)
            
            val depth = terrainHeight - y
            
            // Dynamiczne zwężanie tuneli przy powierzchni
            val currentCaveWidth = if (depth < 12) {
                (tunnelThreshold - surfaceOpeningResistance * (1.0 - depth / 12.0)).coerceAtLeast(0.0)
            } else {
                tunnelThreshold
            }

            // Jeśli wypadkowa dwóch szumów jest mała, wycinamy blok
            if (combinedNoise < currentCaveWidth) {
                chunk.setBlock(lx, y, lz, BLOCK_ID_AIR)
                
                // Fundamenty pod biomy jaskiniowe: Malowanie podłogi jaskini
                // Jeśli blok pod nami (y-1) jest kamieniem, zmieńmy go na kolor biomu jaskiniowego
                if (y > 1 && chunk.getBlock(lx, y - 1, lz) != BLOCK_ID_AIR) {
                    val caveBiome = biomeProvider.getCaveBiome(wx, y, wz)
                    if (caveBiome != Biomes.DEFAULT_CAVE) {
                        chunk.setBlock(lx, y - 1, lz, caveBiome.floorColor)
                    }
                }
                // Malowanie sufitu
                if (y < 127 && chunk.getBlock(lx, y + 1, lz) != BLOCK_ID_AIR) {
                    val caveBiome = biomeProvider.getCaveBiome(wx, y, wz)
                    chunk.setBlock(lx, y + 1, lz, caveBiome.wallColor)
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
                    val surfaceY = rand.nextInt(10) + 4
                    val radius = rand.nextDouble() * 3.0 + 2.5
                    val maxDepth = rand.nextDouble() * 3.0 + 2.0
                    placeLake(chunk, lx, surfaceY, lz, radius, maxDepth)
                }
            }
        }
    }

    open fun isLakeCenter(wx: Int, wz: Int): Boolean {
        val hash = (wx * 73856093 xor wz * 19349663 xor seed).toString().hashCode()
        val random = Random(hash.toLong())
        return random.nextDouble() < 0.0005
    }

    open fun placeLake(chunk: Chunk, centerLx: Int, surfaceY: Int, centerLz: Int, radius: Double, maxDepth: Double) {
        val stoneColor = Color(0x8EA3A1).rgb
        val margin = 5.0
        val minX = (centerLx - radius - margin).toInt().coerceIn(0, 15)
        val maxX = (centerLx + radius + margin).toInt().coerceIn(0, 15)
        val minZ = (centerLz - radius - margin).toInt().coerceIn(0, 15)
        val maxZ = (centerLz + radius + margin).toInt().coerceIn(0, 15)
        val minY = (surfaceY - maxDepth - 3).toInt().coerceIn(1, 127)
        val maxY = (surfaceY + 2).toInt().coerceIn(0, 127)

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val dx = x - centerLx
                val dz = z - centerLz
                val distSq = dx * dx + dz * dz
                val dist = sqrt(distSq.toDouble())

                val wx = chunk.x * 16 + x
                val wz = chunk.z * 16 + z

                val shapeNoise = noise.noise(wx * 0.2, wz * 0.2)
                val effectiveRadius = radius + (shapeNoise * 2.0)
                val wallRadius = effectiveRadius + 1.5

                val noiseVal = noise.noise(wx * 0.3, wz * 0.3)
                val localDepth = maxDepth - (noiseVal * 1.5).coerceAtLeast(0.0)

                for (y in minY..maxY) {
                    if (effectiveRadius > 0 && dist < effectiveRadius) {
                        if (y <= surfaceY && y >= surfaceY - localDepth) {
                            chunk.setBlock(x, y, z, BLOCK_ID_LAVA)
                            chunk.setMeta(x, y, z, 8)
                        } else if (y > surfaceY && y <= surfaceY + 1) {
                            if (chunk.getBlock(x, y, z) != 0) chunk.setBlock(x, y, z, 0)
                        } else if (y < surfaceY - localDepth) {
                            if (chunk.getBlock(x, y, z) == 0) chunk.setBlock(x, y, z, stoneColor)
                        }
                    } else if (wallRadius > 0 && dist < wallRadius) {
                        if (y <= surfaceY && y >= surfaceY - localDepth) {
                            if (chunk.getBlock(x, y, z) == 0) {
                                chunk.setBlock(x, y, z, stoneColor)
                            }
                        }
                    }
                }
            }
        }
    }

    open fun generateStructureType(chunk: Chunk, cx: Int, cz: Int, model: List<ModelVoxel>, density: Double, minH: Int, maxH: Int, targetBlock: Int, yOffset: Int, clearSpace: Boolean = false, allowedRotations: List<Int> = listOf(0)) {
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
                        if (getSurfaceBlock(y, h, biome) == targetBlock && getSurfaceBlock(y + 1, h, biome) == BLOCK_ID_AIR) {
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