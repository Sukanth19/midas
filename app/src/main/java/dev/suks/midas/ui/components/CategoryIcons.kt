package dev.suks.midas.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon keys are stored in the database, so this mapping must only ever grow. */
object CategoryIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "cart" to Icons.Rounded.ShoppingCart,
        "home" to Icons.Rounded.Home,
        "restaurant" to Icons.Rounded.Restaurant,
        "coffee" to Icons.Rounded.LocalCafe,
        "car" to Icons.Rounded.DirectionsCar,
        "gas" to Icons.Rounded.LocalGasStation,
        "bag" to Icons.Rounded.ShoppingBag,
        "bolt" to Icons.Rounded.Bolt,
        "water" to Icons.Rounded.WaterDrop,
        "wifi" to Icons.Rounded.Wifi,
        "phone" to Icons.Rounded.PhoneAndroid,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "tv" to Icons.Rounded.Tv,
        "music" to Icons.Rounded.MusicNote,
        "health" to Icons.Rounded.LocalHospital,
        "fitness" to Icons.Rounded.FitnessCenter,
        "movie" to Icons.Rounded.Movie,
        "school" to Icons.Rounded.School,
        "flight" to Icons.Rounded.Flight,
        "spa" to Icons.Rounded.Spa,
        "pets" to Icons.Rounded.Pets,
        "gift" to Icons.Rounded.CardGiftcard,
        "card" to Icons.Rounded.CreditCard,
        "receipt" to Icons.Rounded.Receipt,
        "work" to Icons.Rounded.Work,
        "laptop" to Icons.Rounded.Laptop,
        "undo" to Icons.Rounded.Replay,
        "savings" to Icons.Rounded.Savings,
        "more" to Icons.Rounded.Category,
    )

    fun get(key: String?): ImageVector = all[key] ?: Icons.Rounded.Category

    /** Pleasant, distinguishable colours offered when creating a category. */
    val palette: List<Long> = listOf(
        0xFF2E7D32, 0xFF5E35B1, 0xFFEF6C00, 0xFF1565C0, 0xFFD81B60, 0xFFF9A825,
        0xFF00838F, 0xFFC62828, 0xFF6A1B9A, 0xFF283593, 0xFF00695C, 0xFF546E7A,
    )
}
