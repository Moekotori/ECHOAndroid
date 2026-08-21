package app.echo.android.usbaudio

class UsbAudioDescriptorParser {
    fun parse(rawDescriptors: ByteArray): UsbAudioDescriptorInfo {
        val streamingFormats = mutableListOf<UsbAudioStreamingFormat>()
        val classVersions = linkedSetOf<UsbAudioClassVersion>()
        val audioInterfaces = mutableSetOf<Int>()
        val audioControlInterfaces = mutableSetOf<Int>()
        val audioStreamingInterfaces = mutableSetOf<Int>()
        val clockSourceIds = linkedSetOf<Int>()
        var acInterfaceNumber: Int? = null
        var currentInterface: InterfaceContext? = null
        var feedbackEndpointFound = false

        var offset = 0
        while (offset + 1 < rawDescriptors.size) {
            val length = rawDescriptors[offset].u8()
            val descriptorType = rawDescriptors[offset + 1].u8()
            if (length < 2 || offset + length > rawDescriptors.size) break

            when (descriptorType) {
                DESCRIPTOR_TYPE_INTERFACE -> {
                    currentInterface = parseInterface(rawDescriptors, offset, length)
                    currentInterface?.takeIf { it.isAudio }?.let { context ->
                        audioInterfaces += context.number
                        classVersions += context.version
                        when (context.subclass) {
                            AUDIO_SUBCLASS_CONTROL -> {
                                audioControlInterfaces += context.number
                                if (acInterfaceNumber == null) acInterfaceNumber = context.number
                            }
                            AUDIO_SUBCLASS_STREAMING -> audioStreamingInterfaces += context.number
                        }
                    }
                }

                DESCRIPTOR_TYPE_ENDPOINT -> {
                    val context = currentInterface
                    if (context?.subclass == AUDIO_SUBCLASS_STREAMING) {
                        val endpoint = parseEndpoint(rawDescriptors, offset, length)
                        if (endpoint.direction == UsbEndpointDirection.In &&
                            endpoint.transferType == UsbEndpointTransferType.Isochronous &&
                            endpoint.usageType == UsbEndpointUsageType.Feedback
                        ) {
                            feedbackEndpointFound = true
                            offset += length
                            continue
                        }
                        val lastIndex = streamingFormats.indexOfLast {
                            it.interfaceNumber == context.number && it.alternateSetting == context.alternateSetting
                        }
                        if (lastIndex >= 0) {
                            streamingFormats[lastIndex] = streamingFormats[lastIndex].copy(
                                endpointAddress = endpoint.address,
                                endpointDirection = endpoint.direction,
                                endpointTransferType = endpoint.transferType,
                                endpointSyncType = endpoint.syncType,
                                endpointUsageType = endpoint.usageType,
                                maxPacketSize = endpoint.maxPacketSize,
                            )
                        } else if (endpoint.direction == UsbEndpointDirection.Out) {
                            streamingFormats += UsbAudioStreamingFormat(
                                interfaceNumber = context.number,
                                alternateSetting = context.alternateSetting,
                                audioClassVersion = context.version,
                                endpointAddress = endpoint.address,
                                endpointDirection = endpoint.direction,
                                endpointTransferType = endpoint.transferType,
                                endpointSyncType = endpoint.syncType,
                                endpointUsageType = endpoint.usageType,
                                maxPacketSize = endpoint.maxPacketSize,
                            )
                        }
                    }
                }

                DESCRIPTOR_TYPE_CLASS_SPECIFIC_INTERFACE -> {
                    val context = currentInterface
                    when (context?.subclass) {
                        AUDIO_SUBCLASS_CONTROL -> {
                            parseClockSourceId(rawDescriptors, offset, length)?.let(clockSourceIds::add)
                        }
                        AUDIO_SUBCLASS_STREAMING -> {
                            parseAsGeneral(rawDescriptors, offset, length, context)?.let { parsed ->
                                mergeStreamingFormat(streamingFormats, parsed)
                            }
                            parseStreamingFormat(rawDescriptors, offset, length, context)?.let { parsed ->
                                mergeStreamingFormat(streamingFormats, parsed)
                            }
                        }
                    }
                }
            }

            offset += length
        }

        return UsbAudioDescriptorInfo(
            audioInterfaceCount = audioInterfaces.size,
            audioControlInterfaceCount = audioControlInterfaces.size,
            audioStreamingInterfaceCount = audioStreamingInterfaces.size,
            classVersions = classVersions.ifEmpty { setOf(UsbAudioClassVersion.Unknown) },
            streamingFormats = streamingFormats
                .filter { it.alternateSetting > 0 || it.endpointDirection == UsbEndpointDirection.Out }
                .map { format ->
                    format.copy(
                        acInterfaceNumber = format.acInterfaceNumber ?: acInterfaceNumber,
                        clockSourceIds = format.clockSourceIds.ifEmpty { clockSourceIds.toList() },
                    )
                }
                .distinctBy { "${it.interfaceNumber}:${it.alternateSetting}:${it.endpointAddress}" },
            hasIsochronousOut = streamingFormats.any { it.isIsochronousOut },
            hasFeedbackEndpoint = feedbackEndpointFound,
            acInterfaceNumber = acInterfaceNumber,
            clockSourceIds = clockSourceIds.toList(),
        )
    }

