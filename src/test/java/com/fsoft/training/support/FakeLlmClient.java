package com.fsoft.training.support;

import com.fsoft.training.llm.ChatMessage;
import com.fsoft.training.llm.LlmClient;
import com.fsoft.training.llm.LlmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/** Scripted LlmClient for tests: queue responses/failures, inspect recorded calls. */
public class FakeLlmClient implements LlmClient {

    private final Deque<Supplier<String>> queue = new ArrayDeque<>();
    private final List<List<ChatMessage>> calls = new ArrayList<>();
    private Runnable beforeEachResponse;

    /** Runs (inside complete) before each scripted response; cleared by reset(). */
    public synchronized void onComplete(Runnable hook) {
        this.beforeEachResponse = hook;
    }

    public synchronized void enqueue(String... responses) {
        for (String response : responses) {
            queue.add(() -> response);
        }
    }

    public synchronized void enqueueFailure(LlmException e) {
        queue.add(() -> {
            throw e;
        });
    }

    public synchronized List<List<ChatMessage>> calls() {
        return List.copyOf(calls);
    }

    public synchronized void reset() {
        queue.clear();
        calls.clear();
        beforeEachResponse = null;
    }

    @Override
    public synchronized String complete(List<ChatMessage> messages) {
        calls.add(List.copyOf(messages));
        if (beforeEachResponse != null) {
            beforeEachResponse.run();
        }
        Supplier<String> next = queue.poll();
        if (next == null) {
            throw new IllegalStateException("FakeLlmClient: no response queued");
        }
        return next.get();
    }
}
