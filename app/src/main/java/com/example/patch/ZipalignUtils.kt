package com.example.patch

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ZipalignUtils {

    private class EntryInfo(
        val name: String,
        val method: Int,
        val time: Long,
        val crc: Long,
        val compressedSize: Long,
        val size: Long,
        val extra: ByteArray?,
        val comment: String?,
        val flags: Int,
        val data: ByteArray
    )

    @Throws(Exception::class)
    fun align(inputApk: File, outputApk: File, alignment: Int = 4) {
        val entries = mutableListOf<EntryInfo>()
        ZipFile(inputApk).use { zip ->
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
                val entry = enumEntries.nextElement()
                val data = zip.getInputStream(entry).use { it.readBytes() }
                entries.add(
                    EntryInfo(
                        name = entry.name,
                        method = entry.method,
                        time = entry.time,
                        crc = entry.crc,
                        compressedSize = entry.compressedSize,
                        size = entry.size,
                        extra = entry.extra,
                        comment = entry.comment,
                        flags = 0,
                        data = data
                    )
                )
            }
        }

        // Write aligned zip to output file
        if (outputApk.exists()) {
            outputApk.delete()
        }

        val localHeaderOffsets = mutableListOf<Long>()
        val centralDirExtras = mutableListOf<ByteArray?>()

        RandomAccessFile(outputApk, "rw").use { raf ->
            for (entry in entries) {
                val headerOffset = raf.filePointer
                localHeaderOffsets.add(headerOffset)

                val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
                val baseExtra = entry.extra ?: ByteArray(0)

                // Calculate padding if STORED
                val padding = if (entry.method == ZipEntry.STORED) {
                    val dataOffset = headerOffset + 30 + nameBytes.size + baseExtra.size
                    val rem = (dataOffset % alignment).toInt()
                    if (rem != 0) alignment - rem else 0
                } else {
                    0
                }

                val finalExtra = if (padding > 0) {
                    baseExtra + ByteArray(padding)
                } else {
                    baseExtra
                }
                centralDirExtras.add(if (baseExtra.isNotEmpty()) baseExtra else null)

                // Write Local File Header (30 bytes + name + extra)
                raf.writeInt(Integer.reverseBytes(0x04034b50)) // LFH signature
                raf.writeShort(java.lang.Short.reverseBytes(20.toShort()).toInt()) // version needed
                raf.writeShort(java.lang.Short.reverseBytes(entry.flags.toShort()).toInt()) // flags
                raf.writeShort(java.lang.Short.reverseBytes(entry.method.toShort()).toInt()) // method
                val dosTime = if (entry.time != -1L) toDosTime(entry.time) else 0L
                raf.writeInt(Integer.reverseBytes(dosTime.toInt())) // mod time/date
                raf.writeInt(Integer.reverseBytes(entry.crc.toInt())) // crc
                raf.writeInt(Integer.reverseBytes(entry.compressedSize.toInt())) // compressed size
                raf.writeInt(Integer.reverseBytes(entry.size.toInt())) // uncompressed size
                raf.writeShort(java.lang.Short.reverseBytes(nameBytes.size.toShort()).toInt()) // name length
                raf.writeShort(java.lang.Short.reverseBytes(finalExtra.size.toShort()).toInt()) // extra length
                raf.write(nameBytes)
                if (finalExtra.isNotEmpty()) {
                    raf.write(finalExtra)
                }

                // Write entry payload data
                raf.write(entry.data)
            }

            // Write Central Directory
            val centralDirStart = raf.filePointer
            for (i in entries.indices) {
                val entry = entries[i]
                val localOffset = localHeaderOffsets[i]
                val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
                val cdExtra = centralDirExtras[i] ?: ByteArray(0)
                val commentBytes = entry.comment?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)

                raf.writeInt(Integer.reverseBytes(0x02014b50)) // CD signature
                raf.writeShort(java.lang.Short.reverseBytes(20.toShort()).toInt()) // version made by
                raf.writeShort(java.lang.Short.reverseBytes(20.toShort()).toInt()) // version needed
                raf.writeShort(java.lang.Short.reverseBytes(entry.flags.toShort()).toInt()) // flags
                raf.writeShort(java.lang.Short.reverseBytes(entry.method.toShort()).toInt()) // method
                val dosTime = if (entry.time != -1L) toDosTime(entry.time) else 0L
                raf.writeInt(Integer.reverseBytes(dosTime.toInt())) // mod time/date
                raf.writeInt(Integer.reverseBytes(entry.crc.toInt())) // crc
                raf.writeInt(Integer.reverseBytes(entry.compressedSize.toInt())) // compressed size
                raf.writeInt(Integer.reverseBytes(entry.size.toInt())) // uncompressed size
                raf.writeShort(java.lang.Short.reverseBytes(nameBytes.size.toShort()).toInt()) // name length
                raf.writeShort(java.lang.Short.reverseBytes(cdExtra.size.toShort()).toInt()) // extra length
                raf.writeShort(java.lang.Short.reverseBytes(commentBytes.size.toShort()).toInt()) // comment length
                raf.writeShort(0) // disk number start
                raf.writeShort(0) // internal file attributes
                raf.writeInt(0) // external file attributes
                raf.writeInt(Integer.reverseBytes(localOffset.toInt())) // local header offset
                raf.write(nameBytes)
                if (cdExtra.isNotEmpty()) {
                    raf.write(cdExtra)
                }
                if (commentBytes.isNotEmpty()) {
                    raf.write(commentBytes)
                }
            }

            val centralDirEnd = raf.filePointer
            val centralDirSize = centralDirEnd - centralDirStart

            // End of Central Directory record (22 bytes)
            raf.writeInt(Integer.reverseBytes(0x06054b50)) // EOCD signature
            raf.writeShort(0) // disk number
            raf.writeShort(0) // disk with CD
            raf.writeShort(java.lang.Short.reverseBytes(entries.size.toShort()).toInt()) // entries on disk
            raf.writeShort(java.lang.Short.reverseBytes(entries.size.toShort()).toInt()) // total entries
            raf.writeInt(Integer.reverseBytes(centralDirSize.toInt())) // size of CD
            raf.writeInt(Integer.reverseBytes(centralDirStart.toInt())) // offset of CD
            raf.writeShort(0) // comment length
        }
    }

    private fun toDosTime(time: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = time
        val year = cal.get(java.util.Calendar.YEAR)
        return if (year < 1980) {
            (1 shl 21) or (1 shl 16)
        } else {
            (((year - 1980) shl 25) or
                    ((cal.get(java.util.Calendar.MONTH) + 1) shl 21) or
                    (cal.get(java.util.Calendar.DAY_OF_MONTH) shl 16) or
                    (cal.get(java.util.Calendar.HOUR_OF_DAY) shl 11) or
                    (cal.get(java.util.Calendar.MINUTE) shl 5) or
                    (cal.get(java.util.Calendar.SECOND) shr 1)).toLong()
        }
    }
}
