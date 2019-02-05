package org.glassfish.grizzly.http2;

import java.util.List;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueRecord;
import org.glassfish.grizzly.http2.frames.DataFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.utils.ChunkedCompletionHandler;

public class Http2OutputQueueRecord extends AsyncQueueRecord<WriteResult> {

    private final int streamId;

    private ChunkedCompletionHandler chunkedCompletionHandler;
    private final CompletionHandler<WriteResult> originalCompletionHandler;
    private Buffer buffer;
    private final boolean isLast;

    private final boolean isZeroSizeData;

    Http2OutputQueueRecord(final int streamId, final Buffer buffer,
            final CompletionHandler<WriteResult> completionHandler, final boolean isLast) {
        super(null, null, null);

        this.streamId = streamId;
        this.buffer = buffer;
        this.isZeroSizeData = !buffer.hasRemaining();
        this.originalCompletionHandler = completionHandler;
        this.isLast = isLast;
    }

    @Override
    public void notifyFailure(final Throwable e) {
        final CompletionHandler<WriteResult> chLocal = getCompletionHandler();
        if (chLocal != null) {
            chLocal.failed(e);
        }
    }

    @Override
    public void recycle() {
    }

    @Override
    public WriteResult getCurrentResult() {
        return null;
    }

    CompletionHandler<WriteResult> getCompletionHandler() {
        return chunkedCompletionHandler != null ? chunkedCompletionHandler : originalCompletionHandler;
    }

    boolean isZeroSizeData() {
        return isZeroSizeData;
    }

    boolean isFinished() {
        return buffer == null;
    }

    int serializeTo(final List<Http2Frame> frames, final int maxDataSize) {

        final int recordSize = buffer.remaining();

        if (recordSize <= maxDataSize) {
            final DataFrame dataFrame = DataFrame.builder().streamId(streamId).data(buffer).endStream(isLast).build();

            frames.add(dataFrame);

            buffer = null;

            return recordSize;
        } else {
            if (originalCompletionHandler != null && chunkedCompletionHandler == null) {
                chunkedCompletionHandler = new ChunkedCompletionHandler(originalCompletionHandler);
            }

            if (chunkedCompletionHandler != null) {
                chunkedCompletionHandler.incChunks();
            }

            final Buffer remainder = buffer.split(buffer.position() + maxDataSize);

            final DataFrame dataFrame = DataFrame.builder().streamId(streamId).data(buffer).endStream(false).build();

            frames.add(dataFrame);

            buffer = remainder;

            return maxDataSize;
        }
    }
}