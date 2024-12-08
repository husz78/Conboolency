package cp2024.solution;

import cp2024.circuit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeSolver implements Runnable{
    private final List<Thread> subthreads; // The threads that will solve the direct children of the node.
    private AtomicBoolean result; // The result of the node will be stored here (null if not solved).
    private final CircuitNode node; // The node that will be solved.
    private final ParallelCircuitSolver solver; // The solver that created this NodeSolver.

    private class Pair {
        private Boolean value;
        private int index;
        Pair(Boolean value, int index) {
            this.index = index;
            this.value = value;
        }
        public Boolean getValue() {
            return value;
        }
        public int getIndex() {
            return index;
        }
    }

    public NodeSolver(CircuitNode node, ParallelCircuitSolver solver) {
        this.subthreads = new ArrayList<>();
        this.result = null;
        this.node = node;
        this.solver = solver;
    }

    @Override
    public void run() {
        solve();
    }

    // Solve the node and store the result in the result field
    private void solve() {
        if (solver.getStopped().get()) {
            interruptSubthreads();
            return;
        }
        if (node.getType() == NodeType.LEAF) {
            try {
                this.result = new AtomicBoolean();
                this.result.set(((LeafNode) node).getValue());
                return;
            } catch (InterruptedException e) {
                this.result = null;
                return;
            }
        }

        CircuitNode[] args = null;
        try {
            args = node.getArgs();
        } catch (InterruptedException e) {
            interruptSubthreads();
            return;
        }
        switch (node.getType()) {
            case IF -> solveIF(args, subthreads, solver);
            case AND -> solveAND(args, subthreads, solver);
            case OR -> solveOR(args, subthreads, solver);
            case GT -> solveGT(args, ((ThresholdNode) node).getThreshold(), subthreads, solver);
            case LT -> solveLT(args, ((ThresholdNode) node).getThreshold(), subthreads, solver);
            case NOT -> solveNOT(args, subthreads, solver);
            default -> throw new RuntimeException("Illegal type " + node.getType());
        };
    }

    private void solveIF(CircuitNode[] args, List<Thread> subthreads, ParallelCircuitSolver solver) {
        BlockingQueue <Pair> queue = new LinkedBlockingQueue<>();
        Boolean[] results = new Boolean[3];
        for (int i = 0; i < 3; i++) {
            results[i] = null;
        }
        for (int i = 0; i < 3; i++) {
            NodeSolver ns = new NodeSolver(args[i], solver);
            int finalI = i;
            Thread t = new Thread(() -> {
                ns.solve();
                Boolean res = ns.getResult();
                if (res == null) {
                    interruptSubthreads();
                    return;
                }
                try {
                    queue.put(new Pair(res, finalI));
                } catch (InterruptedException e) {
                    interruptSubthreads();
                }
            });
            subthreads.add(t);
            t.start();
        }

        for (int i = 0; i < 3; i++) {
            try {
                Pair elem = queue.take();
                if (elem.getIndex() == 0) {
                    results[0] = elem.getValue();
                }
                if (elem.getIndex() == 1) {
                    results[1] = elem.getValue();
                }
                if (elem.getIndex() == 2) {
                    results[2] = elem.getValue();
                }
            } catch (InterruptedException e) {
                interruptSubthreads();
                return;
            }
            if ((results[1] != null && results[2] != null) && ((results[2] && results[1]) || (!results[1] && !results[2]))) {
                this.result = new AtomicBoolean();
                this.result.set(results[2]);
                interruptSubthreads();
                return;
            }
            if (results[0] != null) {
                if (results[0]) {
                    if (results[1] != null) {
                        this.result = new AtomicBoolean();
                        this.result.set(results[1]);
                        interruptSubthreads();
                        return;
                    }
                }
                else {
                    if (results[2] != null) {
                        this.result = new AtomicBoolean();
                        this.result.set(results[2]);
                        interruptSubthreads();
                        return;
                    }
                }
            }
        }
        this.result = new AtomicBoolean();
        this.result.set(results[0] ? results[1] : results[2]);
        interruptSubthreads();
    }

    private void solveAND(CircuitNode[] args, List<Thread> subthreads, ParallelCircuitSolver solver) {
        BlockingQueue<Boolean> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < args.length; i++) {
            NodeSolver ns = new NodeSolver(args[i], solver);
            Thread t = new Thread(() -> {
                ns.solve();
                Boolean res = ns.getResult();
                try {
                    if (res == null) {
                        interruptSubthreads();
                        return;
                    }
                        queue.put(res);
                } catch (InterruptedException e) {
                    interruptSubthreads();
                }
            });
            subthreads.add(t);
            t.start();
        }
        for (int i = 0; i < args.length; i++) {
            try {
                Boolean res = queue.take();
                if (res == false) {
                    interruptSubthreads();
                    this.result = new AtomicBoolean();
                    this.result.set(false);
                    return;
                }
            } catch (InterruptedException e) {
                interruptSubthreads();
                return;
            }
        }
        this.result = new AtomicBoolean();
        this.result.set(true);
        interruptSubthreads();
    }
    private void solveOR(CircuitNode[] args, List<Thread> subthreads, ParallelCircuitSolver solver) {
        BlockingQueue<Boolean> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < args.length; i++) {
            NodeSolver ns = new NodeSolver(args[i], solver);
            Thread t = new Thread(() -> {
                ns.solve();
                Boolean res = ns.getResult();
                if (res == null) {
                    interruptSubthreads();
                    return;
                }
                try {
                    queue.put(res);
                } catch (InterruptedException e) {
                    interruptSubthreads();
                }
            });
            subthreads.add(t);
            t.start();
        }
        for (int i = 0; i < args.length; i++) {
            try {
                Boolean res = queue.take();
                if (res) {
                    interruptSubthreads();
                    this.result = new AtomicBoolean();
                    this.result.set(true);
                    return;
                }
            } catch (InterruptedException e) {
                interruptSubthreads();
                return;
            }
        }
        this.result = new AtomicBoolean();
        this.result.set(false);
        interruptSubthreads();
    }
    private void solveNOT(CircuitNode[] args, List<Thread> subthreads, ParallelCircuitSolver solver) {
        NodeSolver ns = new NodeSolver(args[0], solver);
        Thread t = new Thread(ns);
        subthreads.add(t);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            interruptSubthreads();
            return;
        }
        Boolean res = ns.getResult();
        if (res == null) {
            this.result = null;
            return;
        }
        this.result = new AtomicBoolean();
        this.result.set(!res);
        interruptSubthreads();
    }
    private void solveGT(CircuitNode[] args, int threshold, List<Thread> subthreads, ParallelCircuitSolver solver) {
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < args.length; i++) {
            NodeSolver ns = new NodeSolver(args[i], solver);
            Thread t = new Thread(() -> {
                ns.solve();
                Boolean res = ns.getResult();
                try {
                    if (res == null) queue.put(0);
                    else
                        queue.put(res ? 1 : -1);
                } catch (InterruptedException e) {
                    interruptSubthreads();
                }
            });
            subthreads.add(t);
            t.start();
        }
        int countT = 0;
        int countF = 0;
        for (int i = 0; i < args.length; i++) {
            try {
                int elem = queue.take();
                if (elem == 0) {
                    this.result = null;
                    interruptSubthreads();
                    return;
                }
                if (elem == 1) countT++;
                if (elem == -1) countF++;
                if (countT > threshold) {
                    interruptSubthreads();
                    this.result = new AtomicBoolean();
                    this.result.set(true);
                    return;
                }
                if (countF >= args.length - threshold) {
                    interruptSubthreads();
                    this.result = new AtomicBoolean();
                    this.result.set(false);
                    return;
                }
            } catch (InterruptedException e) {
                interruptSubthreads();
                return;
            }
        }
        this.result = new AtomicBoolean();
        this.result.set(false);
        interruptSubthreads();
    }
    private void solveLT(CircuitNode[] args, int threshold, List<Thread> subthreads, ParallelCircuitSolver solver) {
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < args.length; i++) {
            NodeSolver ns = new NodeSolver(args[i], solver);
            Thread t = new Thread(() -> {
                ns.solve();
                Boolean res = ns.getResult();
                try {
                    if (res == null) queue.put(0);
                    else
                        queue.put(res ? 1 : -1);
                } catch (InterruptedException e) {
                    interruptSubthreads();
                }
            });
            subthreads.add(t);
            t.start();
        }
        int countT = 0;
        int countF = 0;
        for (int i = 0; i < args.length; i++) {
            try {
                int elem = queue.take();
                if (elem == 0) {
                    this.result = null;
                    interruptSubthreads();
                    return;
                }
                if (elem == 1) countT++;
                if (elem == -1) countF++;
                if (countF > args.length - threshold) {
                    interruptSubthreads();
                    this.result = new AtomicBoolean();
                    this.result.set(true);
                    return;
                }
                if (countT >= threshold) {
                    interruptSubthreads();
                    this.result = new AtomicBoolean();
                    this.result.set(false);
                    return;
                }

            } catch (InterruptedException e) {
                interruptSubthreads();
                return;
            }
        }
        this.result = new AtomicBoolean();
        this.result.set(false);
        interruptSubthreads();
    }

    // A method that interrupts all subthreads and waits for them to finish.
    private void interruptSubthreads() {
        for (Thread t2 : subthreads) {
            t2.interrupt();
        }

    }
    public Boolean getResult() {
        if (result == null) return null;
        return result.get();
    }

}
