package io.temporal;

public class PostProcessingChildImpl implements PostProcessingChild {
    @Override
    public boolean execute(int id) {
        return true;
    }
}
