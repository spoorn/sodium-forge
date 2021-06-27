package me.jellysquid.mods.sodium.client.render.chunk.compile;

import com.google.common.primitives.Floats;
import it.unimi.dsi.fastutil.ints.IntArrays;
import me.jellysquid.mods.sodium.client.gl.SodiumVertexFormats;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadEncoder;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadViewMutable;
import me.jellysquid.mods.sodium.client.model.quad.sink.ModelQuadSink;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.BitSet;

/**
 * An optimized resizeable buffer for writing rendered quad data that will be later used for chunk mesh rendering. Since
 * chunks only perform one kind of transformation (a translation), the expensive matrix operations can be eliminated.
 */
public class ChunkMeshBuilder implements ModelQuadSink {
    /**
     * The encoder used to serialize model quads into the specified vertex format for consumption by the graphics card.
     */
    private final ModelQuadEncoder encoder;

    /**
     * The collection of sprites used by the vertex data in this builder.
     */
    private ChunkRenderData.Builder renderData;

    /**
     * The size of each written quad in bytes. This is always 4 times the stride of the vertex format.
     */
    private final int stride;

    /**
     * The backing direct buffer in the current platform's native byte order which holds the buffered vertex data.
     */
    private ByteBuffer buffer;

    /**
     * The current pointer into the backing buffer which marks the head at which data will be written into next.
     */
    private int position;

    /**
     * The maximum capacity of the backing buffer in bytes before it needs to be resized.
     */
    private int capacity;

    /**
     * The translation to be applied to all quads written into this mesh builder.
     */
    private float x, y, z;

    /**
     * The scale to be applied to all offsets and quads written into this mesh builder.
     */
    private final float scale;

    private int numQuads;

    public ChunkMeshBuilder(GlVertexFormat<?> format, int initialSize) {
        this.scale = 1.0f / 32.0f;
        this.stride = format.getStride() * 4;
        this.encoder = SodiumVertexFormats.getEncoder(format);

        this.buffer = GLAllocation.createDirectByteBuffer(initialSize);
        this.capacity = initialSize;
        this.numQuads = 0;
    }

    public void begin(ChunkRenderData.Builder renderData) {
        if (this.renderData != null) {
            throw new IllegalStateException("Not finished building!");
        }

        this.renderData = renderData;
    }

    public void setOffset(int x, int y, int z) {
        this.x = x * this.scale;
        this.y = y * this.scale;
        this.z = z * this.scale;
    }

    @Override
    public void write(ModelQuadViewMutable quad) {
        // Mark the write pointer we will be using
        int position = this.position;
        int len = this.stride;

        // Advance the write pointer by the number of bytes we're about to write
        this.position += len;

        // If the write pointer is now outside the capacity of the backing buffer, grow it to accommodate the incoming data
        if (this.position >= this.capacity) {
            this.grow(len);
        }

        // Translate the quad to its local position in the chunk
        for (int i = 0; i < 4; i++) {
            quad.setX(i, (quad.getX(i) * this.scale) + this.x);
            quad.setY(i, (quad.getY(i) * this.scale) + this.y);
            quad.setZ(i, (quad.getZ(i) * this.scale) + this.z);
        }

        numQuads++;
        // Write the quad to the backing buffer using the marked position from earlier
        this.encoder.write(quad, this.buffer, position);

        TextureAtlasSprite TextureAtlasSprite = quad.getSprite();

        if (TextureAtlasSprite != null) {
            this.renderData.addSprite(TextureAtlasSprite);
        }
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

    /**
     * @return True if no vertex data exists in this buffer, otherwise false
     */
    public boolean isEmpty() {
        return this.position <= 0;
    }

    // TODO: For some reason, this still causes certain angles to not render correctly
    public void sortQuads(float x, float y, float z) {
        x = x * this.scale;
        y = y * this.scale;
        z = z * this.scale;

        sortStandardFormat(x, y, z);
        // TODO: sort in compact format
        //sortCompactFormat(x, y, z);
    }

    private void sortStandardFormat(float x, float y, float z) {
        FloatBuffer floatBuffer = this.buffer.asFloatBuffer();

        int quadStride = this.stride/4;

        int quadStart = 0;
        int quadCount = this.numQuads;
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

    /**
     * Ends the stream of written data and makes a copy of it to be passed around.
     */
    public void copyInto(ByteBuffer dst) {
        if (this.renderData != null) {
            throw new IllegalStateException("Not finished building");
        }

        // Mark the slice of memory that needs to be copied
        this.buffer.position(0);
        this.buffer.limit(this.position);

        // Allocate a new buffer which is just large enough to contain the slice of vertex data
        // The buffer is then flipped after the operation so the callee sees a range of bytes from (0,len] which can
        // then be immediately passed to native libraries or the graphics driver
        dst.put(this.buffer.slice());

        // Reset the position and limit set earlier of the backing scratch buffer
        this.buffer.clear();
        this.position = 0;
        this.numQuads = 0;
    }

    public int getSize() {
        return this.position;
    }

    public void finish() {
        this.renderData = null;
    }
}
