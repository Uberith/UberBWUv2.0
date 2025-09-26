package com.uberith.ubertestingutil

import net.botwithus.rs3.cache.assets.items.ItemDefinition
import net.botwithus.rs3.cache.assets.items.StackType
import net.botwithus.rs3.item.GroundItem
import net.botwithus.rs3.world.Area
import net.botwithus.rs3.world.Coordinate
import net.botwithus.rs3.world.Distance
import net.botwithus.xapi.query.GroundItemQuery
import java.util.function.BiFunction
import java.util.regex.Pattern
import kotlin.math.min

class GroundItemQueryTester {

    enum class Status {
        PASSED,
        FAILED,
        SKIPPED
    }

    data class TestResult(val name: String, val status: Status, val detail: String)

    fun runDiagnostics(items: List<GroundItem>): List<TestResult> {
        val safeItems = items.filterNotNull()
        val tests = listOf(
            run("constructor") { testConstructor(safeItems) },
            run("newQuery") { testNewQuery(safeItems) },
            run("results") { testResults() },
            run("iterator") { testIterator() },
            run("test") { testPredicate(safeItems) },
            run("id") { testId(safeItems) },
            run("quantity(predicate)") { testQuantityPredicate(safeItems) },
            run("quantity") { testQuantityEquals(safeItems) },
            run("itemTypes") { testItemTypes(safeItems) },
            run("category") { testCategory(safeItems) },
            run("name(predicate)") { testNamePredicate(safeItems) },
            run("name") { testNameEquals(safeItems) },
            run("name(pattern)") { testNamePattern(safeItems) },
            run("stackType") { testStackType(safeItems) },
            run("coordinate") { testCoordinate(safeItems) },
            run("inside") { testInside(safeItems) },
            run("outside") { testOutside(safeItems) },
            run("distance") { testDistance(safeItems) },
            run("valid") { testValid(safeItems) },
            run("and") { testAnd(safeItems) },
            run("or") { testOr(safeItems) },
            run("invert") { testInvert(safeItems) },
            run("mark") { testMark() }
        )
        return tests
    }

    private inline fun run(name: String, block: () -> Outcome): TestResult =
        try {
            val outcome = block()
            TestResult(name, outcome.status, outcome.detail)
        } catch (throwable: Throwable) {
            TestResult(name, Status.FAILED, "Exception: ${throwable.message ?: throwable.javaClass.simpleName}")
        }

    private data class Outcome(val status: Status, val detail: String)

    private fun testConstructor(items: List<GroundItem>): Outcome {
        val query = GroundItemQuery()
        val anyRejected = items.any { !safeTest(query, it) }
        return if (!anyRejected) {
            Outcome(Status.PASSED, "Default predicate accepted ${items.size} item(s)")
        } else {
            Outcome(Status.FAILED, "Default predicate rejected at least one ground item")
        }
    }

    private fun testNewQuery(items: List<GroundItem>): Outcome {
        val query = GroundItemQuery.newQuery()
        val anyRejected = items.any { !safeTest(query, it) }
        return if (!anyRejected) {
            Outcome(Status.PASSED, "Factory predicate accepted ${items.size} item(s)")
        } else {
            Outcome(Status.FAILED, "Factory predicate rejected at least one ground item")
        }
    }

    private fun testResults(): Outcome {
        val results = GroundItemQuery.newQuery().results()
        return Outcome(Status.PASSED, "results() returned ${results.size()} item(s)")
    }

    private fun testIterator(): Outcome {
        val iterator = GroundItemQuery.newQuery().iterator()
        var count = 0
        while (iterator.hasNext()) {
            iterator.next()
            count++
        }
        return Outcome(Status.PASSED, "iterator() produced $count item(s)")
    }

    private fun testPredicate(items: List<GroundItem>): Outcome {
        if (items.isEmpty()) {
            return Outcome(Status.SKIPPED, "No ground items available to test predicate")
        }
        val query = GroundItemQuery.newQuery()
        val anyRejected = items.any { !safeTest(query, it) }
        return if (!anyRejected) {
            Outcome(Status.PASSED, "Default predicate matched observed items")
        } else {
            Outcome(Status.FAILED, "Default predicate rejected an observed item")
        }
    }

