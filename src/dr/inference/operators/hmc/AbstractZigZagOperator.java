package dr.inference.operators.hmc;

import dr.inference.hmc.GradientWrtParameterProvider;
import dr.inference.hmc.PrecisionColumnProvider;
import dr.inference.hmc.PrecisionMatrixVectorProductProvider;
import dr.inference.loggers.LogColumn;
import dr.inference.loggers.Loggable;
import dr.inference.loggers.NumberColumn;
import dr.inference.model.Parameter;
import dr.math.matrixAlgebra.ReadableVector;
import dr.math.matrixAlgebra.WrappedVector;
import dr.util.TaskPool;

import java.util.function.BinaryOperator;

/**
 * @author Zhenyu Zhang
 * @author Marc A. Suchard
 */
abstract class AbstractZigZagOperator extends AbstractParticleOperator implements Loggable {

    AbstractZigZagOperator(GradientWrtParameterProvider gradientProvider,
                           PrecisionMatrixVectorProductProvider multiplicationProvider,
                           PrecisionColumnProvider columnProvider,
                           double weight, Options runtimeOptions, Parameter mask,
                           int threadCount) {

        super(gradientProvider, multiplicationProvider, columnProvider, weight, runtimeOptions, mask);
        this.taskPool = (threadCount > 1) ? new TaskPool(gradientProvider.getDimension(), threadCount) : null;
    }

    abstract WrappedVector drawInitialVelocity(WrappedVector momentum);

    abstract void updatePositionAndPossiblyMomentum(WrappedVector position,
                                                    WrappedVector velocity,
                                                    WrappedVector action,
                                                    WrappedVector gradient,
                                                    WrappedVector momentum,
                                                    double time);


    abstract void updateDynamics(double[] p,
                                 double[] v,
                                 double[] a,
                                 double[] g,
                                 double[] m,
                                 double[] c,
                                 double time,
                                 int index);

    abstract void reflectPossiblyMomentum(WrappedVector position,
                                          WrappedVector momentum,
                                          Type eventType,
                                          int eventIndex);

    BounceState doBounce(BounceState initialBounceState,
                               MinimumTravelInformation firstBounce,
                               WrappedVector position, WrappedVector velocity,
                               WrappedVector action, WrappedVector gradient, WrappedVector momentum) {

        if (TIMING) {
            timer.startTimer("doBounce");
        }

        double remainingTime = initialBounceState.remainingTime;
        double eventTime = firstBounce.time;

        final BounceState finalBounceState;
        if (remainingTime < eventTime) { // No event during remaining time

            updatePositionAndPossiblyMomentum(position, velocity,
                    action, gradient, momentum, remainingTime);

            finalBounceState = new BounceState(Type.NONE, -1, 0.0);

        } else {

            final Type eventType = firstBounce.type;
            final int eventIndex = firstBounce.index;

            WrappedVector column = getPrecisionColumn(eventIndex);

            if (!TEST_NATIVE_INNER_BOUNCE) {

                updateDynamics(position.getBuffer(), velocity.getBuffer(),
                        action.getBuffer(), gradient.getBuffer(), momentum.getBuffer(),
                        column.getBuffer(), eventTime, eventIndex);

            } else {

                nativeZigZag.updateDynamics(position.getBuffer(), velocity.getBuffer(),
                        action.getBuffer(), gradient.getBuffer(), momentum.getBuffer(),
                        column.getBuffer(), eventTime, eventIndex, eventType.ordinal());
            }

            reflectPossiblyMomentum(position, momentum, eventType, eventIndex);

            reflectVelocity(velocity, eventIndex);

            finalBounceState = new BounceState(eventType, eventIndex, remainingTime - eventTime);
        }

        if (TIMING) {
            timer.stopTimer("doBounce");
        }

        return finalBounceState;
    }

    void testNative(MinimumTravelInformation firstBounce,
                              WrappedVector position,
                              WrappedVector velocity,
                              WrappedVector action,
                              WrappedVector gradient,
                              WrappedVector momentum) {

        if (TIMING) {
            timer.startTimer("getNextC++");
        }

        final MinimumTravelInformation mti;
        if (TEST_CRITICAL_REGION) {

            if (!nativeZigZag.inCriticalRegion()) {
                nativeZigZag.enterCriticalRegion(position.getBuffer(), velocity.getBuffer(),
                        action.getBuffer(), gradient.getBuffer(), momentum.getBuffer());
            }

            mti = nativeZigZag.getNextEventInCriticalRegion();
        } else {
            mti = nativeZigZag.getNextEvent(position.getBuffer(), velocity.getBuffer(),
                    action.getBuffer(), gradient.getBuffer(), momentum.getBuffer());
        }

        if (TIMING) {
            timer.stopTimer("getNextC++");
        }

        if (!firstBounce.equals(mti)) {
            System.err.println(mti + " ?= " + firstBounce + "\n");
            System.exit(-1);
        }

    }

    MinimumTravelInformation getNextBounce(WrappedVector position,
                                           WrappedVector velocity,
                                           WrappedVector action,
                                           WrappedVector gradient,
                                           WrappedVector momentum) {
        if (TIMING) {
            timer.startTimer("getNext");
        }

        MinimumTravelInformation result =  getNextBounceImpl(0, position.getDim(),
                position.getBuffer(), velocity.getBuffer(),
                action.getBuffer(), gradient.getBuffer(), momentum.getBuffer());

        if (TIMING) {
            timer.stopTimer("getNext");
        }

        return result;
    }

