package com.mns.dora

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.baggage.BaggageEntry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import java.time.Instant


fun main(vararg args: String)  {
    var otel = sdk()

    val tracer: Tracer = otel.getTracer("io.opentelemetry.example")

    var spann = tracer.spanBuilder("Build").setStartTimestamp(Instant.now()).setNoParent().startSpan().end(Instant.now());
    Thread.sleep(2000);
}

fun baggageHandler(): SpanProcessor =
    object : SpanProcessor {
        override fun onStart(parentContext: Context?, span: ReadWriteSpan?) {
            Baggage.current().asMap().forEach { (s: String?, baggageEntry: BaggageEntry) ->
                var value = baggageEntry.value;

                if( value.startsWith("%%int%%")) {
                    span!!.setAttribute(
                        s,
                        baggageEntry.value.substring(7).toLong()
                    )
                } else {
                    span!!.setAttribute(
                        s,
                        baggageEntry.value
                    )
                }
            }

        }

        override fun isStartRequired(): Boolean {
            return true
        }

        override fun onEnd(span: ReadableSpan) {

        }

        override fun isEndRequired(): Boolean {
            return false
        }
    }



