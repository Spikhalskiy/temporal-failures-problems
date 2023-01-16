package io.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PostProcessingChild {
    @WorkflowMethod
    boolean execute(int id);
}