    abstract MinimumTravelInformation getNextBounceImpl(final int begin, final int end,
                                                        final double[] position,
                                                        final double[] velocity,
                                                        final double[] action,
                                                        final double[] gradient,
                                                        final double[] momentum);

    MinimumTravelInformation getNextBounceParallel(WrappedVector inPosition,
                                                   WrappedVector inVelocity,
                                                   WrappedVector inAction,
                                                   WrappedVector inGradient,
                                                   WrappedVector inMomentum) {
        if (TIMING) {
            timer.startTimer("getNextParallel");
        }

        final double[] position = inPosition.getBuffer();
        final double[] velocity = inVelocity.getBuffer();
        final double[] action = inAction.getBuffer();
        final double[] gradient = inGradient.getBuffer();
        final double[] momentum = inMomentum.getBuffer();

        TaskPool.RangeCallable<MinimumTravelInformation> map =
                (start, end, thread) -> getNextBounceImpl(start, end,
                        position, velocity, action, gradient, momentum);

        BinaryOperator<MinimumTravelInformation> reduce =
                (lhs, rhs) -> (lhs.time < rhs.time) ? lhs : rhs;

        MinimumTravelInformation result = taskPool.mapReduce(map, reduce);

        if (TIMING) {
            timer.stopTimer("getNextParallel");
        }

        return result;
    }

    static double findGradientRoot(double action,
                                   double gradient,
                                   double momentum) {
        return minimumPositiveRoot(-0.5 * action, gradient, momentum);
    }

    double findBoundaryTime(int index, double position,
                                    double velocity) {

        double time = Double.POSITIVE_INFINITY;

        if (headingTowardsBoundary(position, velocity, index)) { // Also ensures x != 0.0
            time = Math.abs(position / velocity);
        }

        return time;
    }

    private static double minimumPositiveRoot(double a,
                                              double b,
                                              double c) {
        double signA = sign(a);
        b = b * signA;
        c = c * signA;
        a = a * signA;

        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        double sqrtDiscriminant = Math.sqrt(discriminant);
        double root = (-b - sqrtDiscriminant) / (2 * a);
        if (root <= 0.0) {
            root = (-b + sqrtDiscriminant) / (2 * a);
        }
        if (root <= 0.0) {
            root = Double.POSITIVE_INFINITY;
        }

        return root;
    }

    static void reflectMomentum(WrappedVector momentum,
                                WrappedVector position,
                                int eventIndex) {

        momentum.set(eventIndex, -momentum.get(eventIndex));
        position.set(eventIndex, 0.0); // Exactly on boundary to avoid potential round-off error
    }

    static void setZeroMomentum(WrappedVector momentum,
                                int gradientEventIndex) {

        momentum.set(gradientEventIndex, 0.0); // Exactly zero on gradient event to avoid potential round-off error
    }

    private static void reflectVelocity(WrappedVector velocity,
                                int eventIndex) {

        velocity.set(eventIndex, -velocity.get(eventIndex));
    }

//    private static double minimumPositiveRoot(double a,
//                                              double b,
//                                              double c) {
//
//        double discriminant = b * b - 4 * a * c;
//        if (discriminant < 0.0) {
//            return Double.POSITIVE_INFINITY;
//        }
//
//        double sqrtDiscriminant = Math.sqrt(discriminant);
//        double root1 = (-b - sqrtDiscriminant) / (2 * a);
//        double root2 = (-b + sqrtDiscriminant) / (2 * a);
//
//        root1 = (root1 > 0.0) ? root1 : Double.POSITIVE_INFINITY;
//        root2 = (root2 > 0.0) ? root2 : Double.POSITIVE_INFINITY;
//
//        return (root1 < root2) ? root1 : root2;
//    }

    String printSign(ReadableVector position) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < position.getDim(); ++i) {
            double p = position.get(i);
            if (p < 0.0) sb.append("- ");
            else if (p > 0.0) sb.append("+ ");
            else sb.append("0 ");
        }
        return sb.toString();
    }

    void debugAfter(BounceState bounceState, ReadableVector position) {
        System.err.println("post position: " + position);
        System.err.println(bounceState);
        System.err.println();
    }

    void debugBefore(ReadableVector position, int count) {
        System.err.println("before number: " + count);
        System.err.println("init position: " + position);
    }

    protected boolean close(double[] lhs, double[] rhs) {
        for (int i = 0; i < lhs.length; ++i) {
            if (Math.abs((lhs[i] - rhs[i]) / (lhs[i] + rhs[i])) > 0.00001) {
                return false;
            }
        }
        return true;
    }

    static int sign(double x) {
        int sign = 0;
        if (x > 0.0) {
            sign = 1;
        } else if (x < 0.0) {
            sign = -1;
        }
        return sign;
    }

    public LogColumn[] getColumns() {
        LogColumn[] columns = new LogColumn[1];
        columns[0] = new NumberColumn("number of event") {
            @Override
            public double getDoubleValue() {
                return numEvents;
            }
        };
        return columns;
    }

    final TaskPool taskPool;

    protected final static boolean DEBUG = false;
    final static boolean DEBUG_SIGN = false;
}