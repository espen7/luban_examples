package luban

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Supplier
import kotlin.math.max

class ByteBuf(
    private var data: ByteArray = EMPTY_BYTES,
    private var beginPos: Int = 0,
    private var endPos: Int = 0
) {
    private var capacity: Int

    constructor(initCapacity: Int) : this(ByteArray(initCapacity), 0, 0)

    constructor(data: ByteArray) : this(data, 0, data.size)

    init {
        this.capacity = data.size
    }

    fun replace(data: ByteArray, beginPos: Int, endPos: Int) {
        this.data = data
        this.beginPos = beginPos
        this.endPos = endPos
        this.capacity = data.size
    }

    fun replace(data: ByteArray) {
        this.data = data
        this.beginPos = 0
        this.capacity = data.size
        this.endPos = this.capacity
    }

    fun sureRead(n: Int) {
        if (beginPos + n > endPos) {
            throw SerializationException("read not enough")
        }
    }

    private fun chooseNewSize(originSize: Int, needSize: Int): Int {
        var newSize = max(originSize, 12)
        while (newSize < needSize) {
            newSize = newSize * 3 / 2
        }
        return newSize
    }

    fun sureWrite(n: Int) {
        if (endPos + n > capacity) {
            val curSize = endPos - beginPos
            val needSize = curSize + n
            if (needSize > capacity) {
                capacity = chooseNewSize(capacity, needSize)
                val newData = ByteArray(capacity)
                System.arraycopy(data, beginPos, newData, 0, curSize)
                data = newData
            } else {
                System.arraycopy(data, beginPos, data, 0, curSize)
            }
            beginPos = 0
            endPos = curSize
        }
    }

    fun writeSize(x: Int) {
        writeCompactUint(x)
    }

    fun readSize(): Int {
        return readCompactUint()
    }

    fun writeShort(x: Short) {
        writeCompactShort(x)
    }

    fun readShort(): Short {
        return readCompactShort()
    }

    fun readCompactShort(): Short {
        sureRead(1)
        val h = (data[beginPos].toInt() and 0xff)
        if (h < 0x80) {
            beginPos++
            return h.toShort()
        } else if (h < 0xc0) {
            sureRead(2)
            val x = ((h and 0x3f) shl 8) or (data[beginPos + 1].toInt() and 0xff)
            beginPos += 2
            return x.toShort()
        } else if ((h == 0xff)) {
            sureRead(3)
            val x = ((data[beginPos + 1].toInt() and 0xff) shl 8) or (data[beginPos + 2].toInt() and 0xff)
            beginPos += 3
            return x.toShort()
        } else {
            throw SerializationException("exceed max short")
        }
    }

    fun writeCompactShort(x: Short) {
        if (x >= 0) {
            if (x < 0x80) {
                sureWrite(1)
                data[endPos++] = x.toByte()
                return
            } else if (x < 0x4000) {
                sureWrite(2)
                data[endPos + 1] = x.toByte()
                data[endPos] = ((x.toInt() shr 8) or 0x80).toByte()
                endPos += 2
                return
            }
        }
        sureWrite(3)
        data[endPos] = 0xff.toByte()
        data[endPos + 2] = x.toByte()
        data[endPos + 1] = (x.toInt() shr 8).toByte()
        endPos += 3
    }

    fun readCompactInt(): Int {
        sureRead(1)
        val h = data[beginPos].toInt() and 0xff
        if (h < 0x80) {
            beginPos++
            return h
        } else if (h < 0xc0) {
            sureRead(2)
            val x = ((h and 0x3f) shl 8) or (data[beginPos + 1].toInt() and 0xff)
            beginPos += 2
            return x
        } else if (h < 0xe0) {
            sureRead(3)
            val x =
                ((h and 0x1f) shl 16) or ((data[beginPos + 1].toInt() and 0xff) shl 8) or (data[beginPos + 2].toInt() and 0xff)
            beginPos += 3
            return x
        } else if (h < 0xf0) {
            sureRead(4)
            val x =
                ((h and 0x0f) shl 24) or ((data[beginPos + 1].toInt() and 0xff) shl 16) or ((data[beginPos + 2].toInt() and 0xff) shl 8) or (data[beginPos + 3].toInt() and 0xff)
            beginPos += 4
            return x
        } else {
            sureRead(5)
            val x =
                ((data[beginPos + 1].toInt() and 0xff) shl 24) or ((data[beginPos + 2].toInt() and 0xff) shl 16) or ((data[beginPos + 3].toInt() and 0xff) shl 8) or (data[beginPos + 4].toInt() and 0xff)
            beginPos += 5
            return x
        }
    }

    fun writeCompactInt(x: Int) {
        if (x >= 0) {
            if (x < 0x80) {
                sureWrite(1)
                data[endPos++] = x.toByte()
                return
            } else if (x < 0x4000) {
                sureWrite(2)
                data[endPos + 1] = x.toByte()
                data[endPos] = ((x shr 8) or 0x80).toByte()
                endPos += 2
                return
            } else if (x < 0x200000) {
                sureWrite(3)
                data[endPos + 2] = x.toByte()
                data[endPos + 1] = (x shr 8).toByte()
                data[endPos] = ((x shr 16) or 0xc0).toByte()
                endPos += 3
                return
            } else if (x < 0x10000000) {
                sureWrite(4)
                data[endPos + 3] = x.toByte()
                data[endPos + 2] = (x shr 8).toByte()
                data[endPos + 1] = (x shr 16).toByte()
                data[endPos] = ((x shr 24) or 0xe0).toByte()
                endPos += 4
                return
            }
        }
        sureWrite(5)
        data[endPos] = 0xf0.toByte()
        data[endPos + 4] = x.toByte()
        data[endPos + 3] = (x shr 8).toByte()
        data[endPos + 2] = (x shr 16).toByte()
        data[endPos + 1] = (x shr 24).toByte()
        endPos += 5
    }

    fun readCompactLong(): Long {
        sureRead(1)
        val h = data[beginPos].toInt() and 0xff
        if (h < 0x80) {
            beginPos++
            return h.toLong()
        } else if (h < 0xc0) {
            sureRead(2)
            val x = ((h and 0x3f) shl 8) or (data[beginPos + 1].toInt() and 0xff)
            beginPos += 2
            return x.toLong()
        } else if (h < 0xe0) {
            sureRead(3)
            val x =
                ((h and 0x1f) shl 16) or ((data[(beginPos + 1)].toInt() and 0xff) shl 8) or (data[(beginPos + 2)].toInt() and 0xff)
            beginPos += 3
            return x.toLong()
        } else if (h < 0xf0) {
            sureRead(4)
            val x =
                ((h and 0x0f) shl 24) or ((data[(beginPos + 1)].toInt() and 0xff) shl 16) or ((data[(beginPos + 2)].toInt() and 0xff) shl 8) or (data[(beginPos + 3)].toInt() and 0xff)
            beginPos += 4
            return x.toLong()
        } else if (h < 0xf8) {
            sureRead(5)
            val xl =
                (data[(beginPos + 1)].toInt() shl 24) or ((data[(beginPos + 2)].toInt() and 0xff) shl 16) or ((data[(beginPos + 3)].toInt() and 0xff) shl 8) or (data[(beginPos + 4)].toInt() and 0xff)
            val xh = h and 0x07
            beginPos += 5
            return (xh.toLong() shl 32) or (xl.toLong() and 0xffffffffL)
        } else if (h < 0xfc) {
            sureRead(6)
            val xl =
                (data[(beginPos + 2)].toInt() shl 24) or ((data[(beginPos + 3)].toInt() and 0xff) shl 16) or ((data[(beginPos + 4)].toInt() and 0xff) shl 8) or (data[(beginPos + 5)].toInt() and 0xff)
            val xh = ((h and 0x03) shl 8) or (data[(beginPos + 1)].toInt() and 0xff)
            beginPos += 6
            return (xh.toLong() shl 32) or (xl.toLong() and 0xffffffffL)
        } else if (h < 0xfe) {
            sureRead(7)
            val xl =
                (data[(beginPos + 3)].toInt() shl 24) or ((data[(beginPos + 4)].toInt() and 0xff) shl 16) or ((data[(beginPos + 5)].toInt() and 0xff) shl 8) or (data[(beginPos + 6)].toInt() and 0xff)
            val xh =
                ((h and 0x01) shl 16) or ((data[(beginPos + 1)].toInt() and 0xff) shl 8) or (data[(beginPos + 2)].toInt() and 0xff)
            beginPos += 7
            return (xh.toLong() shl 32) or (xl.toLong() and 0xffffffffL)
        } else if (h < 0xff) {
            sureRead(8)
            val xl =
                (data[(beginPos + 4)].toInt() shl 24) or ((data[(beginPos + 5)].toInt() and 0xff) shl 16) or ((data[(beginPos + 6)].toInt() and 0xff) shl 8) or (data[(beginPos + 7)].toInt() and 0xff)
            val xh = /*((h & 0x0) << 16) | */
                ((data[(beginPos + 1)].toInt() and 0xff) shl 16) or ((data[(beginPos + 2)].toInt() and 0xff) shl 8) or (data[(beginPos + 3)].toInt() and 0xff)
            beginPos += 8
            return (xh.toLong() shl 32) or (xl.toLong() and 0xffffffffL)
        } else {
            sureRead(9)
            val xl =
                (data[(beginPos + 5)].toInt() shl 24) or ((data[(beginPos + 6)].toInt() and 0xff) shl 16) or ((data[(beginPos + 7)].toInt() and 0xff) shl 8) or (data[(beginPos + 8)].toInt() and 0xff)
            val xh =
                (data[(beginPos + 1)].toInt() shl 24) or ((data[(beginPos + 2)].toInt() and 0xff) shl 16) or ((data[(beginPos + 3)].toInt() and 0xff) shl 8) or (data[(beginPos + 4)].toInt() and 0xff)
            beginPos += 9
            return (xh.toLong() shl 32) or (xl.toLong() and 0xffffffffL)
        }
    }

    fun writeCompactLong(x: Long) {
        if (x >= 0) {
            if (x < 0x80) {
                sureWrite(1)
                data[(endPos++)] = x.toByte()
                return
            } else if (x < 0x4000) {
                sureWrite(2)
                data[(endPos + 1)] = x.toByte()
                data[(endPos)] = ((x shr 8) or 0x80L).toByte()
                endPos += 2
                return
            } else if (x < 0x200000) {
                sureWrite(3)
                data[(endPos + 2)] = x.toByte()
                data[(endPos + 1)] = (x shr 8).toByte()
                data[(endPos)] = ((x shr 16) or 0xc0L).toByte()
                endPos += 3
                return
            } else if (x < 0x10000000) {
                sureWrite(4)
                data[(endPos + 3)] = x.toByte()
                data[(endPos + 2)] = (x shr 8).toByte()
                data[(endPos + 1)] = (x shr 16).toByte()
                data[(endPos)] = ((x shr 24) or 0xe0L).toByte()
                endPos += 4
                return
            } else if (x < 0x800000000L) {
                sureWrite(5)
                data[(endPos + 4)] = x.toByte()
                data[(endPos + 3)] = (x shr 8).toByte()
                data[(endPos + 2)] = (x shr 16).toByte()
                data[(endPos + 1)] = (x shr 24).toByte()
                data[(endPos)] = ((x shr 32) or 0xf0L).toByte()
                endPos += 5
                return
            } else if (x < 0x40000000000L) {
                sureWrite(6)
                data[(endPos + 5)] = x.toByte()
                data[(endPos + 4)] = (x shr 8).toByte()
                data[(endPos + 3)] = (x shr 16).toByte()
                data[(endPos + 2)] = (x shr 24).toByte()
                data[(endPos + 1)] = (x shr 32).toByte()
                data[(endPos)] = ((x shr 40) or 0xf8L).toByte()
                endPos += 6
                return
            } else if (x < 0x200000000000L) {
                sureWrite(7)
                data[(endPos + 6)] = x.toByte()
                data[(endPos + 5)] = (x shr 8).toByte()
                data[(endPos + 4)] = (x shr 16).toByte()
                data[(endPos + 3)] = (x shr 24).toByte()
                data[(endPos + 2)] = (x shr 32).toByte()
                data[(endPos + 1)] = (x shr 40).toByte()
                data[(endPos)] = ((x shr 48) or 0xfcL).toByte()
                endPos += 7
                return
            } else if (x < 0x100000000000000L) {
                sureWrite(8)
                data[(endPos + 7)] = x.toByte()
                data[(endPos + 6)] = (x shr 8).toByte()
                data[(endPos + 5)] = (x shr 16).toByte()
                data[(endPos + 4)] = (x shr 24).toByte()
                data[(endPos + 3)] = (x shr 32).toByte()
                data[(endPos + 2)] = (x shr 40).toByte()
                data[(endPos + 1)] = (x shr 48).toByte()
                data[(endPos)] = 0xfe.toByte()
                endPos += 8
                return
            }
        }
        sureWrite(9)
        data[(endPos + 8)] = x.toByte()
        data[(endPos + 7)] = (x shr 8).toByte()
        data[(endPos + 6)] = (x shr 16).toByte()
        data[(endPos + 5)] = (x shr 24).toByte()
        data[(endPos + 4)] = (x shr 32).toByte()
        data[(endPos + 3)] = (x shr 40).toByte()
        data[(endPos + 2)] = (x shr 48).toByte()
        data[(endPos + 1)] = (x shr 56).toByte()
        data[(endPos)] = 0xff.toByte()
        endPos += 9
    }

    fun readCompactUint(): Int {
        val n = readCompactInt()
        if (n >= 0) {
            return n
        } else {
            throw SerializationException("unmarshal CompactUnit")
        }
    }

    fun writeCompactUint(x: Int) {
        writeCompactInt(x)
    }

    //    public void writeCompactUint(ByteBuf byteBuf, int x) {
    //        if (x >= 0) {
    //            if (x < 0x80) {
    //                byteBuf.writeByte(x);
    //            } else if (x < 0x4000) {
    //                byteBuf.writeShort(x | 0x8000);
    //            } else if (x < 0x200000) {
    //                byteBuf.writeMedium(x | 0xc00000);
    //            } else if (x < 0x10000000) {
    //                byteBuf.writeInt(x | 0xe0000000);
    //            } else {
    //                throw new RuntimeException("exceed max unit");
    //            }
    //        }
    //    }
    fun readInt(): Int {
        return readCompactInt()
    }

    fun writeInt(x: Int) {
        writeCompactInt(x)
    }

    fun readLong(): Long {
        return readCompactLong()
    }

    fun writeLong(x: Long) {
        writeCompactLong(x)
    }

    fun writeSint(x: Int) {
        writeInt((x shl 1) or (x ushr 31))
    }

    fun readSint(): Int {
        val x = readInt()
        return (x ushr 1) or ((x and 1) shl 31)
    }

    fun writeSlong(x: Long) {
        writeLong((x shl 1) or (x ushr 63))
    }

    fun readSlong(): Long {
        val x = readLong()
        return (x ushr 1) or ((x and 1L) shl 63)
    }

    fun readFshort(): Short {
        sureRead(4)
        val x = (data[(beginPos)].toInt() and 0xff) or ((data[(beginPos + 1)].toInt() and 0xff) shl 8)
        beginPos += 2
        return x.toShort()
    }

    fun writeFshort(x: Short) {
        sureWrite(2)
        data[(endPos)] = (x.toInt() and 0xff).toByte()
        data[(endPos + 1)] = ((x.toInt() shr 8) and 0xff).toByte()
        endPos += 2
    }

    fun readFint(): Int {
        sureRead(4)
        val x =
            (data[(beginPos)].toInt() and 0xff) or ((data[(beginPos + 1)].toInt() and 0xff) shl 8) or ((data[(beginPos + 2)].toInt() and 0xff) shl 16) or ((data[(beginPos + 3)].toInt() and 0xff) shl 24)
        beginPos += 4
        return x
    }

    fun writeFint(x: Int) {
        sureWrite(4)
        data[(endPos)] = (x and 0xff).toByte()
        data[(endPos + 1)] = ((x shr 8) and 0xff).toByte()
        data[(endPos + 2)] = ((x shr 16) and 0xff).toByte()
        data[(endPos + 3)] = ((x shr 24) and 0xff).toByte()
        endPos += 4
    }

    fun readFlong(): Long {
        sureRead(8)
        val x =
            ((data[(beginPos + 7)].toLong() and 0xffL) shl 56) or ((data[(beginPos + 6)].toLong() and 0xffL) shl 48) or ((data[(beginPos + 5)].toLong() and 0xffL) shl 40) or ((data[(beginPos + 4)].toLong() and 0xffL) shl 32) or ((data[(beginPos + 3)].toLong() and 0xffL) shl 24) or ((data[(beginPos + 2)].toLong() and 0xffL) shl 16) or ((data[(beginPos + 1)].toLong() and 0xffL) shl 8) or (data[(beginPos)].toLong() and 0xffL)
        beginPos += 8
        return x
    }

    fun writeFlong(x: Long) {
        sureWrite(8)
        data[(endPos + 7)] = (x shr 56).toByte()
        data[(endPos + 6)] = (x shr 48).toByte()
        data[(endPos + 5)] = (x shr 40).toByte()
        data[(endPos + 4)] = (x shr 32).toByte()
        data[(endPos + 3)] = (x shr 24).toByte()
        data[(endPos + 2)] = (x shr 16).toByte()
        data[(endPos + 1)] = (x shr 8).toByte()
        data[(endPos)] = x.toByte()
        endPos += 8
    }

    fun readFloat(): Float {
        return java.lang.Float.intBitsToFloat(readFint())
    }

    fun writeFloat(z: Float) {
        writeFint(java.lang.Float.floatToIntBits(z))
    }

    fun readDouble(): Double {
        return java.lang.Double.longBitsToDouble(readFlong())
    }

    fun writeDouble(z: Double) {
        writeFlong(java.lang.Double.doubleToLongBits(z))
    }

    fun readString(): String {
        val n = readSize()
        if (n > 0) {
            sureRead(n)
            val start = beginPos
            beginPos += n
            return String(data, start, n, MARSHAL_CHARSET)
        } else {
            return ""
        }
    }

    fun writeString(x: String) {
        if (x.length > 0) {
            val bytes = x.toByteArray(MARSHAL_CHARSET)
            val n = bytes.size
            writeCompactUint(n)
            sureWrite(n)
            System.arraycopy(bytes, 0, data, endPos, n)
            endPos += n
        } else {
            writeCompactUint(0)
        }
    }

    fun writeOctets(o: ByteBuf) {
        val n = o.size()
        writeCompactUint(n)
        if (n > 0) {
            sureWrite(n)
            System.arraycopy(o.data, o.beginPos, this.data, this.endPos, n)
            this.endPos += n
        }
    }

    fun readOctets(): ByteBuf {
        val n = readSize()
        sureRead(n)
        val start = beginPos
        beginPos += n
        return ByteBuf(Arrays.copyOfRange(data, start, beginPos))
    }

    fun readOctets(o: ByteBuf): ByteBuf {
        val n = readSize()
        sureRead(n)
        val start = beginPos
        beginPos += n
        o.sureWrite(n)
        System.arraycopy(data, start, o.data, o.endPos, n)
        o.endPos += n
        return o
    }

    fun readBytes(): ByteArray? {
        val n = readSize()
        if (n > 0) {
            sureRead(n)
            val start = beginPos
            beginPos += n
            return Arrays.copyOfRange(data, start, beginPos)
        } else {
            return EMPTY_BYTES
        }
    }

    fun writeBytes(x: ByteArray) {
        val n = x.size
        writeCompactUint(n)
        if (n > 0) {
            sureWrite(n)
            System.arraycopy(x, 0, data, endPos, n)
            endPos += n
        }
    }

    fun readBool(): Boolean {
        sureRead(1)
        return data[(beginPos++)].toInt() != 0
    }

    fun writeBool(x: Boolean) {
        sureWrite(1)
        data[(endPos++)] = if (x) 1.toByte() else 0
    }

    fun readByte(): Byte {
        sureRead(1)
        return data[(beginPos++)]
    }

    fun writeByte(x: Byte) {
        sureWrite(1)
        data[endPos++] = x
    }

    //    public void writeTo(ByteBuf byteBuf) {
    //        int n = size();
    //        writeCompactUint(byteBuf, n);
    //        byteBuf.writeBytes(data, beginPos, n);
    //    }
    fun writeTo(os: ByteBuf) {
        val n = size()
        os.writeCompactUint(n)
        os.sureWrite(n)
        System.arraycopy(data, beginPos, os.data, os.endPos, n)
        os.endPos += n
    }

    //    public void readFrom(ByteBuf byteBuf) {
    //        int n = byteBuf.readableBytes();
    //        sureWrite(n);
    //        byteBuf.readBytes(data, endPos, n);
    //        endPos += n;
    //    }
    fun wrapRead(src: ByteBuf, size: Int) {
        this.data = src.data
        this.beginPos = src.beginPos
        src.beginPos += size
        this.endPos = src.beginPos
        this.capacity = src.capacity
    }

    fun clear() {
        beginPos = 0
        endPos = 0
    }

    fun size(): Int {
        return endPos - beginPos
    }

    fun empty(): Boolean {
        return endPos == beginPos
    }

    fun nonEmpty(): Boolean {
        return endPos > beginPos
    }

    fun readerIndex(): Int {
        return beginPos
    }

    fun rollbackReadIndex(readerMark: Int) {
        beginPos = readerMark
    }

    fun skip(n: Int) {
        sureRead(n)
        beginPos += n
    }

    fun skipBytes() {
        val n = readSize()
        sureRead(n)
        beginPos += n
    }

    fun array(): ByteArray {
        return data
    }

    fun copyRemainData(): ByteArray? {
        return Arrays.copyOfRange(data, beginPos, endPos)
    }

    override fun toString(): String {
        val b = StringBuilder()
        for (i in beginPos..<endPos) {
            b.append(data[i].toInt()).append(",")
        }
        return b.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ByteBuf) return false
        val o = other
        if (size() != o.size()) return false
        for (i in beginPos..<endPos) {
            if (data[i] != o.data[o.beginPos + i - beginPos]) return false
        }
        return true
    }

    companion object {
        val EMPTY_BYTES: ByteArray = ByteArray(0)
        private val MARSHAL_CHARSET: Charset = StandardCharsets.UTF_8

        //    public static Octets wrap(byte[] bytes) {
        //        return new Octets(bytes, 0, bytes.length);
        //    }
        //
        //    public static Octets wrap(byte[] bytes, int beginPos, int len) {
        //        return new Octets(bytes, beginPos, beginPos + len);
        //    }
        private val pool: ThreadLocal<Stack<ByteBuf?>> = ThreadLocal.withInitial<Stack<ByteBuf?>>(Supplier { Stack() })
        fun alloc(): ByteBuf? {
            val p = pool.get()
            if (!p.empty()) {
                return p.pop()
            } else {
                return ByteBuf()
            }
        }

        fun free(os: ByteBuf): ByteBuf? {
            os.clear()
            return pool.get().push(os)
        }

        fun fromString(value: String): ByteBuf {
            if (value.isEmpty()) {
                return ByteBuf()
            }
            val ss: Array<String?> = value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val data = ByteArray(ss.size)
            for (i in data.indices) {
                data[i] = ss[i]!!.toInt().toByte()
            }
            return ByteBuf(data)
        }
    }

    override fun hashCode(): Int {
        var result = beginPos
        result = 31 * result + endPos
        result = 31 * result + capacity
        result = 31 * result + data.contentHashCode()
        return result
    }
}
