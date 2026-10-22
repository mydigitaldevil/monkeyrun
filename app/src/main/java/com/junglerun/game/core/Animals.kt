package com.junglerun.game.core

/** Each animal has its own food. Whatever animal is running, its food is what spawns on the track. */
enum class Food(val displayName: String, val emoji: String, val color: Int, val accent: Int) {
    BANANA("Banana", "🍌", 0xFFFFD93B.toInt(), 0xFF8A6D00.toInt()),
    MEAT("Meat", "🍖", 0xFFC0392B.toInt(), 0xFFF5E6CC.toInt()),
    CARROT("Carrot", "🥕", 0xFFFF8C1A.toInt(), 0xFF2ECC40.toInt()),
    BAMBOO("Bamboo", "🎋", 0xFF7BC74D.toInt(), 0xFF3D7A1F.toInt()),
    HONEY("Honey", "🍯", 0xFFF5A623.toInt(), 0xFF8B5A00.toInt()),
    PEANUT("Peanut", "🥜", 0xFFD9A066.toInt(), 0xFF7A4E22.toInt()),
    LEAVES("Leaves", "🌿", 0xFF4CAF50.toInt(), 0xFF1B5E20.toInt()),
}

/** Visual theme + hazard mix for the track. */
enum class Biome(
    val displayName: String,
    val skyTop: Int, val skyBottom: Int,
    val farHills: Int, val nearTrees: Int,
    val ground: Int, val groundTop: Int,
    val pitColor: Int,
    /** Relative weights for: LOW, TALL, OVERHEAD, PIT, FALLING, MOVING, FLYER */
    val hazardWeights: FloatArray,
    val challengeName: String,
) {
    JUNGLE("Deep Jungle", 0xFF5EC8F2.toInt(), 0xFFC9F1D9.toInt(), 0xFF2E7D32.toInt(), 0xFF1B5E20.toInt(),
        0xFF6D4C41.toInt(), 0xFF43A047.toInt(), 0xFF0D47A1.toInt(),
        floatArrayOf(3f, 1.5f, 2.5f, 1.5f, 2.5f, 0.5f, 0.5f), "Falling coconuts"),
    SAVANNA("Golden Savanna", 0xFFFFB74D.toInt(), 0xFFFFF3C4.toInt(), 0xFFC68A3A.toInt(), 0xFF8D6E2B.toInt(),
        0xFFB5823C.toInt(), 0xFFD4B24C.toInt(), 0xFF5D4037.toInt(),
        floatArrayOf(3f, 2f, 1f, 1.5f, 0.5f, 3f, 0.5f), "Warthog stampede"),
    MEADOW("Carrot Meadow", 0xFF81D4FA.toInt(), 0xFFE1F5FE.toInt(), 0xFF7CB342.toInt(), 0xFF558B2F.toInt(),
        0xFF8D6E63.toInt(), 0xFF8BC34A.toInt(), 0xFF3E2723.toInt(),
        floatArrayOf(3f, 1f, 1.5f, 3.5f, 0.5f, 1f, 1f), "Burrow holes"),
    BAMBOO_FOREST("Bamboo Grove", 0xFFB2EBF2.toInt(), 0xFFF1F8E9.toInt(), 0xFF9CCC65.toInt(), 0xFF689F38.toInt(),
        0xFF795548.toInt(), 0xFF7CB342.toInt(), 0xFF1A237E.toInt(),
        floatArrayOf(2.5f, 1.5f, 3.5f, 1f, 1f, 0.5f, 1f), "Low bamboo bars"),
    PINE_FOREST("Honey Woods", 0xFF90A4AE.toInt(), 0xFFE0F2F1.toInt(), 0xFF37474F.toInt(), 0xFF2E5D3A.toInt(),
        0xFF5D4037.toInt(), 0xFF388E3C.toInt(), 0xFF263238.toInt(),
        floatArrayOf(2.5f, 1.5f, 1.5f, 1f, 1f, 0.5f, 3.5f), "Bee swarms"),
    RIVERBANK("Croc River", 0xFF4FC3F7.toInt(), 0xFFB3E5FC.toInt(), 0xFF00897B.toInt(), 0xFF00695C.toInt(),
        0xFF6D4C41.toInt(), 0xFF26A69A.toInt(), 0xFF0277BD.toInt(),
        floatArrayOf(2.5f, 1.5f, 1f, 3.5f, 0.5f, 1.5f, 0.5f), "Wide rivers"),
}

/** Who chases you. The monkey is chased by the lion! */
enum class Chaser(val displayName: String, val body: Int, val accent: Int) {
    LION("Lion", 0xFFE0A030.toInt(), 0xFF8B4513.toInt()),
    FOX("Fox", 0xFFE8702A.toInt(), 0xFFFFFFFF.toInt()),
    TIGER("Tiger", 0xFFF57C00.toInt(), 0xFF212121.toInt()),
    RHINO("Rhino", 0xFF8D8D8D.toInt(), 0xFFE0E0E0.toInt()),
    BEES("Angry Bees", 0xFFFFD600.toInt(), 0xFF212121.toInt()),
    CROCODILE("Crocodile", 0xFF4E7D32.toInt(), 0xFFF0F4C3.toInt()),
    HYENA("Hyena Pack", 0xFFB59B6D.toInt(), 0xFF4E342E.toInt()),
}

