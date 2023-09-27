package com.mns.dora

import java.time.Duration
import java.time.Instant

interface TimelineVisitor {
    fun visit(tli: TimelineItem)
    fun visitEnd(tli: TimelineItem)
}

class TimelineEvent(val name: String, val at: Instant) {

}

interface TimelineItem {

    val fromInstant: Instant
    val toInstant: Instant?
    val name: String


    val events: List<TimelineEvent>

    val attributes: Map<String, Any>

    val baggage: Map<String, Any>

    fun addEvent(name: String, at: Instant)
    fun accept(v: TimelineVisitor)

    val empty:Boolean

    val duration: Duration? get() { return if(toInstant != null) Duration.between(fromInstant, toInstant) else null }
}

abstract class TimelineBase {
    val events = ArrayList<TimelineEvent>()
    fun addEvent(name: String, at: Instant) {
        events.add(TimelineEvent(name, at))
    }
}

class Timeline : TimelineBase, TimelineItem {
    private var fromTime: Instant
    private var toTime: Instant?
    private val _name: String

    private val children = ArrayList<TimelineItem>()

    override val attributes = HashMap<String, Any>()
    override val baggage = HashMap<String, Any>()


    override val empty:Boolean get() = false
    constructor(name: String, fromTime: Instant, toTime: Instant?) {
        this.fromTime = fromTime
        this.toTime = toTime
        this._name = name
        if (toInstant?.isBefore(fromInstant)?:false)
            throw Exception("Date Error")
    }

    override val fromInstant: Instant
        get() = fromTime

    override val toInstant: Instant?
        get() = toTime
    override val name: String
        get() = _name

    fun add(ti: TimelineItem) {
        if( ti.fromInstant.isBefore(fromTime)) {
            //throw Exception("Before Fail")
            println("Adjusting time on ${this.name} based on ${ti.name}")
            fromTime = ti.fromInstant
        }
        if( ti.toInstant != null && toTime != null && ti.toInstant?.isAfter(toTime)?:false) {
            //   throw Exception("After Fail")
            println("Adjusting time on ${this.name} based on ${ti.name}")
            toTime = ti.toInstant
        }
        children.add(ti)
    }

    override fun accept(v: TimelineVisitor) {
        v.visit(this)
        children.forEach { it.accept(v) }
        v.visitEnd(this)
    }
}

class TimelineParent(val _name: String) : TimelineBase(), TimelineItem {

    private val children = ArrayList<TimelineItem>()
    override val attributes = HashMap<String, Any>()
    override val baggage = HashMap<String, Any>()
    override val empty:Boolean get() = children.isEmpty()
    override val fromInstant: Instant
        get() {
            var from: Instant = Instant.MAX
            children.forEach {
                if (it.fromInstant.isBefore(from))
                    from = it.fromInstant
            }
            return from
        }

    override val toInstant: Instant
        get() {
            var from: Instant = Instant.MIN
            children.forEach {
                if (it.toInstant?.isAfter(from)?:false)
                    from = it.toInstant!!
            }
            return from
        }

    fun add(ti: TimelineItem) {
        children.add(ti)
    }

    override val name: String
        get() = _name

    override fun accept(v: TimelineVisitor) {
        if( empty )
            return

        v.visit(this)

        children.forEach { it.accept(v) }

        v.visitEnd(this)
    }
}