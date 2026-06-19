package net.rpcsx.esde

import net.rpcsx.RPCSX
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Resolved launch target for a PS3 ISO passed down from an upper caller such as ES-DE.
 *
 * [sourceIsoPath] is the original ISO selected by the frontend.
 * [titleId] and [title] come from `PS3_GAME/PARAM.SFO`.
 * [importedGamePath] is the RPCSX-managed game directory that should be booted internally.
 */
data class Ps3EsdeIsoTarget(
    val sourceIsoPath: String,
    val titleId: String,
    val title: String?,
    val importedGamePath: String,
)

/**
 * Minimal ISO-only resolver for ES-DE integration.
 *
 * This intentionally supports only `.iso` input for now to keep the Phase 3 change small and
 * explicit. Other PS3 source formats can be added later once the ES-DE boot path is proven.
 */
object Ps3EsdeIsoResolver {
    private const val SectorSize = 2048
    private val paramSfoPath = listOf("PS3_GAME", "PARAM.SFO")

    /**
     * Resolves an ES-DE PS3 ISO into the RPCSX-managed imported game path.
     *
     * The default [rootDirectory] matches the app's current RPCSX storage root, but tests can
     * inject a temporary root without mutating global emulator state.
     */
    fun resolve(isoPath: String, rootDirectory: String = RPCSX.rootDirectory): Ps3EsdeIsoTarget {
        require(isoPath.endsWith(".iso", ignoreCase = true)) {
            "Only ISO input is supported for ES-DE integration."
        }

        RandomAccessFile(isoPath, "r").channel.use { channel ->
            val primaryVolume = readPrimaryVolumeDescriptor(channel)
            val root = parseDirectoryRecord(sliceBuffer(primaryVolume, 156, primaryVolume.size - 156))
            val paramRecord = findPath(channel, root, paramSfoPath)
                ?: error("Could not locate PS3_GAME/PARAM.SFO inside ISO: $isoPath")

            val paramBytes = readExtent(channel, paramRecord.extentLba, paramRecord.dataLength)
            val sfo = parseParamSfo(paramBytes)
            val titleId = sfo["TITLE_ID"] ?: error("TITLE_ID missing in PARAM.SFO: $isoPath")
            val title = sfo["TITLE"]
            val importedGamePath = File(rootDirectory, "config/games/$titleId").path

            return Ps3EsdeIsoTarget(
                sourceIsoPath = isoPath,
                titleId = titleId,
                title = title,
                importedGamePath = importedGamePath,
            )
        }
    }

    private fun readPrimaryVolumeDescriptor(channel: java.nio.channels.FileChannel): ByteArray {
        val buffer = ByteBuffer.allocate(SectorSize)
        channel.position(16L * SectorSize)
        channel.read(buffer)
        val data = buffer.array()
        require(data[0].toInt() == 1) { "Primary volume descriptor missing at sector 16." }
        require(String(data, 1, 5) == "CD001") { "Unexpected ISO identifier in primary volume descriptor." }
        return data
    }

    private fun findPath(
        channel: java.nio.channels.FileChannel,
        root: DirectoryRecord,
        segments: List<String>,
    ): DirectoryRecord? {
        var current = root
        for ((index, segment) in segments.withIndex()) {
            val children = readDirectory(channel, current)
            val next = children.firstOrNull { it.normalizedName.equals(segment, ignoreCase = true) }
                ?: return null
            if (index < segments.lastIndex && !next.isDirectory) {
                return null
            }
            current = next
        }
        return current
    }

    private fun readDirectory(
        channel: java.nio.channels.FileChannel,
        record: DirectoryRecord,
    ): List<DirectoryRecord> {
        val bytes = readExtent(channel, record.extentLba, record.dataLength)
        val children = mutableListOf<DirectoryRecord>()
        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) {
                offset = ((offset / SectorSize) + 1) * SectorSize
                continue
            }
            val child = parseDirectoryRecord(sliceBuffer(bytes, offset, length))
            if (child.normalizedName != "." && child.normalizedName != "..") {
                children += child
            }
            offset += length
        }
        return children
    }

    private fun readExtent(
        channel: java.nio.channels.FileChannel,
        extentLba: Int,
        dataLength: Int,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(dataLength)
        channel.position(extentLba.toLong() * SectorSize)
        channel.read(buffer)
        return buffer.array()
    }

    private fun parseDirectoryRecord(buffer: ByteBuffer): DirectoryRecord {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val recordLength = buffer.get(0).toInt() and 0xFF
        require(recordLength > 0) { "Empty directory record." }
        val extentLba = buffer.getInt(2)
        val dataLength = buffer.getInt(10)
        val flags = buffer.get(25).toInt() and 0xFF
        val fileIdLength = buffer.get(32).toInt() and 0xFF
        val nameBytes = ByteArray(fileIdLength)
        for (index in 0 until fileIdLength) {
            nameBytes[index] = buffer.get(33 + index)
        }
        val rawName = when {
            fileIdLength == 1 && nameBytes[0].toInt() == 0 -> "."
            fileIdLength == 1 && nameBytes[0].toInt() == 1 -> ".."
            else -> String(nameBytes)
        }
        return DirectoryRecord(
            extentLba = extentLba,
            dataLength = dataLength,
            isDirectory = (flags and 0x02) != 0,
            normalizedName = rawName.substringBefore(';'),
        )
    }

    private fun parseParamSfo(bytes: ByteArray): Map<String, String> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        require(magic == 0x46535000) { "PARAM.SFO magic mismatch." }
        buffer.int
        val keyTableStart = buffer.int
        val dataTableStart = buffer.int
        val entryCount = buffer.int

        val result = linkedMapOf<String, String>()
        for (index in 0 until entryCount) {
            val entryOffset = 20 + (index * 16)
            val keyOffset = littleShort(bytes, entryOffset)
            val dataFormat = littleShort(bytes, entryOffset + 2)
            val dataLength = littleInt(bytes, entryOffset + 4)
            val dataOffset = littleInt(bytes, entryOffset + 12)
            val key = readCString(bytes, keyTableStart + keyOffset)
            if (dataFormat == 0x0204 || dataFormat == 0x0004) {
                val start = dataTableStart + dataOffset
                val valueBytes = bytes.copyOfRange(start, start + dataLength)
                result[key] = valueBytes.toString(Charsets.UTF_8).trimEnd('\u0000')
            }
        }
        return result
    }

    private fun littleShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun littleInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readCString(bytes: ByteArray, offset: Int): String {
        var end = offset
        while (end < bytes.size && bytes[end] != 0.toByte()) {
            end += 1
        }
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    private fun sliceBuffer(bytes: ByteArray, offset: Int, length: Int): ByteBuffer {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(offset)
        buffer.limit(offset + length)
        return buffer.slice()
    }

    private data class DirectoryRecord(
        val extentLba: Int,
        val dataLength: Int,
        val isDirectory: Boolean,
        val normalizedName: String,
    )
}