    private fun testId(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull() ?: return Outcome(Status.SKIPPED, "No ground items to test id filter")
        val query = GroundItemQuery.newQuery().id(candidate.id)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it.id != candidate.id }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = buildString {
                append("Accepted id ")
                append(candidate.id)
                if (alternate != null) {
                    append(" and rejected ")
                    append(alternate.id)
                } else {
                    append("; no alternate id observed")
                }
            }
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "id filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testQuantityPredicate(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull() ?: return Outcome(Status.SKIPPED, "No ground items to test quantity predicate")
        val threshold = candidate.quantity
        val query = GroundItemQuery.newQuery().quantity(BiFunction { actual: Int, expected: Int -> actual >= expected }, threshold)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it.quantity < threshold }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = buildString {
                append("Threshold >= ")
                append(threshold)
                if (alternate != null) {
                    append(" and rejected stack of ")
                    append(alternate.quantity)
                } else {
                    append("; no smaller stack observed")
                }
            }
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "quantity predicate failed (pass=$positive negate=$negative)")
        }
    }

    private fun testQuantityEquals(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull() ?: return Outcome(Status.SKIPPED, "No ground items to test quantity equality")
        val threshold = candidate.quantity
        val query = GroundItemQuery.newQuery().quantity(threshold)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it.quantity != threshold }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = buildString {
                append("Matched quantity ")
                append(threshold)
                if (alternate != null) {
                    append(" and rejected ")
                    append(alternate.quantity)
                } else {
                    append("; no differing quantity observed")
                }
            }
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "quantity equality failed (pass=$positive negate=$negative)")
        }
    }

    private fun testItemTypes(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeType(it) != null } ?: return Outcome(Status.SKIPPED, "No item definitions available")
        val type = safeType(candidate) ?: return Outcome(Status.SKIPPED, "Item definition unavailable")
        val query = GroundItemQuery.newQuery().itemTypes(type)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it !== candidate && safeType(it) != type }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = if (alternate != null) "Matched item type and rejected alternate type" else "Matched item type; no alternate type observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "itemTypes filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testCategory(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeCategory(it) != null } ?: return Outcome(Status.SKIPPED, "No category data available")
        val category = safeCategory(candidate) ?: return Outcome(Status.SKIPPED, "Category unavailable")
        val query = GroundItemQuery.newQuery().category(category)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it !== candidate && safeCategory(it) != category }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = if (alternate != null) "Matched category $category and rejected ${safeCategory(alternate)}" else "Matched category $category; no alternate category observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "category filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testNamePredicate(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeName(it) != null } ?: return Outcome(Status.SKIPPED, "No item names available")
        val name = safeName(candidate) ?: return Outcome(Status.SKIPPED, "Item name unavailable")
        if (name.isEmpty()) {
            return Outcome(Status.SKIPPED, "Item name is blank")
        }
        val fragment = name.substring(0, min(3, name.length))
        val query = GroundItemQuery.newQuery().name(BiFunction<String, CharSequence, Boolean> { actual, expected -> actual != null && actual.contains(expected, ignoreCase = true) }, fragment)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it !== candidate && (safeName(it)?.contains(fragment, ignoreCase = true) != true) }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = if (alternate != null) "Matched fragment '$fragment' and rejected '${safeName(alternate) ?: ""}'" else "Matched fragment '$fragment'; no contrasting name observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "name predicate failed (pass=$positive negate=$negative)")
        }
    }

    private fun testNameEquals(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeName(it) != null } ?: return Outcome(Status.SKIPPED, "No item names available")
        val name = safeName(candidate) ?: return Outcome(Status.SKIPPED, "Item name unavailable")
        val query = GroundItemQuery.newQuery().name(name)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it !== candidate && safeName(it) != name }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = if (alternate != null) "Matched name '$name' and rejected '${safeName(alternate) ?: ""}'" else "Matched name '$name'; no alternate name observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "name filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testNamePattern(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeName(it) != null } ?: return Outcome(Status.SKIPPED, "No named ground items available")
        val name = safeName(candidate) ?: return Outcome(Status.SKIPPED, "Item name unavailable")
        if (name.isEmpty()) {
            return Outcome(Status.SKIPPED, "Item name is blank")
        }
        val fragment = name.substring(0, min(3, name.length))
        val pattern = Pattern.compile(".*" + Pattern.quote(fragment) + ".*", Pattern.CASE_INSENSITIVE)
        val query = GroundItemQuery.newQuery().name(pattern)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it !== candidate && (safeName(it)?.let { n -> pattern.matcher(n).matches() } != false) }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = "Pattern '${pattern.pattern()}' matched target name"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "name(pattern) filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testStackType(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeStackType(it) != null } ?: return Outcome(Status.SKIPPED, "No stack type data available")
        val stackType = safeStackType(candidate) ?: return Outcome(Status.SKIPPED, "Stack type unavailable")
        val query = GroundItemQuery.newQuery().stackType(stackType)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it !== candidate && safeStackType(it) != stackType }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = if (alternate != null) "Matched stackType $stackType and rejected ${safeStackType(alternate)}" else "Matched stackType $stackType; no alternate observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "stackType filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testCoordinate(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeCoordinate(it) != null } ?: return Outcome(Status.SKIPPED, "No coordinates available")
        val coordinate = safeCoordinate(candidate) ?: return Outcome(Status.SKIPPED, "Coordinate unavailable")
        val query = GroundItemQuery.newQuery().coordinate(coordinate)
        val positive = safeTest(query, candidate)
        val alternate = items.firstOrNull { it !== candidate && safeCoordinate(it) != null && safeCoordinate(it) != coordinate }
        val negative = alternate?.let { !safeTest(query, it) } ?: true
        return if (positive && negative) {
            val detail = if (alternate != null) "Matched coordinate $coordinate and rejected ${safeCoordinate(alternate)}" else "Matched coordinate $coordinate; no alternate observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "coordinate filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testInside(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeCoordinate(it) != null } ?: return Outcome(Status.SKIPPED, "No coordinates for inside() test")
        val coordinate = safeCoordinate(candidate) ?: return Outcome(Status.SKIPPED, "Coordinate unavailable")
        val containing = Area.Rectangular(coordinate, coordinate)
        val offset = coordinate.derive(10, 10, 0)
        val elsewhere = Area.Rectangular(offset, offset)
        val insideQuery = GroundItemQuery.newQuery().inside(containing)
        val outsideQuery = GroundItemQuery.newQuery().inside(elsewhere)
        val positive = safeTest(insideQuery, candidate)
        val negative = !safeTest(outsideQuery, candidate)
        return if (positive && negative) {
            Outcome(Status.PASSED, "inside() detected membership and rejected outside area")
        } else {
            Outcome(Status.FAILED, "inside() filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testOutside(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeCoordinate(it) != null } ?: return Outcome(Status.SKIPPED, "No coordinates for outside() test")
        val coordinate = safeCoordinate(candidate) ?: return Outcome(Status.SKIPPED, "Coordinate unavailable")
        val containing = Area.Rectangular(coordinate, coordinate)
        val offset = coordinate.derive(10, 10, 0)
        val elsewhere = Area.Rectangular(offset, offset)
        val insideAreaQuery = GroundItemQuery.newQuery().outside(containing)
        val outsideAreaQuery = GroundItemQuery.newQuery().outside(elsewhere)
        val positive = safeTest(outsideAreaQuery, candidate)
        val negative = !safeTest(insideAreaQuery, candidate)
        return if (positive && negative) {
            Outcome(Status.PASSED, "outside() rejected containing area and accepted outside area")
        } else {
            Outcome(Status.FAILED, "outside() filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testDistance(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeCoordinate(it) != null } ?: return Outcome(Status.SKIPPED, "No coordinates for distance() test")
        val coordinate = safeCoordinate(candidate) ?: return Outcome(Status.SKIPPED, "Coordinate unavailable")
        val distanceToPlayer = Distance.to(coordinate)
        if (distanceToPlayer.isNaN()) {
            return Outcome(Status.SKIPPED, "Player position unavailable for distance() test")
        }
        val inclusiveQuery = GroundItemQuery.newQuery().distance(distanceToPlayer + 0.5)
        val positive = safeTest(inclusiveQuery, candidate)
        val exclusiveThreshold = distanceToPlayer - 0.5
        val negative = if (exclusiveThreshold > 0) {
            !safeTest(GroundItemQuery.newQuery().distance(exclusiveThreshold), candidate)
        } else {
            true
        }
        return if (positive && negative) {
            Outcome(Status.PASSED, "distance() respected threshold ${"%.2f".format(distanceToPlayer)}")
        } else {
            Outcome(Status.FAILED, "distance() filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testValid(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull { safeValidity(it) != null } ?: return Outcome(Status.SKIPPED, "No stack validity data available")
        val isValid = safeValidity(candidate) ?: return Outcome(Status.SKIPPED, "Stack validity unavailable")
        val positive = safeTest(GroundItemQuery.newQuery().valid(isValid), candidate)
        val negative = !safeTest(GroundItemQuery.newQuery().valid(!isValid), candidate)
        return if (positive && negative) {
            Outcome(Status.PASSED, "valid($isValid) behaved as expected")
        } else {
            Outcome(Status.FAILED, "valid() filter failed (pass=$positive negate=$negative)")
        }
    }

    private fun testAnd(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull() ?: return Outcome(Status.SKIPPED, "No ground items for and() test")
        val base = GroundItemQuery.newQuery().id(candidate.id)
        val combined = base.and(GroundItemQuery.newQuery().quantity(candidate.quantity))
        val positive = safeTest(combined, candidate)
        val alternate = items.firstOrNull { it !== candidate && (it.id != candidate.id || it.quantity != candidate.quantity) }
        val negative = alternate?.let { !safeTest(combined, it) } ?: true
        return if (positive && negative) {
            val detail = if (alternate != null) "and() required matching id and quantity" else "and() matched candidate; no contrasting item observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "and() combination failed (pass=$positive negate=$negative)")
        }
    }

    private fun testOr(items: List<GroundItem>): Outcome {
        val first = items.firstOrNull() ?: return Outcome(Status.SKIPPED, "No ground items for or() test")
        val second = items.firstOrNull { it !== first && it.id != first.id }
            ?: return Outcome(Status.SKIPPED, "Only one distinct id available for or() test")
        val query = GroundItemQuery.newQuery().id(first.id).or(GroundItemQuery.newQuery().id(second.id))
        val positiveFirst = safeTest(query, first)
        val positiveSecond = safeTest(query, second)
        val detail = "or() matched ids ${first.id} and ${second.id}"
        return if (positiveFirst && positiveSecond) {
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "or() combination failed (first=$positiveFirst second=$positiveSecond)")
        }
    }

    private fun testInvert(items: List<GroundItem>): Outcome {
        val candidate = items.firstOrNull() ?: return Outcome(Status.SKIPPED, "No ground items for invert() test")
        val base = GroundItemQuery.newQuery().id(candidate.id)
        val inverted = base.invert()
        val negative = !safeTest(inverted, candidate)
        val alternate = items.firstOrNull { it.id != candidate.id }
        val positive = alternate?.let { safeTest(inverted, it) } ?: true
        return if (negative && positive) {
            val detail = if (alternate != null) "invert() rejected id ${candidate.id} and accepted ${alternate.id}" else "invert() rejected id ${candidate.id}; no alternate id observed"
            Outcome(Status.PASSED, detail)
        } else {
            Outcome(Status.FAILED, "invert() failed (pass=$positive negate=${!negative})")
        }
    }

    private fun testMark(): Outcome {
        val query = GroundItemQuery.newQuery()
        val marked = query.mark()
        return if (marked === query) {
            Outcome(Status.PASSED, "mark() preserved query instance")
        } else {
            Outcome(Status.FAILED, "mark() returned a different instance")
        }
    }

    private fun safeTest(query: GroundItemQuery, item: GroundItem): Boolean =
        runCatching { query.test(item) }.getOrDefault(false)

    private fun safeType(item: GroundItem): ItemDefinition? =
        runCatching { item.type }.getOrNull()

    private fun safeCategory(item: GroundItem): Int? =
        runCatching { item.category }.getOrNull()

    private fun safeName(item: GroundItem): String? =
        runCatching { item.name }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun safeStackType(item: GroundItem): StackType? =
        runCatching { item.stackType }.getOrNull()

    private fun safeCoordinate(item: GroundItem): Coordinate? =
        runCatching { item.stack?.coordinate }.getOrNull()

    private fun safeValidity(item: GroundItem): Boolean? =
        runCatching { item.stack?.isValid }.getOrNull()
}

