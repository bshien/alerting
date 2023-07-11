/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.alerting.model

import org.opensearch.common.io.stream.StreamInput
import org.opensearch.common.io.stream.StreamOutput
import org.opensearch.commons.alerting.alerts.AlertError
import org.opensearch.core.xcontent.ToXContent
import org.opensearch.core.xcontent.XContentBuilder
import org.opensearch.script.ScriptException
import java.io.IOException
import java.time.Instant

data class ChainedAlertTriggerRunResult(
    override var triggerName: String,
    var triggered: Boolean,
    override var error: Exception?,
    var actionResults: MutableMap<String, ActionRunResult> = mutableMapOf(),
    val associatedAlertIds: Set<String>,
) : TriggerRunResult(triggerName, error) {

    @Throws(IOException::class)
    @Suppress("UNCHECKED_CAST")
    constructor(sin: StreamInput) : this(
        triggerName = sin.readString(),
        error = sin.readException(),
        triggered = sin.readBoolean(),
        actionResults = sin.readMap() as MutableMap<String, ActionRunResult>,
        associatedAlertIds = sin.readStringList().toSet()
    )

    override fun alertError(): AlertError? {
        if (error != null) {
            return AlertError(Instant.now(), "Failed evaluating trigger:\n${error!!.userErrorMessage()}")
        }
        for (actionResult in actionResults.values) {
            if (actionResult.error != null) {
                return AlertError(Instant.now(), "Failed running action:\n${actionResult.error.userErrorMessage()}")
            }
        }
        return null
    }

    override fun internalXContent(builder: XContentBuilder, params: ToXContent.Params): XContentBuilder {
        if (error is ScriptException) error = Exception((error as ScriptException).toJsonString(), error)
        return builder
            .field("triggered", triggered)
            .field("action_results", actionResults as Map<String, ActionRunResult>)
    }

    @Throws(IOException::class)
    override fun writeTo(out: StreamOutput) {
        super.writeTo(out)
        out.writeBoolean(triggered)
        out.writeMap(actionResults as Map<String, ActionRunResult>)
        out.writeStringCollection(associatedAlertIds)
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun readFrom(sin: StreamInput): TriggerRunResult {
            return ChainedAlertTriggerRunResult(sin)
        }
    }
}