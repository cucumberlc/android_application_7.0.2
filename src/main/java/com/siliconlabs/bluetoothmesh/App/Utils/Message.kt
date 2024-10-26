/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import android.content.Context
import androidx.annotation.StringRes
import com.siliconlab.bluetoothmesh.adk.errors.MeshError
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Utils.Message.*

sealed class Message(val level: Level) : MessageBearer {
    abstract val message: String

    private var titleContent: Message? = null

    fun setTitle(title: String) {
        check(titleContent == null) { "Title can only be set once" }
        titleContent = info(title)
    }

    internal fun setTitle(@StringRes titleRes: Int) {
        check(titleContent == null) { "Title can only be set once" }
        titleContent = info(titleRes)
    }

    /** OPTIONAL message title. */
    val title: String?
        get() = titleContent?.message

    // copy with different level
    internal fun copy(level: Level) = createCopy(level).also { copy ->
        copy.titleContent = titleContent
    }

    protected abstract fun createCopy(level: Level): Message

    class Raw(override val message: String, level: Level) : Message(level) {
        override fun createCopy(level: Level) = Raw(message, level)
    }

    class StringResource(@StringRes val messageRes: Int, level: Level) :
        Message(level) {
        override val message: String
            get() = MeshApplication.appContext.getString(messageRes)

        override fun createCopy(level: Level) = StringResource(messageRes, level)
    }

    class AdkMeshError(val meshError: MeshError, level: Level) : Message(level) {
        override val message: String
            get() = meshError.toString()

        override fun createCopy(level: Level) = AdkMeshError(meshError, level)
    }

    class Throwable(val throwable: kotlin.Throwable, level: Level) : Message(level) {
        override val message: String
            get() = throwable.message ?: throwable::class.java.name

        override fun createCopy(level: Level) = Throwable(throwable, level)
    }

    class LazyString(loadMessage: Context.() -> String, level: Level) : Message(level) {
        private var load : (Context.() -> String)? = loadMessage
        override val message: String by lazy {
            load = null
            MeshApplication.appContext.loadMessage()
        }
        override fun createCopy(level: Level) = when(val l = load) {
            null -> Raw(message, level)
            else -> LazyString(l, level)
        }
    }

    enum class Level {
        INFO,
        ERROR,    // default level
        CRITICAL
    }

    // satisfy MessageBearer interface but its not intended to unfold Message from Message itself
    @Deprecated("Unintended messageContent chain", level = DeprecationLevel.HIDDEN)
    override val messageContent
        get() = this

    // static factory
    companion object : MessageBearer.FactoryBase<Message>() {
        val EMPTY by lazy { error("") }
        override fun create(message: Message) = message
    }
}

interface MessageBearer {
    val messageContent: Message

    /** MessageBearer that's also an exception */
    abstract class Exception(override val messageContent: Message) :
        MessageBearer, RuntimeException(messageContent.message) {
        // guaranteed to have non-null message
        final override val message: String
            get() = super.message!!
    }

    /** This factory can be extended by message bearing classes companion object to provide constructor-like invocation. */
    abstract class Factory<T : MessageBearer>(
        private val defaultLevel: Level = Level.ERROR,
    ) : FactoryBase<T>() {
        operator fun invoke(message: String) = invoke(message, defaultLevel)
        operator fun invoke(@StringRes messageRes: Int) = invoke(messageRes, defaultLevel)
        operator fun invoke(meshError: MeshError) = invoke(meshError, defaultLevel)
        operator fun invoke(throwable: Throwable) = invoke(throwable, defaultLevel)
    }

    /** Factory that does not contain default level */
    abstract class FactoryBase<T : MessageBearer> {
        protected abstract fun create(message: Message): T
        fun from(message: MessageBearer) = create(message.messageContent)

        operator fun invoke(
            message: String,
            level: Level,
        ) = create(Raw(message, level))

        operator fun invoke(
            @StringRes messageRes: Int,
            level: Level,
        ) = create(StringResource(messageRes, level))

        operator fun invoke(
            meshError: MeshError,
            level: Level,
        ) = create(AdkMeshError(meshError, level))

        operator fun invoke(
            throwable: Throwable,
            level: Level,
        ) = create(Throwable(throwable, level))

        // level is reversed here for lambda invocation
        operator fun invoke(
            level: Level,
            loadMessage: Context.() -> String,
        ) = create(LazyString(loadMessage, level))

        // nested fixed level builders
        @MessageFactory
        val info: FixedLevel<T> = SingleLevelFactory(Level.INFO)

        @MessageFactory
        val error: FixedLevel<T> = SingleLevelFactory(Level.ERROR)

        @MessageFactory
        val critical: FixedLevel<T> = SingleLevelFactory(Level.CRITICAL)

        interface FixedLevel<T : MessageBearer> {
            @MessageFactory
            operator fun invoke(message: String): T

            @MessageFactory
            operator fun invoke(@StringRes messageRes: Int): T

            @MessageFactory
            operator fun invoke(throwable: Throwable): T

            @MessageFactory
            operator fun invoke(meshError: MeshError): T

            @MessageFactory
            operator fun invoke(loadMessage: Context.() -> String): T
        }

        private inner class SingleLevelFactory(private val level: Level) : FixedLevel<T> {
            override fun invoke(message: String) = invoke(message, level)
            override fun invoke(messageRes: Int) = invoke(messageRes, level)
            override fun invoke(throwable: Throwable) = invoke(throwable, level)
            override fun invoke(meshError: MeshError) = invoke(meshError, level)
            override fun invoke(loadMessage: Context.() -> String) = invoke(level, loadMessage)
        }
    }
}

// this recolors SingleLevelFactories so they don't look like properties
@DslMarker
annotation class MessageFactory

val MessageBearer.message
    get() = messageContent.message

val MessageBearer.isInfo
    get() = messageContent.level == Level.INFO

val MessageBearer.isError
    get() = messageContent.level == Level.ERROR

val MessageBearer.isCritical
    get() = messageContent.level == Level.CRITICAL

fun <M : MessageBearer> M.withTitle(title: String) = apply { messageContent.setTitle(title) }
fun <M : MessageBearer> M.withTitle(@StringRes titleRes: Int) = apply {
    messageContent.setTitle(titleRes)
}

@Suppress("UNCHECKED_CAST")
fun <M : Message> M.withLevel(level: Level): M =
    if (level == this.level) this else copy(level) as M

fun <M : Message> M.asInfo() = withLevel(Level.INFO)