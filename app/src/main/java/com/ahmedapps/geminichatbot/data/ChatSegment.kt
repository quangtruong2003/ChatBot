// ChatSegment.kt
package com.ahmedapps.geminichatbot.data

import com.google.firebase.firestore.PropertyName

data class ChatSegment(
    @get:PropertyName("id")
    var id: String = "",
    @get:PropertyName("title")
    var title: String = "",
    @get:PropertyName("createdAt")
    var createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromTitle(title: String): ChatSegment {
            return ChatSegment(
                title = title
            )
        }
    }

}
