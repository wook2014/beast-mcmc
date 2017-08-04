/*
 * PartitionClockModel.java
 *
 * Copyright (c) 2002-2015 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.app.beauti.options;

import dr.app.beauti.types.*;
import dr.evolution.util.Taxa;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Drummond
 * @author Andrew Rambaut
 * @author Walter Xie
 * @version $Id$
 */
public class PartitionClockModel extends PartitionOptions {
    private static final long serialVersionUID = -6904595851602060488L;

    private ClockType clockType = ClockType.STRICT_CLOCK;
    private ClockDistributionType clockDistributionType = ClockDistributionType.LOGNORMAL;
    private boolean continuousQuantile = false;

    private final AbstractPartitionData partition;
    private final int dataLength;

    public PartitionClockModel(final BeautiOptions options, AbstractPartitionData partition) {
        super(options);

        this.partitionName = partition.getName();
        dataLength = partition.getSiteCount();

        this.partition = partition;
        initModelParametersAndOpererators();
    }

    /**
     * A copy constructor
     *
     * @param options the beauti options
     * @param name    the name of the new model
     * @param source  the source model
     */
    public PartitionClockModel(BeautiOptions options, String name, PartitionClockModel source) {
        super(options);

        this.partitionName = name;

        this.clockType = source.clockType;
        clockDistributionType = source.clockDistributionType;

        dataLength = source.dataLength;

        this.partition = source.partition;

        initModelParametersAndOpererators();
    }

//    public PartitionClockModel(BeautiOptions options, String name) {
//        this.options = options;
//        this.name = name;
//    }

    public void initModelParametersAndOpererators() {
        double rate = 1.0;

        new Parameter.Builder("clock.rate", "substitution rate")
                .prior(PriorType.CTMC_RATE_REFERENCE_PRIOR).initial(rate)
                .isCMTCRate(true).isNonNegative(true).partitionOptions(this)
                .isAdaptiveMultivariateCompatible(true).build(parameters);

        new Parameter.Builder(ClockType.UCED_MEAN, "uncorrelated exponential relaxed clock mean").
                prior(PriorType.CTMC_RATE_REFERENCE_PRIOR).initial(rate)
                .isCMTCRate(true).isNonNegative(true).partitionOptions(this)
                .isAdaptiveMultivariateCompatible(true).build(parameters);

        new Parameter.Builder(ClockType.UCLD_MEAN, "uncorrelated lognormal relaxed clock mean").
                prior(PriorType.CTMC_RATE_REFERENCE_PRIOR).initial(rate)
                .isCMTCRate(true).isNonNegative(true).partitionOptions(this)
                .isAdaptiveMultivariateCompatible(true).build(parameters);

        new Parameter.Builder(ClockType.UCGD_MEAN, "uncorrelated gamma relaxed clock mean").
                prior(PriorType.CTMC_RATE_REFERENCE_PRIOR).initial(rate)
                .isCMTCRate(true).isNonNegative(true).partitionOptions(this)
                .isAdaptiveMultivariateCompatible(true).build(parameters);

        new Parameter.Builder(ClockType.UCLD_STDEV, "uncorrelated lognormal relaxed clock stdev").
                scaleType(PriorScaleType.LOG_STDEV_SCALE).prior(PriorType.EXPONENTIAL_PRIOR).isNonNegative(true)
                .initial(1.0 / 3.0).mean(1.0 / 3.0).offset(0.0).partitionOptions(this)
                .isAdaptiveMultivariateCompatible(true).build(parameters);

        new Parameter.Builder(ClockType.UCGD_SHAPE, "uncorrelated gamma relaxed clock shape").
                prior(PriorType.EXPONENTIAL_PRIOR).isNonNegative(true)
                .initial(1.0 / 3.0).mean(1.0 / 3.0).offset(0.0).partitionOptions(this)
                .isAdaptiveMultivariateCompatible(true).build(parameters);

        // Random local clock
        createParameterGammaPrior(ClockType.LOCAL_CLOCK + ".relativeRates", "random local clock relative rates",
                PriorScaleType.SUBSTITUTION_RATE_SCALE, 1.0, 0.5, 2.0, false, false);
        createParameter(ClockType.LOCAL_CLOCK + ".changes", "random local clock rate change indicator");

        createScaleOperator("clock.rate", demoTuning, rateWeights);
        createScaleOperator(ClockType.UCED_MEAN, demoTuning, rateWeights);
        createScaleOperator(ClockType.UCLD_MEAN, demoTuning, rateWeights);
        createScaleOperator(ClockType.UCLD_STDEV, demoTuning, rateWeights);
        createScaleOperator(ClockType.UCGD_MEAN, demoTuning, rateWeights);
        createScaleOperator(ClockType.UCGD_SHAPE, demoTuning, rateWeights);
        // Random local clock
        createScaleOperator(ClockType.LOCAL_CLOCK + ".relativeRates", demoTuning, treeWeights);
        createOperator(ClockType.LOCAL_CLOCK + ".changes", OperatorType.BITFLIP, 1, treeWeights);
        createDiscreteStatistic("rateChanges", "number of random local clocks"); // POISSON_PRIOR

        // A vector of relative rates across all partitions...

        createNonNegativeParameterDirichletPrior("allNus", "relative rates amongst partitions parameter", this, PriorScaleType.SUBSTITUTION_PARAMETER_SCALE, 1.0, true);
        createOperator("deltaNus", "allNus",
                "Change partition rates relative to each other maintaining mean", "allNus",
                OperatorType.DELTA_EXCHANGE, 0.01, 3.0);

        createNonNegativeParameterInfinitePrior("allMus", "relative rates amongst partitions parameter", this, PriorScaleType.SUBSTITUTION_PARAMETER_SCALE, 1.0, true);
        createOperator("deltaMus", "allMus",
                "Change partition rates relative to each other maintaining mean", "allMus",
                OperatorType.WEIGHTED_DELTA_EXCHANGE, 0.01, 3.0);

    }

