package com.mns.dora

import com.apollographql.apollo3.ApolloClient
import com.github.GetTeamMembersQuery
import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.MetricBatchSenderFactory
import com.newrelic.telemetry.OkHttpPoster
import com.newrelic.telemetry.http.HttpPoster
import com.newrelic.telemetry.metrics.Gauge
import com.newrelic.telemetry.metrics.MetricBatchSender
import com.newrelic.telemetry.metrics.MetricBuffer
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit
import java.util.function.Supplier


suspend fun main(vararg args: String) = coroutineScope {

    var bearer = args[0]

        println("Hello world!")
        val apolloClient = ApolloClient.Builder()
            .serverUrl("https://api.github.com/graphql")
            .addHttpHeader("Authorization", "Bearer " + bearer) // .networkTransport(QueueTestNetworkTransport())
            .build()
        val q = GetTeamMembersQuery("price-domain", "DigitalInnovation")

        var resp = apolloClient.query(q).execute();
        println(resp.data)


    }

fun sendMetrics(licenseKey:String) {


    val factory: MetricBatchSenderFactory =
        MetricBatchSenderFactory.fromHttpImplementation(Supplier<HttpPoster> { OkHttpPoster() } as Supplier<HttpPoster?>?)
    val sender: MetricBatchSender =
        MetricBatchSender.create(factory.configureWith(licenseKey).useLicenseKey(true).build())

    val metricBuffer = MetricBuffer(getCommonAttributes())

    for (i in 0..9) {
        val currentTemperature: Gauge = getLTTC()
        println("Recording temperature: $currentTemperature")
        metricBuffer.addMetric(currentTemperature)
        TimeUnit.SECONDS.sleep(5) // 5 seconds between measurements
    }

    sender.sendBatch(metricBuffer.createBatch())
}
private fun getCommonAttributes(): Attributes? {
    return Attributes().put("pr.title", "<title>>")
}

private fun getLTTC(): Gauge {
    return Gauge(
        "lttc",
        100.0,
        System.currentTimeMillis(),
        getLTTCAttributes()
    )
}

private fun getLTTCAttributes(): Attributes? {
    val attributes = Attributes()
    attributes.put("change.startDate", 1)
    attributes.put("change.endDate", 1)

    return attributes
}