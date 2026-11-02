package dev.mathieu.youcandoit.domain.model

@JvmInline
value class TaskId(val value: String)

@JvmInline
value class CategoryId(val value: String)

fun interface IdGenerator {
    fun newId(): String
}

class UuidIdGenerator : IdGenerator {
    override fun newId(): String = java.util.UUID.randomUUID().toString()
}
