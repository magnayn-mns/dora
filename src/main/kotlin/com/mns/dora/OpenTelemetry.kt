package com.mns.dora

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import java.util.concurrent.TimeUnit

fun sdk(): OpenTelemetry {
    val spanExporter = OtlpGrpcSpanExporter
        .builder()
        .setEndpoint("https://api.honeycomb.io/v1/traces")
        .addHeader("x-honeycomb-team","Kx1MPofNP2yefngVDRjwve")
        .addHeader("x-honeycomb-dataset", "my-metrics")
        .setTimeout(10, TimeUnit.SECONDS)
        .build();

    val spanProcessor = BatchSpanProcessor.builder(spanExporter)
        .setScheduleDelay(100, TimeUnit.MILLISECONDS)
        .build();

    val serviceNameResource: Resource = Resource
        .create(Attributes.of(ResourceAttributes.SERVICE_NAME, "hpdlp"))

    val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(spanProcessor)
        .addSpanProcessor(baggageHandler())
        .setSampler(Sampler.alwaysOn())
        .setResource(Resource.getDefault().merge(serviceNameResource)).build()
    val openTelemetrySdk: OpenTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider)

        .buildAndRegisterGlobal()

    Runtime.getRuntime().addShutdownHook(Thread { tracerProvider.shutdown() })

    return openTelemetrySdk
}