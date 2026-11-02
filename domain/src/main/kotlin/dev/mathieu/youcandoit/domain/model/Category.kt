package dev.mathieu.youcandoit.domain.model

import java.time.Instant

data class Category(
    val id: CategoryId,
    val name: String,
    val createdAt: Instant,
)