    @Override
    public List<Parameter> selectParameters(List<Parameter> params) {
//        setAvgRootAndRate();
        double rate = 1.0;

        if (options.hasData()) {
            switch (clockType) {
                case STRICT_CLOCK:
//                    rateParam = getParameter("clock.rate");
                    break;

                case RANDOM_LOCAL_CLOCK:
//                    rateParam = getParameter("clock.rate");
                    getParameter(ClockType.LOCAL_CLOCK + ".changes");
                    params.add(getParameter("rateChanges"));
                    params.add(getParameter(ClockType.LOCAL_CLOCK + ".relativeRates"));
                    break;

                case FIXED_LOCAL_CLOCK:
                    for (Taxa taxonSet : options.taxonSets) {
                        if (options.taxonSetsMono.get(taxonSet)) {
                            String parameterName = taxonSet.getId() + ".rate";
                            if (!hasParameter(parameterName)) {
                                new Parameter.Builder(parameterName, "substitution rate")
                                        .prior(PriorType.CTMC_RATE_REFERENCE_PRIOR)
                                        .initial(rate)
                                        .isCMTCRate(true).isNonNegative(true)
                                        .partitionOptions(this)
                                        .taxonSet(taxonSet)
                                        .build(parameters);
                                createScaleOperator(parameterName, demoTuning, rateWeights);
                            }

                            params.add(getParameter(taxonSet.getId() + ".rate"));
                        }
                    }
                    break;

                case UNCORRELATED:
                    // add the scale parameter (if needed) for the distribution. The location parameter will be added
                    // in getClockRateParameter.
                    switch (clockDistributionType) {
                        case LOGNORMAL:
                            params.add(getParameter(ClockType.UCLD_STDEV));
                            break;
                        case GAMMA:
                            params.add(getParameter(ClockType.UCGD_SHAPE));
                            break;
                        case CAUCHY:
                            throw new UnsupportedOperationException("Uncorrelated Cauchy clock not implemented yet");
//                            break;
                        case EXPONENTIAL:
                            break;
                    }
                    break;

                case AUTOCORRELATED:
                    throw new UnsupportedOperationException("Autocorrelated clock not implemented yet");
//                    params.add(getParameter("branchRates.var"));
//                    break;

                default:
                    throw new IllegalArgumentException("Unknown clock model");
            }

            Parameter rateParam = getClockRateParameter();
            params.add(rateParam);
        }
        return params;
    }

    public Parameter getClockRateParameter() {
        return getClockRateParameter(clockType, clockDistributionType);
    }

