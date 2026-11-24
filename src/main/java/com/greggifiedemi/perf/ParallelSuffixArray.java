package com.greggifiedemi.perf;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.searchtree.SuffixArray;

/**
 * A {@link SuffixArray} whose {@link #generate()} sorts on a worker thread, so the four arrays EMI builds when it bakes
 * its search index (names, tooltips, mods, aliases) sort at the same time instead of one after another. Every other
 * method waits for a pending sort first, so no caller can see a half-built array. Not an NEI feature: an EMI load-time
 * fix, ported from the owner's TFG performance fixes ({@code emiParallelSearch}).
 *
 * <p>The worker runs {@code super.generate()}, never this class's {@link #generate()}. The original fix hooked
 * {@code SuffixArray.generate()} itself, so the worker passed through its own hook and could wait on its own task
 * forever, which froze EMI's search.
 */
public final class ParallelSuffixArray<T> extends SuffixArray<T> {

    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)), runnable -> {
                final Thread thread = new Thread(runnable, "Greggified EMI search bake " + THREADS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

    private volatile Future<?> pending;

    @Override
    public void generate() {
        awaitPending();
        this.pending = EXECUTOR.submit(super::generate);
    }

    @Override
    public void add(T value, String text) {
        awaitPending();
        super.add(value, text);
    }

    @Override
    public List<T> search(String text) {
        awaitPending();
        return super.search(text);
    }

    /** Whether the sort has finished (for tests). */
    public boolean isDone() {
        final Future<?> task = this.pending;
        return task == null || task.isDone();
    }

    private void awaitPending() {
        final Future<?> task = this.pending;

        if (task == null) {
            return;
        }

        boolean interrupted = false;

        try {
            while (true) {
                try {
                    task.get();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (e.getCause() instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(e.getCause());
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        if (this.pending == task) {
            this.pending = null;
        }
    }

}
