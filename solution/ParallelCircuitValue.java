package cp2024.solution;

import cp2024.circuit.CircuitNode;
import cp2024.circuit.CircuitValue;

import java.util.concurrent.atomic.AtomicBoolean;


public class ParallelCircuitValue implements CircuitValue {
    private AtomicBoolean result; // The result of the circuit will be stored here.
    private final CircuitNode root; // The root of the circuit.
    private final ParallelCircuitSolver solver; // The solver that created this value.
    private final NodeSolver nodeSolver; // The NodeSolver that will solve the circuit.
    private final Thread t; // The thread that will solve the circuit.

    public ParallelCircuitValue(CircuitNode root, ParallelCircuitSolver solver) {
        this.result = null;
        this.root = root;
        this.solver = solver;
        this.nodeSolver = new NodeSolver(root, solver);
        this.t = new Thread(nodeSolver);
    }
    @Override
    public boolean getValue() throws InterruptedException {
        if (result != null) {
            return result.get();
        }
        Thread t = new Thread(nodeSolver);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {}
        // If the solver has finished, return the result.
        if (nodeSolver.getResult() != null) {
            result = new AtomicBoolean(nodeSolver.getResult());
            return result.get();
        }
        // If the solver has been stopped, throw an InterruptedException.
        else {
            throw new InterruptedException();
        }
    }

    public Thread getThread() {
        return t;
    }
}
