package io.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.workflow.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * We have three backend implementations of something.
 * Only one need to succeed, when one is succeeded, we cancel others.
 * If any of others are already completed - it's ok.
 * But a failure of an implementation is never ok and if it happens, we don't run compensation.
 * Compensation of a failed implementation is undefined / not implemented.
 * If workflow is explicitly canceled, we run compensation.
 */
public class HowIsItNowWorkflowImpl implements ExampleWorkflow {

    PersistingActivity activity =
            Workflow.newActivityStub(
                    PersistingActivity.class,
                    ActivityOptions.newBuilder().setTaskQueue(Workflow.getInfo().getTaskQueue())
                            .setScheduleToCloseTimeout(Duration.ofMinutes(1))
                            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                            .build());

    Map<Integer, CancellationScope> outstandingActivities = new HashMap<>();

    @Override
    public boolean execute(int id) {
        Saga saga = new Saga(new Saga.Options.Builder().build());
        try {
            Promise<Integer> promise1 = schedule(1, id, saga);
            Promise<Integer> promise2 = schedule(2, id, saga);
            Promise<Integer> promise3 = schedule(3, id, saga);

            Workflow.sleep(Duration.ofSeconds(10));

            // Cancellation of these activities is normal, failure is not.
            // We need to verify that all three didn't fail.
            try {
                promise1.get();
                // do something if 1st is completed
            } catch (ActivityFailure e) {
                if (e.getCause() instanceof CanceledFailure && !CancellationScope.current().isCancelRequested()) {
                    // it's ok, the activity was cancelled by the workflow, because another activity is finished
                } else {
                    throw e;
                }
            }

            try {
                promise2.get();
                // do something when 2nd is completed
            } catch (ActivityFailure e) {
                if (e.getCause() instanceof CanceledFailure && !CancellationScope.current().isCancelRequested()) {
                    // it's ok, the activity was cancelled by the workflow, because another activity is finished
                } else {
                    throw e;
                }
            }

            try {
                promise3.get();
                // do something when 3rd is completed
            } catch (ActivityFailure e) {
                if (e.getCause() instanceof CanceledFailure && !CancellationScope.current().isCancelRequested()) {
                    // it's ok, the activity was cancelled by the workflow, because another activity is finished
                } else {
                    throw e;
                }
            }

            try {
                return Workflow.newChildWorkflowStub(PostProcessingChild.class).execute(id);
            } catch (ChildWorkflowFailure e) {
                if (e.getCause() instanceof CanceledFailure && CancellationScope.current().isCancelRequested()) {
                    // workflow got cancelled
                    throw e;
                } else {
                    // do something else
                    //
                    //
                    //
                }
            }

            return true;
        } catch (CanceledFailure e) {
            // Exactly the same branch
            Workflow.newDetachedCancellationScope(saga::compensate).run();
            return false;
        } catch (ActivityFailure e) {
            if (CancellationScope.current().isCancelRequested()) {
                // Exactly the same branch
                Workflow.newDetachedCancellationScope(saga::compensate).run();
                return false;
            } else {
                throw e;
            }
        } catch (ChildWorkflowFailure e) {
            if (CancellationScope.current().isCancelRequested()) {
                // Exactly the same branch
                Workflow.newDetachedCancellationScope(saga::compensate).run();
                return false;
            } else {
                throw ApplicationFailure.newFailureWithCause("ChildWorkflowFailure should be caught around the child call", "Bug", e);
            }
        }
    }

    private Promise<Integer> schedule(int implementationId, int id, Saga saga) {
        AtomicReference<Promise<Integer>> result = new AtomicReference<>();
        CancellationScope scope =
                Workflow.newCancellationScope(
                        () -> {
                            Promise<Integer> function = Async.function(activity::execute, implementationId, id);
                            function.thenApply(
                                    r -> {
                                        outstandingActivities.forEach(
                                                (k, v) -> {
                                                    if (k != id) v.cancel();
                                                });
                                        return null;
                                    });
                            result.set(function);
                        });
        saga.addCompensation(activity::rollback, implementationId, id);
        scope.run();
        outstandingActivities.put(id, scope);
        return result.get();
    }
}
