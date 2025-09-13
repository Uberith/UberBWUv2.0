package com.uberith.uberchop

import net.botwithus.rs3.world.Coordinate

object LocationCatalog {
    val ALL: List<TreeLocation> = listOf(
        TreeLocation(
            name = "Anywhere",
            availableTrees = TreeTypes.ALL,
            chop = null,
            bank = null
        ),
        TreeLocation(
            name = "Prifddinas Ithell Elders",
            availableTrees = listOf("Elder"),
            chop = Coordinate(2228, 3389, 0),
            bank = Coordinate(2235, 3370, 0)
        ),
        TreeLocation(
            name = "Prifddinas Iorwerth Elders",
            availableTrees = listOf("Elder"),
            chop = Coordinate(2206, 3362, 0),
            bank = Coordinate(2199, 3369, 0)
        ),
        TreeLocation(
            name = "Prifddinas Crystal Tree",
            availableTrees = listOf("Crystal"),
            chop = Coordinate(2225, 3372, 0),
            bank = Coordinate(2235, 3370, 0)
        ),
        TreeLocation(
            name = "Prifddinas Max Guild Magics",
            availableTrees = listOf("Magic", "Yew"),
            chop = Coordinate(2214, 3357, 0),
            bank = Coordinate(2207, 3369, 1)
        ),
        TreeLocation(
            name = "Menaphos Port Acadia",
            availableTrees = listOf("Acadia"),
            chop = Coordinate(3205, 2818, 0),
            bank = Coordinate(3208, 2802, 0)
        ),
        TreeLocation(
            name = "Menaphos Merchant Acadia",
            availableTrees = listOf("Acadia"),
            chop = Coordinate(3178, 2834, 0),
            bank = Coordinate(3174, 2842, 0)
        ),
        TreeLocation(
            name = "Varrock Palace Ivy (AFK)",
            availableTrees = listOf("Ivy"),
            chop = Coordinate(3215, 3462, 0),
            bank = Coordinate(3253, 3420, 0)
        ),
        TreeLocation(
            name = "Ardougne Ivy (AFK)",
            availableTrees = listOf("Ivy"),
            chop = Coordinate(2602, 3294, 0),
            bank = Coordinate(2615, 3332, 0)
        ),
        TreeLocation(
            name = "Burthorpe",
            availableTrees = listOf("Tree", "Oak", "Willow"),
            chop = Coordinate(2903, 3501, 0),
            bank = Coordinate(2890, 3534, 0)
        ),
        TreeLocation(
            name = "Burthorpe",
            availableTrees = listOf("Yew"),
            chop = Coordinate(2919, 3548, 0),
            bank = Coordinate(2890, 3534, 0)
        ),
        TreeLocation(
            name = "Lumbridge",
            availableTrees = listOf("Tree", "Oak", "Willow"),
            chop = Coordinate(3202, 3231, 0),
            bank = Coordinate(3209, 3219, 2)
        ),
        TreeLocation(
            name = "Varrock West",
            availableTrees = listOf("Tree", "Oak", "Ivy"),
            chop = Coordinate(3169, 3421, 0),
            bank = Coordinate(3185, 3436, 0)
        ),
        TreeLocation(
            name = "Varrock East",
            availableTrees = listOf("Tree", "Oak", "Ivy"),
            chop = Coordinate(3291, 3426, 0),
            bank = Coordinate(3253, 3420, 0)
        ),
        TreeLocation(
            name = "Draynor Willows",
            availableTrees = listOf("Willow"),
            chop = Coordinate(3085, 3238, 0),
            bank = Coordinate(3093, 3245, 0)
        ),
        TreeLocation(
            name = "Edgeville Yews",
            availableTrees = listOf("Yew"),
            chop = Coordinate(3088, 3478, 0),
            bank = Coordinate(3093, 3491, 0)
        ),
        TreeLocation(
            name = "Seers' Village",
            availableTrees = listOf("Maple", "Magic", "Yew"),
            chop = Coordinate(2725, 3491, 0),
            bank = Coordinate(2723, 3493, 0)
        ),
        TreeLocation(
            name = "Prifddinas",
            availableTrees = listOf("Magic", "Yew", "Elder", "Crystal"),
            chop = null,
            bank = null
        ),
        TreeLocation(
            name = "Anachronia (Bamboo)",
            availableTrees = listOf("Bamboo"),
            chop = Coordinate(5477, 2333, 0),
            bank = null
        ),
        TreeLocation(
            name = "Karamja (Teak/Mahogany)",
            availableTrees = listOf("Teak", "Mahogany"),
            chop = Coordinate(2795, 3086, 0),
            bank = null
        ),
        TreeLocation(
            name = "Falador Yews (South)",
            availableTrees = listOf("Yew"),
            chop = Coordinate(3059, 3311, 0),
            bank = Coordinate(3013, 3355, 0)
        ),
        TreeLocation(
            name = "Seers' Magics (Tower)",
            availableTrees = listOf("Magic"),
            chop = Coordinate(2706, 3405, 0),
            bank = Coordinate(2723, 3493, 0)
        ),
        TreeLocation(
            name = "Catherby Maples",
            availableTrees = listOf("Maple"),
            chop = Coordinate(2780, 3433, 0),
            bank = Coordinate(2809, 3440, 0)
        ),
        TreeLocation(
            name = "Gnome Stronghold Yews",
            availableTrees = listOf("Yew"),
            chop = Coordinate(2448, 3423, 0),
            bank = Coordinate(2449, 3426, 0)
        )
    )
}

