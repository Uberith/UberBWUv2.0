package com.uberith.api.game.world

import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.world.Coordinate
import kotlin.math.sqrt

class Coordinates {
    fun isPlayerAtCoordinate(x: Int, y: Int, z: Int): Boolean {
        val target = Coordinate(x, y, z)
        return getPlayerCoordinate() == target
    }
    fun isPlayerWithinRadius(centerX: Int, centerY: Int, centerZ: Int, radius: Int): Boolean {
        val center = Coordinate(centerX, centerY, centerZ)
        val pc = getPlayerCoordinate() ?: return false
        return calculateDistance(pc, center) <= radius
    }
    fun isPlayerWithinRadius(playerCoords: Coordinate, targetCoords: Coordinate?, radius: Int): Boolean {
        if (targetCoords == null) return false
        val dx = playerCoords.x - targetCoords.x
        val dy = playerCoords.y - targetCoords.y
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
        return dist <= radius
    }
    fun isPlayerWithinSquare(px: Int, py: Int, minX: Int, minY: Int, maxX: Int, maxY: Int): Boolean =
        px in minX..maxX && py in minY..maxY

    fun getPlayerCoordinate(): Coordinate? = LocalPlayer.self()?.coordinate
    fun calculateDistance(a: Coordinate, b: Coordinate): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt((dx * dx + dy * dy).toDouble())
    }
    fun distance(a: Coordinate, b: Coordinate): Double =
        Math.sqrt(Math.pow((a.x - b.x).toDouble(), 2.0) + Math.pow((a.y - b.y).toDouble(), 2.0))
    fun validateCoordinate(c: Coordinate?): Boolean = c != null
    fun createCoordinates(x: Int, y: Int, z: Int): Coordinate = Coordinate(x, y, z)
}