/** Active ability, charged by eating food and fired with the ability button. */
enum class Ability(val displayName: String, val description: String, val baseDuration: Float) {
    FOOD_FRENZY("Banana Frenzy", "Turns every obstacle on screen into food", 0.6f),
    MOON_JUMP("Moon Jump", "Huge floaty bounces — nothing can stop you", 6f),
    ROLL_SHIELD("Panda Roll", "Roll through anything, untouchable", 6f),
    ROAR("King's Roar", "Blasts obstacles away and scares the chaser off", 0.6f),
    HONEY_MAGNET("Honey Magnet", "Pulls every treat on screen to you", 10f),
    STOMP("Trunk Smash", "Smash straight through obstacles", 7f),
    DASH("Turbo Dash", "Blazing invincible sprint", 5f),
    LONG_NECK("Long Neck", "Grab food at any height automatically", 8f),
}

enum class Shape { MONKEY, RABBIT, PANDA, LION, BEAR, ELEPHANT, CHEETAH, GIRAFFE }

enum class UnlockType { FREE, COINS, GEMS, PURCHASE }

data class AnimalSpec(
    val id: String,
    val displayName: String,
    val tagline: String,
    val shape: Shape,
    val bodyColor: Int,
    val accentColor: Int,
    val food: Food,
    val biome: Biome,
    val chaser: Chaser,
    val ability: Ability,
    val unlockType: UnlockType,
    val price: Int,
    /** Google Play product id when [unlockType] is PURCHASE. */
    val productId: String? = null,
    val jumpMultiplier: Float = 1f,
    val speedMultiplier: Float = 1f,
    val coinMultiplier: Float = 1f,
    val doubleJump: Boolean = false,
)

object Animals {
    val all: List<AnimalSpec> = listOf(
        AnimalSpec("monkey", "Momo the Monkey", "Swings, double-jumps and loves bananas. Watch out for the lion!",
            Shape.MONKEY, 0xFF8D5524.toInt(), 0xFFF1C27D.toInt(), Food.BANANA, Biome.JUNGLE, Chaser.LION,
            Ability.FOOD_FRENZY, UnlockType.FREE, 0, doubleJump = true),
        AnimalSpec("rabbit", "Bunny Bolt", "Highest jumper in the meadow. A fox is always close behind.",
            Shape.RABBIT, 0xFFEDEDED.toInt(), 0xFFFFB6C1.toInt(), Food.CARROT, Biome.MEADOW, Chaser.FOX,
            Ability.MOON_JUMP, UnlockType.COINS, 2_500, jumpMultiplier = 1.15f),
        AnimalSpec("panda", "Pao the Panda", "Rolls through trouble in the bamboo grove.",
            Shape.PANDA, 0xFFFAFAFA.toInt(), 0xFF212121.toInt(), Food.BAMBOO, Biome.BAMBOO_FOREST, Chaser.TIGER,
            Ability.ROLL_SHIELD, UnlockType.COINS, 5_000),
        AnimalSpec("lion", "Leo the Lion", "King of the savanna. Roar to clear the path — but the rhino is angry.",
            Shape.LION, 0xFFE0A030.toInt(), 0xFF8B4513.toInt(), Food.MEAT, Biome.SAVANNA, Chaser.RHINO,
            Ability.ROAR, UnlockType.COINS, 8_000, speedMultiplier = 1.05f),
        AnimalSpec("bear", "Bruno Bear", "Stole the honey. Now the whole hive wants it back.",
            Shape.BEAR, 0xFF6D4C41.toInt(), 0xFFD7B899.toInt(), Food.HONEY, Biome.PINE_FOREST, Chaser.BEES,
            Ability.HONEY_MAGNET, UnlockType.COINS, 12_000, coinMultiplier = 1.1f),
        AnimalSpec("elephant", "Ellie Elephant", "Smashes through anything. Crocs lurk in the river.",
            Shape.ELEPHANT, 0xFF9E9E9E.toInt(), 0xFFF8BBD0.toInt(), Food.PEANUT, Biome.RIVERBANK, Chaser.CROCODILE,
            Ability.STOMP, UnlockType.GEMS, 80, jumpMultiplier = 0.95f),
        AnimalSpec("cheetah", "Chase the Cheetah", "Fastest animal alive. Outrun the hyena pack!",
            Shape.CHEETAH, 0xFFF4C430.toInt(), 0xFF3E2723.toInt(), Food.MEAT, Biome.SAVANNA, Chaser.HYENA,
            Ability.DASH, UnlockType.GEMS, 150, speedMultiplier = 1.1f, coinMultiplier = 1.15f),
        AnimalSpec("giraffe", "Gigi Giraffe", "Reaches food nobody else can.",
            Shape.GIRAFFE, 0xFFF6C46A.toInt(), 0xFF8B5A2B.toInt(), Food.LEAVES, Biome.SAVANNA, Chaser.LION,
            Ability.LONG_NECK, UnlockType.GEMS, 200, coinMultiplier = 1.2f),
        AnimalSpec("golden_monkey", "Golden Momo", "Premium: double jump, 2x coins forever and a golden glow.",
            Shape.MONKEY, 0xFFFFC107.toInt(), 0xFFFFF59D.toInt(), Food.BANANA, Biome.JUNGLE, Chaser.LION,
            Ability.FOOD_FRENZY, UnlockType.PURCHASE, 0, productId = "animal_golden_monkey",
            doubleJump = true, coinMultiplier = 2f),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): AnimalSpec = byId[id] ?: all.first()

    const val DEFAULT_ID = "monkey"
}
