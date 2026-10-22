package com.junglerun.game.billing

/** Google Play Console product ids. Create these exact ids in Play Console → Monetize → Products. */
enum class ProductKind { CONSUMABLE, NON_CONSUMABLE, SUBSCRIPTION }

data class StoreProduct(
    val id: String,
    val kind: ProductKind,
    val title: String,
    val description: String,
    val coins: Int = 0,
    val gems: Int = 0,
    val fallbackPrice: String,
    val badge: String? = null,
)

object Products {
    const val REMOVE_ADS = "remove_ads"
    const val STARTER_PACK = "starter_pack"
    const val GOLDEN_MONKEY = "animal_golden_monkey"
    const val VIP_MONTHLY = "vip_monthly"

    val all = listOf(
        StoreProduct("coins_small", ProductKind.CONSUMABLE, "Pile of Coins", "5,000 coins", coins = 5_000, fallbackPrice = "₹79"),
        StoreProduct("coins_medium", ProductKind.CONSUMABLE, "Sack of Coins", "15,000 coins", coins = 15_000, fallbackPrice = "₹199", badge = "Popular"),
        StoreProduct("coins_large", ProductKind.CONSUMABLE, "Treasure Chest", "50,000 coins", coins = 50_000, fallbackPrice = "₹499", badge = "Best value"),
        StoreProduct("gems_small", ProductKind.CONSUMABLE, "Handful of Gems", "50 gems", gems = 50, fallbackPrice = "₹99"),
        StoreProduct("gems_medium", ProductKind.CONSUMABLE, "Bag of Gems", "150 gems", gems = 150, fallbackPrice = "₹249"),
        StoreProduct("gems_large", ProductKind.CONSUMABLE, "Vault of Gems", "500 gems", gems = 500, fallbackPrice = "₹699", badge = "+25% bonus"),
        StoreProduct(STARTER_PACK, ProductKind.NON_CONSUMABLE, "Starter Pack", "Remove ads + 10,000 coins + 50 gems + Bunny Bolt", coins = 10_000, gems = 50, fallbackPrice = "₹149", badge = "One time"),
        StoreProduct(REMOVE_ADS, ProductKind.NON_CONSUMABLE, "Remove Ads", "No banners, interstitials or app-open ads. Rewarded ads stay optional.", fallbackPrice = "₹249"),
        StoreProduct(GOLDEN_MONKEY, ProductKind.NON_CONSUMABLE, "Golden Momo", "Premium monkey with 2x coins forever", fallbackPrice = "₹299"),
        StoreProduct(VIP_MONTHLY, ProductKind.SUBSCRIPTION, "Jungle VIP", "No ads, 2x coins every run, +10 gems daily", fallbackPrice = "₹149/month", badge = "Subscription"),
    )

    fun get(id: String): StoreProduct? = all.firstOrNull { it.id == id }
}
