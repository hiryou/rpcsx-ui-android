package net.rpcsx.esde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class Ps3EsdeIsoResolverTest {

    @Test
    fun resolve_parsesTitleIdAndImportedGamePathFromIso() {
        val tempDir = Files.createTempDirectory("rpcsx-esde-test").toFile()
        val isoFile = File(tempDir, "DuckTales.iso")
        createMinimalPs3Iso(
            isoFile = isoFile,
            titleId = "BLUS31368",
            title = "DuckTales: Remastered",
        )

        val resolved = Ps3EsdeIsoResolver.resolve(
            isoPath = isoFile.absolutePath,
            rootDirectory = tempDir.absolutePath + "/",
        )

        assertEquals(isoFile.absolutePath, resolved.sourceIsoPath)
        assertEquals("BLUS31368", resolved.titleId)
        assertEquals("DuckTales: Remastered", resolved.title)
        assertEquals(
            File(tempDir, "config/games/BLUS31368").path,
            resolved.importedGamePath,
        )
    }

    @Test
    fun resolve_rejectsNonIsoInput() {
        val tempDir = Files.createTempDirectory("rpcsx-esde-test").toFile()
        val badInput = File(tempDir, "DuckTales.pkg")
        badInput.writeText("not an iso")

        val error = runCatching {
            Ps3EsdeIsoResolver.resolve(
                isoPath = badInput.absolutePath,
                rootDirectory = tempDir.absolutePath + "/",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Only ISO input is supported for ES-DE integration.", error?.message)
    }

    private fun createMinimalPs3Iso(
        isoFile: File,
        titleId: String,
        title: String,
    ) {
        val rootLba = 20
        val ps3GameLba = 21
        val paramSfoLba = 22
        val paramSfoBytes = buildParamSfo(
            linkedMapOf(
                "TITLE_ID" to titleId,
                "TITLE" to title,
            ),
        )

        RandomAccessFile(isoFile, "rw").use { raf ->
            raf.setLength((24L * SECTOR_SIZE))

            val pvd = ByteArray(SECTOR_SIZE)
            pvd[0] = 1
            "CD001".toByteArray().copyInto(pvd, 1)
            pvd[6] = 1
            directoryRecord(extentLba = rootLba, dataLength = SECTOR_SIZE, flags = 0x02, fileId = byteArrayOf(0))
                .copyInto(pvd, 156)
            raf.seek(16L * SECTOR_SIZE)
            raf.write(pvd)

            val rootDir = ByteArray(SECTOR_SIZE)
            var offset = 0
            listOf(
                directoryRecord(rootLba, SECTOR_SIZE, 0x02, byteArrayOf(0)),
                directoryRecord(rootLba, SECTOR_SIZE, 0x02, byteArrayOf(1)),
                directoryRecord(ps3GameLba, SECTOR_SIZE, 0x02, "PS3_GAME".toByteArray()),
            ).forEach { record ->
                record.copyInto(rootDir, offset)
                offset += record.size
            }
            raf.seek(rootLba.toLong() * SECTOR_SIZE)
            raf.write(rootDir)

            val ps3GameDir = ByteArray(SECTOR_SIZE)
            offset = 0
            listOf(
                directoryRecord(ps3GameLba, SECTOR_SIZE, 0x02, byteArrayOf(0)),
                directoryRecord(rootLba, SECTOR_SIZE, 0x02, byteArrayOf(1)),
                directoryRecord(paramSfoLba, paramSfoBytes.size, 0x00, "PARAM.SFO;1".toByteArray()),
            ).forEach { record ->
                record.copyInto(ps3GameDir, offset)
                offset += record.size
            }
            raf.seek(ps3GameLba.toLong() * SECTOR_SIZE)
            raf.write(ps3GameDir)

            raf.seek(paramSfoLba.toLong() * SECTOR_SIZE)
            raf.write(paramSfoBytes)
        }
    }

    private fun directoryRecord(
        extentLba: Int,
        dataLength: Int,
        flags: Int,
        fileId: ByteArray,
    ): ByteArray {
        val recordLength = 33 + fileId.size
        val buffer = ByteBuffer.allocate(recordLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0, recordLength.toByte())
        buffer.put(1, 0)
        buffer.putInt(2, extentLba)
        buffer.putInt(10, dataLength)
        buffer.put(25, flags.toByte())
        buffer.putShort(28, 1)
        buffer.putShort(30, 0)
        buffer.put(32, fileId.size.toByte())
        fileId.copyInto(buffer.array(), 33)
        return buffer.array()
    }

    private fun buildParamSfo(entries: LinkedHashMap<String, String>): ByteArray {
        val keyTable = ByteArrayOutput()
        val dataTable = ByteArrayOutput()
        val entryTable = ByteBuffer.allocate(entries.size * 16).order(ByteOrder.LITTLE_ENDIAN)

        entries.forEach { (key, value) ->
            val keyOffset = keyTable.size
            keyTable.writeCString(key)

            val dataOffset = dataTable.size
            val valueBytes = value.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
            dataTable.write(valueBytes)

            entryTable.putShort(keyOffset.toShort())
            entryTable.putShort(0x0204.toShort())
            entryTable.putInt(valueBytes.size)
            entryTable.putInt(valueBytes.size)
            entryTable.putInt(dataOffset)
        }

        val keyTableStart = 20 + entryTable.capacity()
        val dataTableStart = keyTableStart + keyTable.size
        val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x46535000)
        header.putInt(0x00000101)
        header.putInt(keyTableStart)
        header.putInt(dataTableStart)
        header.putInt(entries.size)

        return header.array() + entryTable.array() + keyTable.toByteArray() + dataTable.toByteArray()
    }

    private class ByteArrayOutput {
        private val bytes = mutableListOf<Byte>()
        val size: Int
            get() = bytes.size

        fun write(data: ByteArray) {
            data.forEach { bytes += it }
        }

        fun writeCString(text: String) {
            write(text.toByteArray(Charsets.UTF_8))
            bytes += 0
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }

    private companion object {
        const val SECTOR_SIZE = 2048
    }
}
