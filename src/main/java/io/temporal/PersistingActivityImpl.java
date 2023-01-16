package io.temporal;

public class PersistingActivityImpl implements PersistingActivity {

    @Override
    public int execute(int implementationNumber, int entityId) {
        return 0;
    }

    @Override
    public void rollback(int implementationNumber, int entityId) {

    }
}
