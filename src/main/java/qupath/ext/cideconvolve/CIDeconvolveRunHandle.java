package qupath.ext.cideconvolve;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class CIDeconvolveRunHandle {

    private final CompletableFuture<CIDeconvolveResult> future;
    private final AtomicBoolean cancelRequested;

    CIDeconvolveRunHandle(CompletableFuture<CIDeconvolveResult> future, AtomicBoolean cancelRequested) {
        this.future = future;
        this.cancelRequested = cancelRequested;
    }

    public boolean cancel() {
        cancelRequested.set(true);
        return future.cancel(true);
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean isCancelled() {
        return cancelRequested.get() || future.isCancelled();
    }

    public CompletableFuture<CIDeconvolveResult> future() {
        return future;
    }
}