    private fun parseInterface(raw: ByteArray, offset: Int, length: Int): InterfaceContext? {
        if (length < 9) return null
        val interfaceClass = raw[offset + 5].u8()
        val subclass = raw[offset + 6].u8()
        val protocol = raw[offset + 7].u8()
        return InterfaceContext(
            number = raw[offset + 2].u8(),
            alternateSetting = raw[offset + 3].u8(),
            interfaceClass = interfaceClass,
            subclass = subclass,
            protocol = protocol,
            version = protocol.toAudioClassVersion(),
        )
    }

    private fun mergeStreamingFormat(
        streamingFormats: MutableList<UsbAudioStreamingFormat>,
        parsed: UsbAudioStreamingFormat,
    ) {
        val existingIndex = streamingFormats.indexOfLast {
            it.interfaceNumber == parsed.interfaceNumber &&
                it.alternateSetting == parsed.alternateSetting
        }
        if (existingIndex >= 0) {
            val existing = streamingFormats[existingIndex]
            streamingFormats[existingIndex] = existing.copy(
                formatType = parsed.formatType ?: existing.formatType,
                channelCount = parsed.channelCount ?: existing.channelCount,
                subslotSize = parsed.subslotSize ?: existing.subslotSize,
                bitResolution = parsed.bitResolution ?: existing.bitResolution,
                sampleRates = parsed.sampleRates.ifEmpty { existing.sampleRates },
                endpointAddress = parsed.endpointAddress ?: existing.endpointAddress,
                endpointDirection = parsed.endpointDirection ?: existing.endpointDirection,
                endpointTransferType = parsed.endpointTransferType ?: existing.endpointTransferType,
                endpointSyncType = parsed.endpointSyncType ?: existing.endpointSyncType,
                endpointUsageType = parsed.endpointUsageType ?: existing.endpointUsageType,
                maxPacketSize = parsed.maxPacketSize ?: existing.maxPacketSize,
                acInterfaceNumber = parsed.acInterfaceNumber ?: existing.acInterfaceNumber,
                clockSourceIds = parsed.clockSourceIds.ifEmpty { existing.clockSourceIds },
            )
        } else {
            streamingFormats += parsed
        }
    }

    private fun parseClockSourceId(raw: ByteArray, offset: Int, length: Int): Int? {
        if (length < 4) return null
        if (raw[offset + 2].u8() != AC_CLOCK_SOURCE) return null
        val clockId = raw[offset + 3].u8()
        return clockId.takeIf { it > 0 }
    }

    private fun parseAsGeneral(
        raw: ByteArray,
        offset: Int,
        length: Int,
        context: InterfaceContext,
    ): UsbAudioStreamingFormat? {
        if (length < 4) return null
        if (raw[offset + 2].u8() != AS_GENERAL) return null
        val channelCount = when {
            context.version == UsbAudioClassVersion.Uac2 || context.version == UsbAudioClassVersion.Uac3 ->
                if (length > 10) raw[offset + 10].u8().takeIf { it > 0 } else null
            else -> null
        }
        return UsbAudioStreamingFormat(
            interfaceNumber = context.number,
            alternateSetting = context.alternateSetting,
            audioClassVersion = context.version,
            channelCount = channelCount,
        )
    }

    private fun parseStreamingFormat(
        raw: ByteArray,
        offset: Int,
        length: Int,
        context: InterfaceContext,
    ): UsbAudioStreamingFormat? {
        if (length < 4) return null
        val subtype = raw[offset + 2].u8()
        if (subtype != AS_FORMAT_TYPE) return null

        return when (context.version) {
            UsbAudioClassVersion.Uac1,
            UsbAudioClassVersion.Unknown -> parseUac1Format(raw, offset, length, context)
            UsbAudioClassVersion.Uac2,
            UsbAudioClassVersion.Uac3 -> parseUac2Format(raw, offset, length, context)
        }
    }

