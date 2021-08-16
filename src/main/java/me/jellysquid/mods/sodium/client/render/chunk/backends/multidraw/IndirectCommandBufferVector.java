package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import org.lwjgl.system.MemoryUtil;

import java.nio.Buffer;

public class IndirectCommandBufferVector extends StructBuffer {
    protected IndirectCommandBufferVector(int capacity) {
        super(capacity, 16);
    }

    public static IndirectCommandBufferVector create(int capacity) {
        return new IndirectCommandBufferVector(capacity);
    }

    public void begin() {
        ((Buffer)this.buffer).clear();
    }

    public void end() {
        ((Buffer)this.buffer).flip();
    }

    public void pushCommandBuffer(ChunkDrawCallBatcher batcher) {
        int len = batcher.getArrayLength();

        if (this.buffer.remaining() < len) {
            this.growBuffer(len);
        }

        this.buffer.put(batcher.getBuffer());
    }

    protected void growBuffer(int n) {
        this.buffer = MemoryUtil.memRealloc(this.buffer, Math.max(this.buffer.capacity() * 2, this.buffer.capacity() + n));
    }
}