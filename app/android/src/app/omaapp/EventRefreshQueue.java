package app.omaapp;

/** Coalesces event refreshes without dropping a different kind while one runs. */
final class EventRefreshQueue {
    static final int THEME = 1;
    static final int FEATURES = 1 << 1;

    private int pending;
    private boolean workerScheduled;

    synchronized boolean enqueue(String kind) {
        if ("theme".equals(kind)) {
            pending |= THEME;
        } else if ("features".equals(kind)) {
            pending |= FEATURES;
        } else {
            return false;
        }
        if (workerScheduled) {
            return false;
        }
        workerScheduled = true;
        return true;
    }

    synchronized int takePending() {
        if (pending == 0) {
            workerScheduled = false;
            return 0;
        }
        int batch = pending;
        pending = 0;
        return batch;
    }
}
