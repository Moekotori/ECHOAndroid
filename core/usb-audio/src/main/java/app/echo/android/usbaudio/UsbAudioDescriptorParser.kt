package app.echo.android.usbaudio

class UsbAudioDescriptorParser {
    fun parse(rawDescriptors: ByteArray): UsbAudioDescriptorInfo {
        val streamingFormats = mutableListOf<UsbAudioStreamingFormat>()
        val classVersions = linkedSetOf<UsbAudioClassVersion>()
        val audioInterfaces = mutableSetOf<Int>()
        val audioControlInterfaces = mutableSetOf<Int>()
        val audioStreamingInterfaces = mutableSetOf<Int>()
        val clockTopologies = mutableMapOf<Int, ClockTopology>()
        val audioFunctions = mutableMapOf<Int, Int>()
        val inputEndpoints = mutableMapOf<Pair<Int, Int>, MutableList<EndpointContext>>()
        var acInterfaceNumber: Int? = null
        var currentInterface: InterfaceContext? = null
        var currentEndpoint: EndpointContext? = null
        var usbVersion: Int? = null

        var offset = 0
        while (offset + 1 < rawDescriptors.size) {
            val length = rawDescriptors[offset].u8()
            val descriptorType = rawDescriptors[offset + 1].u8()
            if (length < 2 || offset + length > rawDescriptors.size) break

            when (descriptorType) {
                DESCRIPTOR_TYPE_DEVICE -> {
                    if (length >= 4) {
                        usbVersion = rawDescriptors.le16(offset + 2)
                    }
                }

                DESCRIPTOR_TYPE_INTERFACE_ASSOCIATION -> {
                    if (length >= 8 && rawDescriptors[offset + 4].u8() == USB_CLASS_AUDIO) {
                        val first = rawDescriptors[offset + 2].u8()
                        val count = rawDescriptors[offset + 3].u8()
                        if (first + count <= 256) {
                            repeat(count) { audioFunctions[first + it] = first }
                        }
                    }
                }

                DESCRIPTOR_TYPE_INTERFACE -> {
                    currentEndpoint = null
                    currentInterface = parseInterface(rawDescriptors, offset, length)?.takeIf { it.isAudio }
                    currentInterface?.let { context ->
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
                        currentEndpoint = endpoint
                        if (
                            endpoint.direction == UsbEndpointDirection.In &&
                            endpoint.transferType == UsbEndpointTransferType.Isochronous
                        ) {
                            inputEndpoints.getOrPut(context.number to context.alternateSetting, ::mutableListOf)
                                .add(endpoint)
                        }
                        if (endpoint.direction == UsbEndpointDirection.Out &&
                            endpoint.usageType != UsbEndpointUsageType.Feedback) {
                            mergeStreamingFormat(streamingFormats, UsbAudioStreamingFormat(
                                interfaceNumber = context.number,
                                alternateSetting = context.alternateSetting,
                                audioClassVersion = context.version,
                                endpointAddress = endpoint.address,
                                endpointDirection = endpoint.direction,
                                endpointTransferType = endpoint.transferType,
                                endpointSyncType = endpoint.syncType,
                                endpointUsageType = endpoint.usageType,
                                maxPacketSize = endpoint.maxPacketSize,
                                endpointInterval = endpoint.interval,
                                synchAddress = endpoint.synchAddress,
                            ))
                        }
                    }
                }

                DESCRIPTOR_TYPE_CLASS_SPECIFIC_ENDPOINT -> {
                    val context = currentInterface
                    if (context?.subclass == AUDIO_SUBCLASS_STREAMING && context.version == UsbAudioClassVersion.Uac1 &&
                        currentEndpoint?.direction == UsbEndpointDirection.Out && length >= 7 &&
                        rawDescriptors[offset + 2].u8() == AS_GENERAL) {
                        mergeStreamingFormat(streamingFormats, UsbAudioStreamingFormat(
                            interfaceNumber = context.number,
                            alternateSetting = context.alternateSetting,
                            audioClassVersion = context.version,
                            uac1SampleRateControl = rawDescriptors[offset + 3].u8() and 1 != 0,
                        ))
                    }
                }

                DESCRIPTOR_TYPE_CLASS_SPECIFIC_INTERFACE -> {
                    val context = currentInterface
                    when (context?.subclass) {
                        AUDIO_SUBCLASS_CONTROL -> {
                            parseAcClockEntities(
                                rawDescriptors,
                                offset,
                                length,
                                context,
                                clockTopologies.getOrPut(context.number, ::ClockTopology),
                            )
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

        fun controlInterfaceFor(streamingInterface: Int): Int? =
            audioFunctions[streamingInterface]?.takeIf(audioControlInterfaces::contains)
                ?: audioControlInterfaces.singleOrNull()

        val formats = streamingFormats
            .filter { it.alternateSetting > 0 || it.endpointDirection == UsbEndpointDirection.Out }
            .map { format ->
                val inputs = inputEndpoints[format.interfaceNumber to format.alternateSetting].orEmpty()
                val controlNumber = controlInterfaceFor(format.interfaceNumber)
                val topology = controlNumber?.let(clockTopologies::get)
                val linkedClock = format.terminalLink?.let { topology?.terminalClocks?.get(it) }
                // Older firmware can omit the IN bit in bSynchAddress.
                val syncAddress = format.synchAddress?.or(ENDPOINT_DIRECTION_IN)
                val referencedInput = inputs.firstOrNull { it.address == syncAddress }
                val linkedCapture = syncAddress != null && inputEndpoints.any { (key, endpoints) ->
                    val sameFunction = key.first == format.interfaceNumber || audioControlInterfaces.isEmpty() ||
                        (controlNumber != null && controlInterfaceFor(key.first) == controlNumber)
                    sameFunction && endpoints.any {
                        it.address == syncAddress && it.usageType != UsbEndpointUsageType.Feedback
                    }
                }
                val feedback = referencedInput?.takeIf {
                    it.usageType == UsbEndpointUsageType.Feedback ||
                        (it.usageType == UsbEndpointUsageType.Data && (it.maxPacketSize ?: 0) in 3..4)
                } ?: inputs.singleOrNull { !linkedCapture && it.usageType == UsbEndpointUsageType.Feedback }
                val adjacentInterface = format.interfaceNumber + 1
                val adjacentImplicit = format.audioClassVersion == UsbAudioClassVersion.Uac2 &&
                    format.endpointSyncType == UsbEndpointSyncType.Asynchronous && controlNumber != null &&
                    controlInterfaceFor(adjacentInterface) == controlNumber &&
                    inputEndpoints[adjacentInterface to format.alternateSetting].orEmpty().any {
                        it.usageType == UsbEndpointUsageType.ImplicitFeedback
                    }
                val requiresImplicitFeedback = format.isIsochronousOut && feedback == null &&
                    (format.endpointUsageType == UsbEndpointUsageType.ImplicitFeedback ||
                        inputs.any { it.usageType == UsbEndpointUsageType.ImplicitFeedback } ||
                        linkedCapture || adjacentImplicit)
                format.copy(
                    acInterfaceNumber = controlNumber,
                    clockSourceIds = listOfNotNull(linkedClock ?: topology?.sourceIds?.singleOrNull()),
                    clockSelectorSources = topology?.selectors?.toMap().orEmpty(),
                    clockValiditySources = topology?.validitySources?.toSet().orEmpty(),
                    feedbackEndpointAddress = feedback?.address?.takeIf { format.isIsochronousOut },
                    feedbackMaxPacketSize = feedback?.maxPacketSize?.takeIf { format.isIsochronousOut },
                    requiresImplicitFeedback = requiresImplicitFeedback,
                )
            }
            .distinctBy { Triple(it.interfaceNumber, it.alternateSetting, it.endpointAddress) }
        return UsbAudioDescriptorInfo(
            audioInterfaceCount = audioInterfaces.size,
            audioControlInterfaceCount = audioControlInterfaces.size,
            audioStreamingInterfaceCount = audioStreamingInterfaces.size,
            classVersions = classVersions.ifEmpty { setOf(UsbAudioClassVersion.Unknown) },
            streamingFormats = formats,
            hasIsochronousOut = formats.any { it.isIsochronousOut },
            hasFeedbackEndpoint = formats.any { it.feedbackEndpointAddress != null },
            acInterfaceNumber = acInterfaceNumber,
            clockSourceIds = clockTopologies.values.flatMap { it.sourceIds }.distinct(),
            usbVersion = usbVersion,
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
                pcmIntegerSupported = parsed.pcmIntegerSupported ?: existing.pcmIntegerSupported,
                channelCount = parsed.channelCount ?: existing.channelCount,
                subslotSize = parsed.subslotSize ?: existing.subslotSize,
                bitResolution = parsed.bitResolution ?: existing.bitResolution,
                sampleRates = parsed.sampleRates.ifEmpty { existing.sampleRates },
                sampleRateMinHz = parsed.sampleRateMinHz ?: existing.sampleRateMinHz,
                sampleRateMaxHz = parsed.sampleRateMaxHz ?: existing.sampleRateMaxHz,
                endpointAddress = parsed.endpointAddress ?: existing.endpointAddress,
                endpointDirection = parsed.endpointDirection ?: existing.endpointDirection,
                endpointTransferType = parsed.endpointTransferType ?: existing.endpointTransferType,
                endpointSyncType = parsed.endpointSyncType ?: existing.endpointSyncType,
                endpointUsageType = parsed.endpointUsageType ?: existing.endpointUsageType,
                maxPacketSize = parsed.maxPacketSize ?: existing.maxPacketSize,
                endpointInterval = parsed.endpointInterval ?: existing.endpointInterval,
                synchAddress = parsed.synchAddress ?: existing.synchAddress,
                uac1SampleRateControl = parsed.uac1SampleRateControl ?: existing.uac1SampleRateControl,
                terminalLink = parsed.terminalLink ?: existing.terminalLink,
            )
        } else {
            streamingFormats += parsed
        }
    }

    private fun parseAcClockEntities(
        raw: ByteArray,
        offset: Int,
        length: Int,
        context: InterfaceContext,
        topology: ClockTopology,
    ) {
        if (length < 4) return
        val subtype = raw[offset + 2].u8()
        val uac2 = context.version == UsbAudioClassVersion.Uac2 ||
            context.version == UsbAudioClassVersion.Uac3
        if (!uac2) return
        when (subtype) {
            AC_CLOCK_SOURCE -> {
                val id = raw[offset + 3].u8()
                if (id > 0) {
                    topology.sourceIds += id
                    if (context.version == UsbAudioClassVersion.Uac2 && length >= 8 &&
                        raw[offset + 5].u8() and 0x04 != 0) topology.validitySources += id
                }
            }
            AC_CLOCK_SELECTOR -> {
                val id = raw[offset + 3].u8()
                // Preserve even malformed selectors so they cannot be queried as sources.
                val pins = if (length >= 5) raw[offset + 4].u8() else 0
                topology.selectors[id] = if (pins > 0 && length >= 7 + pins)
                    (0 until pins).map { raw[offset + 5 + it].u8() } else emptyList()
            }
            AC_INPUT_TERMINAL -> if (length > 7) {
                recordTerminalClock(
                    terminalId = raw[offset + 3].u8(),
                    clockId = raw[offset + 7].u8(),
                    topology = topology,
                )
            }
            AC_OUTPUT_TERMINAL -> if (length > 8) {
                recordTerminalClock(
                    terminalId = raw[offset + 3].u8(),
                    clockId = raw[offset + 8].u8(),
                    topology = topology,
                )
            }
        }
    }

    private fun recordTerminalClock(
        terminalId: Int,
        clockId: Int,
        topology: ClockTopology,
    ) {
        if (terminalId <= 0 || clockId <= 0) return
        topology.terminalClocks[terminalId] = clockId
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
        val terminalLink = raw[offset + 3].u8().takeIf { it > 0 }
        return UsbAudioStreamingFormat(
            interfaceNumber = context.number,
            alternateSetting = context.alternateSetting,
            audioClassVersion = context.version,
            channelCount = channelCount,
            pcmIntegerSupported = when (context.version) {
                UsbAudioClassVersion.Uac1 -> if (length >= 7) raw.le16(offset + 5) == 1 else null
                UsbAudioClassVersion.Uac2 -> if (length >= 10) {
                    raw[offset + 5].u8() == 1 && raw[offset + 6].u8() and 1 != 0
                } else null
                else -> false
            },
            terminalLink = terminalLink,
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
        val continuous = sampleRateType == 0 && length >= 14
        val sampleRateMinHz: Int?
        val sampleRateMaxHz: Int?
        val sampleRates: List<Int>
        if (continuous) {
            val minHz = raw.le24(offset + 8).takeIf { it > 0 }
            val maxHz = raw.le24(offset + 11).takeIf { it > 0 }
            sampleRateMinHz = minHz
            sampleRateMaxHz = maxHz
            sampleRates = emptyList()
        } else {
            sampleRateMinHz = null
            sampleRateMaxHz = null
            sampleRates = (0 until sampleRateType)
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
            sampleRateMinHz = sampleRateMinHz,
            sampleRateMaxHz = sampleRateMaxHz,
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
            interval = if (length > 6) raw[offset + 6].u8() else null,
            synchAddress = if (length > 8) raw[offset + 8].u8().takeIf { it != 0 } else null,
        )
    }

    private class ClockTopology {
        val sourceIds = linkedSetOf<Int>()
        val terminalClocks = mutableMapOf<Int, Int>()
        val selectors = mutableMapOf<Int, List<Int>>()
        val validitySources = mutableSetOf<Int>()
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
        val interval: Int?,
        val synchAddress: Int?,
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
        const val DESCRIPTOR_TYPE_DEVICE = 0x01
        const val DESCRIPTOR_TYPE_INTERFACE = 0x04
        const val DESCRIPTOR_TYPE_ENDPOINT = 0x05
        const val DESCRIPTOR_TYPE_INTERFACE_ASSOCIATION = 0x0b
        const val DESCRIPTOR_TYPE_CLASS_SPECIFIC_INTERFACE = 0x24
        const val DESCRIPTOR_TYPE_CLASS_SPECIFIC_ENDPOINT = 0x25
        const val AS_GENERAL = 0x01
        const val AS_FORMAT_TYPE = 0x02
        const val AC_INPUT_TERMINAL = 0x02
        const val AC_OUTPUT_TERMINAL = 0x03
        const val AC_CLOCK_SOURCE = 0x0A
        const val AC_CLOCK_SELECTOR = 0x0B
        const val ENDPOINT_DIRECTION_IN = 0x80
        const val ENDPOINT_TRANSFER_MASK = 0x03
    }
}
