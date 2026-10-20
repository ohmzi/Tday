package com.ohmz.tday.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Realtime "something changed, refetch" signals pushed over /ws. The class
 * discriminator is serialized as `type`; clients match on its prefix
 * (`todo.`, `list.`, …), so the @SerialName values are the wire contract.
 * Events carry no entity payloads — clients refetch through the normal API,
 * which enforces access.
 */
@Serializable
sealed class DomainEvent {
    @Serializable
    @SerialName("todo.changed")
    data class TodoChanged(val listId: String? = null) : DomainEvent()

    @Serializable
    @SerialName("floater.changed")
    data class FloaterChanged(val listId: String? = null) : DomainEvent()

    @Serializable
    @SerialName("list.changed")
    data class ListChanged(val listId: String? = null) : DomainEvent()

    @Serializable
    @SerialName("floaterList.changed")
    data class FloaterListChanged(val listId: String? = null) : DomainEvent()

    @Serializable
    @SerialName("list.members")
    data class MembersChanged(val listId: String? = null) : DomainEvent()

    @Serializable
    @SerialName("completed.changed")
    data class CompletedChanged(val listId: String? = null) : DomainEvent()
}
