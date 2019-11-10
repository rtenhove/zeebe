package io.zeebe.protocol.impl.tracing;

import io.opentracing.propagation.Binary;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;

public class SbeTracingAdapter implements Binary {
  private final DirectBuffer view;

  public SbeTracingAdapter(final DirectBuffer view) {
    this.view = view;
  }

  @Override
  public ByteBuffer extractionBuffer() {
    final var offset = view.wrapAdjustment();
    final var buffer = view.byteBuffer();
    if (buffer == null) {
      return ByteBuffer.wrap(view.byteArray(), offset, view.capacity());
    }

    final var position = buffer.position();
    final var extractionBuffer = buffer.position(offset).slice();
    buffer.position(position);

    return extractionBuffer.limit(view.capacity());
  }

  @Override
  public ByteBuffer injectionBuffer(final int length) {
    final var buffer = ByteBuffer.allocateDirect(length);
    view.wrap(buffer, 0, length);
    return buffer;
  }
}
