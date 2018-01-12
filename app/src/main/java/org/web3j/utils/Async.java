package org.web3j.utils;


import org.web3j.protocol.core.methods.response.TransactionReceipt;
//import org.web3j.tx.Contract;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Async task facilitation.
 */
public class Async {

    private static ScheduledExecutorService executorService;

    static {
        executorService = defaultExecutorService();
    }

    public static <T> Future<T> run(Callable<T> callable) {
        return executorService.submit(callable);
    }
/*

    public static <T> CompletableFuture<T> xrun(final Callable<T> callable) {
        final CompletableFuture<T> result = new CompletableFuture<>();

        CompletableFuture.runAsync(new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    result.complete(callable.call());
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            }
        });
        /*
        CompletableFuture.runAsync(() -> {
            // we need to explicityly catch any exceptions,
            // otherwise they will be silently discarded
            try {
                result.complete(callable.call());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        }); * /
        return result;
    }*/

    private static int getCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static ScheduledExecutorService defaultExecutorService() {
        return Executors.newScheduledThreadPool(getCpuCount());
    }
}
