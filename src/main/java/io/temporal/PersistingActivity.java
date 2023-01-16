package io.temporal;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface PersistingActivity {
    int execute(int implementationNumber, int entityId);

    void rollback(int implementationNumber, int entityId);
}
