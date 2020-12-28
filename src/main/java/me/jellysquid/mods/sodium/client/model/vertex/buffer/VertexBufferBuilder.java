package me.jellysquid.mods.sodium.client.model.vertex.buffer;

import com.google.common.primitives.Floats;
import it.unimi.dsi.fastutil.ints.IntArrays;
import me.jellysquid.mods.sodium.client.gl.attribute.BufferVertexFormat;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelVertexTransformer;
import net.minecraft.client.renderer.GLAllocation;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.BitSet;

public class VertexBufferBuilder implements VertexBufferView {
    private final BufferVertexFormat vertexFormat;

    private ByteBuffer buffer;
    private int writerOffset;
    private int capacity;

    public VertexBufferBuilder(BufferVertexFormat vertexFormat, int initialCapacity) {
        this.vertexFormat = vertexFormat;

        this.buffer = GLAllocation.createDirectByteBuffer(initialCapacity);
        this.capacity = initialCapacity;
        this.writerOffset = 0;
    }

    private void grow(int len) {
        // The new capacity will at least as large as the write it needs to service
        int cap = Math.max(this.capacity * 2, this.capacity + len);

        // Allocate a new buffer and copy the old buffer's contents into it
        ByteBuffer buffer = GLAllocation.createDirectByteBuffer(cap);
        buffer.put(this.buffer);
        buffer.position(0);

        // Update the buffer and capacity now
        this.buffer = buffer;
        this.capacity = cap;
    }

    @Override
    public boolean ensureBufferCapacity(int bytes) {
        if (this.writerOffset + bytes <= this.capacity) {
            return false;
        }

        this.grow(bytes);

        return true;
    }

    @Override
    public ByteBuffer getDirectBuffer() {
        return this.buffer;
    }

    @Override
    public int getWriterPosition() {
        return this.writerOffset;
    }

    @Override
    public void flush(int vertexCount, BufferVertexFormat format) {
        if (this.vertexFormat != format) {
            throw new IllegalStateException("Mis-matched vertex format (expected: [" + format + "], currently using: [" + this.vertexFormat + "])");
        }

        this.writerOffset += vertexCount * format.getStride();
    }

    @Override
    public BufferVertexFormat getVertexFormat() {
        return this.vertexFormat;
    }

    public boolean isEmpty() {
        return this.writerOffset == 0;
    }

    // TODO: For some reason, this still causes certain angles to not render correctly.  It may be due to there being
    // a VertexBufferBuilder for each ModelQuadFacing
    public void sortQuads(float x, float y, float z) {
        // Scale camera to same space as quads
        x = x * ChunkModelVertexTransformer.SCALE_NORM;
        y = y * ChunkModelVertexTransformer.SCALE_NORM;
        z = z * ChunkModelVertexTransformer.SCALE_NORM;

        sortStandardFormat(x, y, z);
        // TODO: sort in compact format
        //sortCompactFormat(x, y, z);
    }

    private void sortStandardFormat(float x, float y, float z) {
        FloatBuffer floatBuffer = this.buffer.asFloatBuffer();

        // Quad stride by Float size
        int quadStride = this.vertexFormat.getStride();

        int quadStart = 0;
        int quadCount = this.writerOffset/quadStride/4;
        int vertexSizeInteger = quadStride / 4;

        float[] distanceArray = new float[quadCount];
        int[] indicesArray = new int[quadCount];

        for (int quadIdx = 0; quadIdx < quadCount; ++quadIdx) {
            distanceArray[quadIdx] = getDistanceSq(floatBuffer, x, y, z, vertexSizeInteger, quadStart + (quadIdx * quadStride));
            indicesArray[quadIdx] = quadIdx;
        }

        IntArrays.mergeSort(indicesArray, (a, b) -> Floats.compare(distanceArray[b], distanceArray[a]));

        BitSet bits = new BitSet();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer tmp = stack.mallocFloat(quadStride);

            for (int l = bits.nextClearBit(0); l < indicesArray.length; l = bits.nextClearBit(l + 1)) {
                int m = indicesArray[l];

                if (m != l) {
                    sliceQuad(floatBuffer, m, quadStride, quadStart);
                    tmp.clear();
                    tmp.put(floatBuffer);

                    int n = m;

                    for (int o = indicesArray[m]; n != l; o = indicesArray[o]) {
                        sliceQuad(floatBuffer, o, quadStride, quadStart);
                        FloatBuffer floatBuffer3 = floatBuffer.slice();

                        sliceQuad(floatBuffer, n, quadStride, quadStart);
                        floatBuffer.put(floatBuffer3);

                        bits.set(n);
                        n = o;
                    }

                    sliceQuad(floatBuffer, l, quadStride, quadStart);
                    tmp.flip();

                    floatBuffer.put(tmp);
                }

                bits.set(l);
            }
        }
    }

    private void sliceQuad(FloatBuffer floatBuffer, int quadIdx, int quadStride, int quadStart) {
        int base = quadStart + (quadIdx * quadStride);

        floatBuffer.limit(base + quadStride);
        floatBuffer.position(base);
    }

    private float getDistanceSq(FloatBuffer buffer, float xCenter, float yCenter, float zCenter, int stride, int start) {
        int vertexBase = start;
        float x1 = buffer.get(vertexBase);
        float y1 = buffer.get(vertexBase + 1);
        float z1 = buffer.get(vertexBase + 2);

        //x1 = ((x1 - this.x) / this.scale);
        /*x1 = x1 + offset.getX();
        y1 = y1 + offset.getY();
        z1 = z1 + offset.getZ();*/
       /* System.out.println("camera: " + xCenter + "," + yCenter + "," + zCenter);
        System.out.println("buffer1: " + x1 + "," + y1 + "," + z1);*/

        vertexBase += stride;
        float x2 = buffer.get(vertexBase);
        float y2 = buffer.get(vertexBase + 1);
        float z2 = buffer.get(vertexBase + 2);
        //System.out.println("buffer2: " + x2 + "," + y2 + "," + z2);

        vertexBase += stride;
        float x3 = buffer.get(vertexBase);
        float y3 = buffer.get(vertexBase + 1);
        float z3 = buffer.get(vertexBase + 2);
        //System.out.println("buffer3: " + x3 + "," + y3 + "," + z3);

        vertexBase += stride;
        float x4 = buffer.get(vertexBase);
        float y4 = buffer.get(vertexBase + 1);
        float z4 = buffer.get(vertexBase + 2);
        //System.out.println("buffer4: " + x4 + "," + y4 + "," + z4);

        float xDist = ((x1 + x2 + x3 + x4) * 0.25F) - xCenter;
        float yDist = ((y1 + y2 + y3 + y4) * 0.25F) - yCenter;
        float zDist = ((z1 + z2 + z3 + z4) * 0.25F) - zCenter;

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }

    public int getSize() {
        return this.writerOffset;
    }

    /**
     * Ends the stream of written data and makes a copy of it to be passed around.
     */
    public void copyInto(ByteBuffer dst) {
        // Mark the slice of memory that needs to be copied
        this.buffer.position(0);
        this.buffer.limit(this.writerOffset);

        // Allocate a new buffer which is just large enough to contain the slice of vertex data
        // The buffer is then flipped after the operation so the callee sees a range of bytes from (0,len] which can
        // then be immediately passed to native libraries or the graphics driver
        dst.put(this.buffer.slice());

        // Reset the position and limit set earlier of the backing scratch buffer
        this.buffer.clear();
        this.writerOffset = 0;
    }
}
