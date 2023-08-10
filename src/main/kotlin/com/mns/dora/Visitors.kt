package com.mns.dora

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import java.time.Duration
import java.util.*


class DumpVisitor : TimelineVisitor {



    private var stack = Stack<TimelineItem>()

    override fun visit(tli: TimelineItem) {

        println("${indent()}${tli.fromInstant} - ${tli.toInstant} : ${tli.name} [${tli.duration}}]")
        tli.attributes.forEach {
            println("${indent()}     ${it.key}\t${it.value}")
        }
        tli.baggage.forEach {
            println("${indent()}    +${it.key}\t${it.value}")
        }
        tli.events.forEach {
            println("${indent()} - ${it.at} ${it.name}")
        }
        stack.push(tli)
    }

    fun indent():String {
        var s:String = ""
        for(i in 0..stack.size) {
            s = s + " "
        }
        return s
    }

    override fun visitEnd(tli: TimelineItem) {

        stack.pop()
    }

}

class OtelVisitor(val tracer: Tracer) : TimelineVisitor {


    private var spans = Stack<Span>()
    private var scopes = Stack<Scope>()
    private var stack = Stack<TimelineItem>()
    private val currentSpan: Span? get() = if(spans.empty()) null else spans.peek()


    override fun visit(tli: TimelineItem) {


        var spanBuilder = tracer.spanBuilder(tli.name)
            .setStartTimestamp(tli.fromInstant)

        if( currentSpan == null )
            spanBuilder.setNoParent()

        tli.attributes.forEach {

            if (it.value is String) {
                spanBuilder.setAttribute(it.key, it.value as String)
            }
            if (it.value is Long) {
                spanBuilder.setAttribute(it.key, it.value as Long)
            }
            if (it.value is Int) {
                spanBuilder.setAttribute(it.key, (it.value as Int).toLong())
            }
            if (it.value is Duration) {
                spanBuilder.setAttribute(it.key, (it.value as Duration).toMinutes())
            }
        }

        stack.forEach {
            it.baggage.forEach {
                if (it.value is String) {
                    spanBuilder.setAttribute(it.key, it.value as String)
                }
                if (it.value is Long) {
                    spanBuilder.setAttribute(it.key, it.value as Long)
                }
                if (it.value is Int) {
                    spanBuilder.setAttribute(it.key, (it.value as Int).toLong())
                }
                if (it.value is Duration) {
                    spanBuilder.setAttribute(it.key, (it.value as Duration).toMinutes())
                }
            }
        }



        var span = spanBuilder.startSpan();

        tli.events.forEach {
            span.addEvent(it.name, it.at)
        }

        scopes.push(span.makeCurrent())
        spans.push(span)
        stack.push(tli)

    }

    override fun visitEnd(tli: TimelineItem) {
        currentSpan?.end(tli.toInstant)
        scopes.pop().close()
        spans.pop()
        stack.pop()
    }

}