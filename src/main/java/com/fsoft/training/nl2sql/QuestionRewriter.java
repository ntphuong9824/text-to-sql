package com.fsoft.training.nl2sql;

import com.fsoft.training.llm.LlmClient;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QuestionRewriter {

    private final LlmClient llm;
    private final PromptBuilder prompts;

    public QuestionRewriter(LlmClient llm, PromptBuilder prompts) {
        this.llm = llm;
        this.prompts = prompts;
    }

    /** Returns a standalone question; with empty history the input is returned without calling the LLM. */
    public String rewrite(String question, List<TurnContext> history) {
        if (history == null || history.isEmpty()) {
            return question;
        }
        String out = llm.complete(prompts.buildRewrite(question, history));
        String trimmed = out == null ? "" : out.strip();
        return trimmed.isEmpty() ? question : trimmed;
    }
}