    private Parameter getClockRateParameter(ClockType clockType, ClockDistributionType clockDistributionType) {
        Parameter rateParam = null;
        switch (clockType) {
            case STRICT_CLOCK:
            case RANDOM_LOCAL_CLOCK:
            case FIXED_LOCAL_CLOCK:
                rateParam = getParameter("clock.rate");
                break;

            case UNCORRELATED:
                switch (clockDistributionType) {
                    case LOGNORMAL:
                        rateParam = getParameter(ClockType.UCLD_MEAN);
                        break;
                    case GAMMA:
                        rateParam = getParameter(ClockType.UCGD_MEAN);
                        break;
                    case CAUCHY:
                        throw new UnsupportedOperationException("Uncorrelated Cauchy clock not implemented yet");
//                            break;
                    case EXPONENTIAL:
                        rateParam = getParameter(ClockType.UCED_MEAN);
                        break;
                }
                break;

            case AUTOCORRELATED:
                throw new UnsupportedOperationException("Autocorrelated clock not implemented yet");
//                    rateParam = getParameter("treeModel.rootRate");//TODO fix tree?
//                    break;

            default:
                throw new IllegalArgumentException("Unknown clock model");
        }

        if (!rateParam.isPriorEdited()) {
            if (options.treeModelOptions.isNodeCalibrated(partition.treeModel) < 0
                    && !options.clockModelOptions.isTipCalibrated()) {
                rateParam.setFixed(true);
            } else {
                rateParam.priorType = PriorType.CTMC_RATE_REFERENCE_PRIOR;
            }
        }

        return rateParam;
    }

    @Override
    public List<Operator> selectOperators(List<Operator> operators) {
        List<Operator> ops = new ArrayList<Operator>();

        if (options.hasData()) {

            switch (clockType) {
                case STRICT_CLOCK:
                    ops.add(getOperator("clock.rate"));
                    break;

                case RANDOM_LOCAL_CLOCK:
                    ops.add(getOperator("clock.rate"));
                    addRandomLocalClockOperators(ops);
                    break;

                case FIXED_LOCAL_CLOCK:
                    ops.add(getOperator("clock.rate"));
                    for (Taxa taxonSet : options.taxonSets) {
                        if (options.taxonSetsMono.get(taxonSet)) {
                            ops.add(getOperator(taxonSet.getId() + ".rate"));
                        }
                    }
                    break;

                case UNCORRELATED:
                    switch (clockDistributionType) {
                        case LOGNORMAL:
                            ops.add(getOperator(ClockType.UCLD_MEAN));
                            ops.add(getOperator(ClockType.UCLD_STDEV));
                            break;
                        case GAMMA:
                            ops.add(getOperator(ClockType.UCGD_MEAN));
                            ops.add(getOperator(ClockType.UCGD_SHAPE));
                            break;
                        case CAUCHY:
//                                throw new UnsupportedOperationException("Uncorrelated Couchy clock not implemented yet");
                            break;
                        case EXPONENTIAL:
                            ops.add(getOperator(ClockType.UCED_MEAN));
                            break;
                    }
                    break;

                case AUTOCORRELATED:
                    throw new UnsupportedOperationException("Autocorrelated clock not implemented yet");
//                        break;

                default:
                    throw new IllegalArgumentException("Unknown clock model");
            }
        }

        Parameter allMus = getParameter(options.NEW_RELATIVE_RATE_PARAMETERIZATION ? "allNus" : "allMus");

        if (allMus.getSubParameters().size() > 1) {
            Operator muOperator;

            muOperator = getOperator(options.NEW_RELATIVE_RATE_PARAMETERIZATION ? "deltaNus" : "deltaMus");

            ops.add(muOperator);
        }

        if (options.operatorSetType != OperatorSetType.CUSTOM) {
            // unless a custom mix has been chosen these operators should be off if AMTK is on
            for (Operator op : ops) {
                if (op.getParameter1().isAdaptiveMultivariateCompatible) {
                    op.setUsed(options.operatorSetType != OperatorSetType.ADAPTIVE_MULTIVARIATE);
                }
            }
        }

        operators.addAll(ops);

        return ops;
    }

    private void addRandomLocalClockOperators(List<Operator> ops) {
        ops.add(getOperator(ClockType.LOCAL_CLOCK + ".relativeRates"));
        ops.add(getOperator(ClockType.LOCAL_CLOCK + ".changes"));
    }


    /////////////////////////////////////////////////////////////
    public void setClockType(ClockType clockType) {
        this.clockType = clockType;
    }

    public ClockType getClockType() {
        return clockType;
    }

    public ClockDistributionType getClockDistributionType() {
        return clockDistributionType;
    }

    public void setClockDistributionType(final ClockDistributionType clockDistributionType) {
        this.clockDistributionType = clockDistributionType;
    }

    public boolean isContinuousQuantile() {
        return continuousQuantile;
    }

    public void setContinuousQuantile(boolean continuousQuantile) {
        this.continuousQuantile = continuousQuantile;
    }

    public String getPrefix() {
        String prefix = "";
        if (options.getPartitionClockModels().size() > 1) { //|| options.isSpeciesAnalysis()
            // There is more than one active partition model
            prefix += getName() + ".";
        }
        return prefix;
    }

    public void copyFrom(PartitionClockModel source) {
        clockType = source.clockType;
        clockDistributionType = source.clockDistributionType;
        continuousQuantile = source.continuousQuantile;
    }
}