    private fun parseUac1Format(
        raw: ByteArray,
        offset: Int,
        length: Int,
        context: InterfaceContext,
    ): UsbAudioStreamingFormat? {
        if (length < 8) return null
        val sampleRateType = raw[offset + 7].u8()
        val sampleRates = if (sampleRateType == 0 && length >= 14) {
            listOf(raw.le24(offset + 8), raw.le24(offset + 11)).filter { it > 0 }.distinct()
        } else {
            (0 until sampleRateType)
                .mapNotNull { index ->
                    val rateOffset = offset + 8 + index * 3
                    if (rateOffset + 2 < offset + length) raw.le24(rateOffset) else null
                }
                .filter { it > 0 }
                .distinct()
        }

        return UsbAudioStreamingFormat(
            interfaceNumber = context.number,
            alternateSetting = context.alternateSetting,
            audioClassVersion = context.version,
            formatType = raw[offset + 3].u8(),
            channelCount = raw[offset + 4].u8().takeIf { it > 0 },
            subslotSize = raw[offset + 5].u8().takeIf { it > 0 },
            bitResolution = raw[offset + 6].u8().takeIf { it > 0 },
            sampleRates = sampleRates.sorted(),
        )
    }

    private fun parseUac2Format(
        raw: ByteArray,
        offset: Int,
        length: Int,
        context: InterfaceContext,
    ): UsbAudioStreamingFormat? {
        if (length < 6) return null
        return UsbAudioStreamingFormat(
            interfaceNumber = context.number,
            alternateSetting = context.alternateSetting,
            audioClassVersion = context.version,
            formatType = raw[offset + 3].u8(),
            subslotSize = raw[offset + 4].u8().takeIf { it > 0 },
            bitResolution = raw[offset + 5].u8().takeIf { it > 0 },
        )
    }

    private fun parseEndpoint(raw: ByteArray, offset: Int, length: Int): EndpointContext {
        val address = if (length > 2) raw[offset + 2].u8() else 0
        val attributes = if (length > 3) raw[offset + 3].u8() else 0
        return EndpointContext(
            address = address,
            direction = if ((address and ENDPOINT_DIRECTION_IN) != 0) {
                UsbEndpointDirection.In
            } else {
                UsbEndpointDirection.Out
            },
            transferType = when (attributes and ENDPOINT_TRANSFER_MASK) {
                0 -> UsbEndpointTransferType.Control
                1 -> UsbEndpointTransferType.Isochronous
                2 -> UsbEndpointTransferType.Bulk
                else -> UsbEndpointTransferType.Interrupt
            },
            syncType = when ((attributes shr 2) and 0x03) {
                1 -> UsbEndpointSyncType.Asynchronous
                2 -> UsbEndpointSyncType.Adaptive
                3 -> UsbEndpointSyncType.Synchronous
                else -> UsbEndpointSyncType.None
            },
            usageType = when ((attributes shr 4) and 0x03) {
                1 -> UsbEndpointUsageType.Feedback
                2 -> UsbEndpointUsageType.ImplicitFeedback
                3 -> UsbEndpointUsageType.Reserved
                else -> UsbEndpointUsageType.Data
            },
            maxPacketSize = if (length > 5) raw.le16(offset + 4) else null,
        )
    }

    private data class InterfaceContext(
        val number: Int,
        val alternateSetting: Int,
        val interfaceClass: Int,
        val subclass: Int,
        val protocol: Int,
        val version: UsbAudioClassVersion,
    ) {
        val isAudio: Boolean
            get() = interfaceClass == USB_CLASS_AUDIO
    }

    private data class EndpointContext(
        val address: Int,
        val direction: UsbEndpointDirection,
        val transferType: UsbEndpointTransferType,
        val syncType: UsbEndpointSyncType,
        val usageType: UsbEndpointUsageType,
        val maxPacketSize: Int?,
    )

    private fun Int.toAudioClassVersion(): UsbAudioClassVersion =
        when (this) {
            0x00 -> UsbAudioClassVersion.Uac1
            0x20 -> UsbAudioClassVersion.Uac2
            0x30 -> UsbAudioClassVersion.Uac3
            else -> UsbAudioClassVersion.Unknown
        }

    private fun Byte.u8(): Int = toInt() and 0xff

    private fun ByteArray.le16(offset: Int): Int =
        this[offset].u8() or (this[offset + 1].u8() shl 8)

    private fun ByteArray.le24(offset: Int): Int =
        this[offset].u8() or (this[offset + 1].u8() shl 8) or (this[offset + 2].u8() shl 16)

    private companion object {
        const val USB_CLASS_AUDIO = 0x01
        const val AUDIO_SUBCLASS_CONTROL = 0x01
        const val AUDIO_SUBCLASS_STREAMING = 0x02
        const val DESCRIPTOR_TYPE_INTERFACE = 0x04
        const val DESCRIPTOR_TYPE_ENDPOINT = 0x05
        const val DESCRIPTOR_TYPE_CLASS_SPECIFIC_INTERFACE = 0x24
        const val AS_GENERAL = 0x01
        const val AS_FORMAT_TYPE = 0x02
        const val AC_CLOCK_SOURCE = 0x0A
        const val ENDPOINT_DIRECTION_IN = 0x80
        const val ENDPOINT_TRANSFER_MASK = 0x03
    }
}
