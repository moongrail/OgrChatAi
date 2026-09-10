package com.ogrchatai.app.domain.model

data class Folder(
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val color: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isRoot: Boolean
        get() = parentId == null
}
