package cp2024.solution;

import cp2024.circuit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParallelCircuitSolver implements CircuitSolver {
    private final AtomicBoolean stopped; // Tells the solver to stop accepting new solve requests.
    private final List<CircuitValue> values; // Stores the values of the circuits that are being solved.

    public ParallelCircuitSolver() {
        this.stopped = new AtomicBoolean(false);
        this.values = new ArrayList<>();
    }

    @Override
    public CircuitValue solve(Circuit c) {
        if (stopped.get()) {
            return new BrokenCircuitValue();
        }
        ParallelCircuitValue circuitValue = new ParallelCircuitValue(c.getRoot(), this);
        values.add(circuitValue);
        return circuitValue;
    }

    @Override
    public void stop() {
        stopped.set(true);
        for (CircuitValue value : values) {
            // Interrupt the thread that is solving the circuit.
            Thread t = ((ParallelCircuitValue) value).getThread();
            t.interrupt();
        }
        for (CircuitValue value : values) {
            // Interrupt the thread that is solving the circuit.
            Thread t = ((ParallelCircuitValue) value).getThread();
            try {
                t.join();
            } catch (InterruptedException e) {}
        }
        values.clear();
    }
    public AtomicBoolean getStopped() {
        return stopped;
    }
}
