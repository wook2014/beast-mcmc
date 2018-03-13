package test.dr.evomodel.treedatalikelihood.continuous;

import dr.evolution.datatype.Nucleotides;
import dr.evolution.tree.TreeTrait;
import dr.evomodel.branchratemodel.ArbitraryBranchRates;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.branchratemodel.DefaultBranchRateModel;
import dr.evomodel.branchratemodel.StrictClockBranchRates;
import dr.evomodel.continuous.MultivariateDiffusionModel;
import dr.evomodel.treedatalikelihood.ProcessSimulation;
import dr.evomodel.treedatalikelihood.TreeDataLikelihood;
import dr.evomodel.treedatalikelihood.continuous.*;
import dr.evomodel.treedatalikelihood.continuous.cdi.PrecisionType;
import dr.evomodel.treedatalikelihood.preorder.MultivariateConditionalOnTipsRealizedDelegate;
import dr.evomodel.treedatalikelihood.preorder.ProcessSimulationDelegate;
import dr.inference.model.*;
import dr.math.MathUtils;
import test.dr.inference.trace.TraceCorrelationAssert;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * @author Paul Bastide
 */

public class ContinuousDataLikelihoodDelegateTest extends TraceCorrelationAssert {

    private MultivariateDiffusionModel diffusionModel;
    private ContinuousTraitPartialsProvider dataModel;
    private ConjugateRootTraitPrior rootPrior;

    private MultivariateDiffusionModel diffusionModelFactor;
    private IntegratedFactorAnalysisLikelihood dataModelFactor;
    private ConjugateRootTraitPrior rootPriorFactor;

    private NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);

    public ContinuousDataLikelihoodDelegateTest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        super.setUp();

        format.setMaximumFractionDigits(5);

        // Tree
        createAlignment(PRIMATES_TAXON_SEQUENCE, Nucleotides.INSTANCE);
        treeModel = createPrimateTreeModel();

        // Data
        Parameter[] dataTraits = new Parameter[6];
        dataTraits[0] = new Parameter.Default("human", new double[]{-1.0, 2.0, 3.0});
        dataTraits[1] = new Parameter.Default("chimp", new double[]{10.0, 12.0, 14.0});
        dataTraits[2] = new Parameter.Default("bonobo", new double[]{0.5, -2.0, 5.5});
        dataTraits[3] = new Parameter.Default("gorilla", new double[]{2.0, 5.0, -8.0});
        dataTraits[4] = new Parameter.Default("orangutan", new double[]{11.0, 1.0, -1.5});
        dataTraits[5] = new Parameter.Default("siamang", new double[]{1.0, 2.5, 4.0});
        CompoundParameter traitParameter = new CompoundParameter("trait", dataTraits);

        List<Integer> missingIndices = new ArrayList<Integer>();
        traitParameter.setParameterValue(2, 0);
        missingIndices.add(3);
        missingIndices.add(4);
        missingIndices.add(5);
        missingIndices.add(7);

        //// Standard Model //// ***************************************************************************************

        // Diffusion
        Parameter[] precisionParameters = new Parameter[3];
        precisionParameters[0] = new Parameter.Default(new double[]{1.0, 0.1, 0.2});
        precisionParameters[1] = new Parameter.Default(new double[]{0.1, 2.0, 0.0});
        precisionParameters[2] = new Parameter.Default(new double[]{0.2, 0.0, 3.0});
        MatrixParameterInterface diffusionPrecisionMatrixParameter = new MatrixParameter("precisionMatrix", precisionParameters);
        diffusionModel = new MultivariateDiffusionModel(diffusionPrecisionMatrixParameter);

        PrecisionType precisionType = PrecisionType.FULL;

        // Root prior
        rootPrior = new ConjugateRootTraitPrior(new double[]{-1.0, -3.0, 2.5}, 10.0, true);

        // Data Model
        dataModel = new ContinuousTraitDataModel("dataModel",
                traitParameter,
                missingIndices, true,
                3, precisionType);

        //// Factor Model //// *****************************************************************************************
        // Diffusion
        Parameter[] precisionParametersFactor = new Parameter[2];
        precisionParametersFactor[0] = new Parameter.Default(new double[]{1.0, 0.1});
        precisionParametersFactor[1] = new Parameter.Default(new double[]{0.1, 1.5});
        MatrixParameterInterface diffusionPrecisionMatrixParameterFactor = new MatrixParameter("precisionMatrixFactor", precisionParametersFactor);
        diffusionModelFactor = new MultivariateDiffusionModel(diffusionPrecisionMatrixParameterFactor);

        // Root prior
        rootPriorFactor = new ConjugateRootTraitPrior(new double[]{-1.0, 2.0}, 10.0, true);

        // Error model
        Parameter factorPrecisionParameters = new Parameter.Default("factorPrecision", new double[]{1.0, 5.0, 0.5});

        // Loadings
        Parameter[] loadingsParameters = new Parameter[2];
        loadingsParameters[0] = new Parameter.Default(new double[]{1.0, 2.0, 3.0});
        loadingsParameters[1] = new Parameter.Default(new double[]{0.0, 0.5, 1.0});
        MatrixParameterInterface loadingsMatrixParameters = new MatrixParameter("loadings", loadingsParameters);

        dataModelFactor = new IntegratedFactorAnalysisLikelihood("dataModelFactors",
                traitParameter,
                missingIndices,
                loadingsMatrixParameters,
                factorPrecisionParameters, 0.0);
    }

    public void testLikelihoodBM() {
        System.out.println("\nTest Likelihood using vanilla BM:");

        // Diffusion
        DiffusionProcessDelegate diffusionProcessDelegate = new HomogeneousDiffusionModelDelegate(treeModel, diffusionModel);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModel, rootPrior, rateTransformation, rateModel, true);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihood = new TreeDataLikelihood(likelihoodDelegate, treeModel, rateModel);

        String s = dataLikelihood.getReport();
        int indLikBeg = s.indexOf("logDatumLikelihood:") + 20;
        int indLikEnd = s.indexOf("\n", indLikBeg);
        char[] logDatumLikelihoodChar = new char[indLikEnd - indLikBeg + 1];
        s.getChars(indLikBeg, indLikEnd, logDatumLikelihoodChar, 0);
        double logDatumLikelihood = Double.parseDouble(String.valueOf(logDatumLikelihoodChar));

        double integratedLikelihood = dataLikelihood.getLogLikelihood();

        assertEquals("likelihoodBM", format.format(logDatumLikelihood), format.format(integratedLikelihood));

        System.out.println("likelihoodBM: " + format.format(logDatumLikelihood));

        // Conditional simulations
        MathUtils.setSeed(17890826);
        ProcessSimulationDelegate simulationDelegate =
                MultivariateConditionalOnTipsRealizedDelegate.constructMultivariateConditionalOnTipsRealizedDelegate(
                        "dataModel", treeModel,
                        diffusionModel, dataModel, rootPrior, rateTransformation, likelihoodDelegate);
        ProcessSimulation simulationProcess = new ProcessSimulation(dataLikelihood, simulationDelegate);
        simulationProcess.cacheSimulatedTraits(null);
        TreeTrait[] treeTrait = simulationProcess.getTreeTraits();

        String expectedTraits = "{-1.0,2.0,0.0,0.458075216795976,2.650535598209761,3.4693334367360538,0.5,2.6420628558588306,5.5,2.0,5.0,-8.0,11.0,1.0,-1.5,1.0,2.5,4.0}";
        assertEquals("traitsBM", treeTrait[0].getTraitString(treeModel, null), expectedTraits);

    }

    public void testLikelihoodDrift() {
        System.out.println("\nTest Likelihood using Drifted BM:");

        // Diffusion
        List<BranchRateModel> driftModels = new ArrayList<BranchRateModel>();
        driftModels.add(new StrictClockBranchRates(new Parameter.Default("rate.1", new double[]{1.0})));
        driftModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{2.0})));
        driftModels.add(new StrictClockBranchRates(new Parameter.Default("rate.3", new double[]{-2.0})));
        DiffusionProcessDelegate diffusionProcessDelegate = new DriftDiffusionModelDelegate(treeModel, diffusionModel, driftModels);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModel, rootPrior, rateTransformation, rateModel, false);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihood = new TreeDataLikelihood(likelihoodDelegate, treeModel, rateModel);

        String s = dataLikelihood.getReport();
        int indLikBeg = s.indexOf("logDatumLikelihood:") + 20;
        int indLikEnd = s.indexOf("\n", indLikBeg);
        char[] logDatumLikelihoodChar = new char[indLikEnd - indLikBeg + 1];
        s.getChars(indLikBeg, indLikEnd, logDatumLikelihoodChar, 0);
        double logDatumLikelihood = Double.parseDouble(String.valueOf(logDatumLikelihoodChar));

        assertEquals("likelihoodDrift", format.format(logDatumLikelihood), format.format(dataLikelihood.getLogLikelihood()));

        System.out.println("likelihoodDrift: " + format.format(logDatumLikelihood));
    }

    public void testLikelihoodDriftRelaxed() {
        System.out.println("\nTest Likelihood using Drifted relaxed BM:");

        // Diffusion
        List<BranchRateModel> driftModels = new ArrayList<BranchRateModel>();
        driftModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.1", new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}),
                false, false, false));
        driftModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.2", new double[]{0, -1, 2, -3, 4, -5, 6, -7, 8, -9}),
                false, false, false));
        driftModels.add(new StrictClockBranchRates(new Parameter.Default("rate.3", new double[]{-2.0})));
        DiffusionProcessDelegate diffusionProcessDelegate = new DriftDiffusionModelDelegate(treeModel, diffusionModel, driftModels);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModel, rootPrior, rateTransformation, rateModel, false);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihood = new TreeDataLikelihood(likelihoodDelegate, treeModel, rateModel);

        String s = dataLikelihood.getReport();
        int indLikBeg = s.indexOf("logDatumLikelihood:") + 20;
        int indLikEnd = s.indexOf("\n", indLikBeg);
        char[] logDatumLikelihoodChar = new char[indLikEnd - indLikBeg + 1];
        s.getChars(indLikBeg, indLikEnd, logDatumLikelihoodChar, 0);
        double logDatumLikelihood = Double.parseDouble(String.valueOf(logDatumLikelihoodChar));

        assertEquals("likelihoodDriftRelaxed", format.format(logDatumLikelihood), format.format(dataLikelihood.getLogLikelihood()));

        System.out.println("likelihoodDriftRelaxed: " + format.format(logDatumLikelihood));
    }

    public void testLikelihoodDiagonalOU() {
        System.out.println("\nTest Likelihood using Diagonal OU:");

        // Diffusion
        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.1", new double[]{1.0})));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{2.0})));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.3", new double[]{-2.0})));

        DiagonalMatrix strengthOfSelectionMatrixParam = new DiagonalMatrix(new Parameter.Default(new double[]{1.0, 2.0, 3.0}));

        DiffusionProcessDelegate diffusionProcessDelegate = new DiagonalOrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModel, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModel, rootPrior, rateTransformation, rateModel, false);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihood = new TreeDataLikelihood(likelihoodDelegate, treeModel, rateModel);

        String s = dataLikelihood.getReport();
        int indLikBeg = s.indexOf("logDatumLikelihood:") + 20;
        int indLikEnd = s.indexOf("\n", indLikBeg);
        char[] logDatumLikelihoodChar = new char[indLikEnd - indLikBeg + 1];
        s.getChars(indLikBeg, indLikEnd, logDatumLikelihoodChar, 0);
        double logDatumLikelihood = Double.parseDouble(String.valueOf(logDatumLikelihoodChar));

        assertEquals("likelihoodDiagOU", format.format(logDatumLikelihood), format.format(dataLikelihood.getLogLikelihood()));

        System.out.println("likelihoodDiagOU: " + format.format(logDatumLikelihood));
    }

    public void testLikelihoodDiagonalOURelaxed() {
        System.out.println("\nTest Likelihood using Diagonal OU Relaxed:");

        // Diffusion
        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.1", new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}),
                false, false, false));
        optimalTraitsModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.2", new double[]{0, -1, 2, -3, 4, -5, 6, -7, 8, -9}),
                false, false, false));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.3", new double[]{-2.0})));

        DiagonalMatrix strengthOfSelectionMatrixParam = new DiagonalMatrix(new Parameter.Default(new double[]{1.0, 2.0, 3.0}));

        DiffusionProcessDelegate diffusionProcessDelegate = new DiagonalOrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModel, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModel, rootPrior, rateTransformation, rateModel, false);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihood = new TreeDataLikelihood(likelihoodDelegate, treeModel, rateModel);

        String s = dataLikelihood.getReport();
        int indLikBeg = s.indexOf("logDatumLikelihood:") + 20;
        int indLikEnd = s.indexOf("\n", indLikBeg);
        char[] logDatumLikelihoodChar = new char[indLikEnd - indLikBeg + 1];
        s.getChars(indLikBeg, indLikEnd, logDatumLikelihoodChar, 0);
        double logDatumLikelihood = Double.parseDouble(String.valueOf(logDatumLikelihoodChar));

        assertEquals("likelihoodDiagOURelaxed", format.format(logDatumLikelihood), format.format(dataLikelihood.getLogLikelihood()));

        System.out.println("likelihoodDiagOURelaxed: " + format.format(logDatumLikelihood));
    }

    public void testLikelihoodFullOU() {
        System.out.println("\nTest Likelihood using Full OU:");

        // Diffusion
        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.1", new double[]{1.0})));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{2.0})));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.3", new double[]{-2.0})));

        Parameter[] strengthOfSelectionParameters = new Parameter[3];
        strengthOfSelectionParameters[0] = new Parameter.Default(new double[]{0.5, 0.2, 0.0});
        strengthOfSelectionParameters[1] = new Parameter.Default(new double[]{0.2, 2.0, 0.1});
        strengthOfSelectionParameters[2] = new Parameter.Default(new double[]{0.0, 0.1, 5.5});
        MatrixParameter strengthOfSelectionMatrixParam = new MatrixParameter("strengthOfSelectionMatrix", strengthOfSelectionParameters);

        DiffusionProcessDelegate diffusionProcessDelegate = new OrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModel, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModel, rootPrior, rateTransformation, rateModel, false);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihood = new TreeDataLikelihood(likelihoodDelegate, treeModel, rateModel);

        String s = dataLikelihood.getReport();
        int indLikBeg = s.indexOf("logDatumLikelihood:") + 20;
        int indLikEnd = s.indexOf("\n", indLikBeg);
        char[] logDatumLikelihoodChar = new char[indLikEnd - indLikBeg + 1];
        s.getChars(indLikBeg, indLikEnd, logDatumLikelihoodChar, 0);
        double logDatumLikelihood = Double.parseDouble(String.valueOf(logDatumLikelihoodChar));

        assertEquals("likelihoodFullOU", format.format(logDatumLikelihood), format.format(dataLikelihood.getLogLikelihood()));

        System.out.println("likelihoodFullOU: " + format.format(logDatumLikelihood));
    }

    public void testLikelihoodFullOURelaxed() {
        System.out.println("\nTest Likelihood using Full OU Relaxed:");

        // Diffusion
        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.1", new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}),
                false, false, false));
        optimalTraitsModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.2", new double[]{0, -1, 2, -3, 4, -5, 6, -7, 8, -9}),
                false, false, false));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.3", new double[]{-2.0})));

        Parameter[] strengthOfSelectionParameters = new Parameter[3];
        strengthOfSelectionParameters[0] = new Parameter.Default(new double[]{0.5, 0.2, 0.0});
        strengthOfSelectionParameters[1] = new Parameter.Default(new double[]{0.2, 2.0, 0.1});
        strengthOfSelectionParameters[2] = new Parameter.Default(new double[]{0.0, 0.1, 5.5});
        MatrixParameter strengthOfSelectionMatrixParam = new MatrixParameter("strengthOfSelectionMatrix", strengthOfSelectionParameters);

        DiffusionProcessDelegate diffusionProcessDelegate = new OrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModel, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegate = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModel, rootPrior, rateTransformation, rateModel, false);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihood = new TreeDataLikelihood(likelihoodDelegate, treeModel, rateModel);

        String s = dataLikelihood.getReport();
        int indLikBeg = s.indexOf("logDatumLikelihood:") + 20;
        int indLikEnd = s.indexOf("\n", indLikBeg);
        char[] logDatumLikelihoodChar = new char[indLikEnd - indLikBeg + 1];
        s.getChars(indLikBeg, indLikEnd, logDatumLikelihoodChar, 0);
        double logDatumLikelihood = Double.parseDouble(String.valueOf(logDatumLikelihoodChar));

        assertEquals("likelihoodFullOURelaxed", format.format(logDatumLikelihood), format.format(dataLikelihood.getLogLikelihood()));

        System.out.println("likelihoodFullOURelaxed: " + format.format(logDatumLikelihood));
    }

    //// Factor Model //// *********************************************************************************************

    public void testLikelihoodBMFactor() {
        System.out.println("\nTest Likelihood using vanilla BM and factor:");

        // Diffusion
        DiffusionProcessDelegate diffusionProcessDelegate = new HomogeneousDiffusionModelDelegate(treeModel, diffusionModelFactor);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegateFactors = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModelFactor, rootPriorFactor, rateTransformation, rateModel, true);

        dataModelFactor.setLikelihoodDelegate(likelihoodDelegateFactors);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihoodFactors = new TreeDataLikelihood(likelihoodDelegateFactors, treeModel, rateModel);

        String sf = dataModelFactor.getReport();
        int indLikBegF = sf.indexOf("logMultiVariateNormalDensity = ") + 31;
        int indLikEndF = sf.indexOf("\n", indLikBegF);
        char[] logDatumLikelihoodCharF = new char[indLikEndF - indLikBegF + 1];
        sf.getChars(indLikBegF, indLikEndF, logDatumLikelihoodCharF, 0);
        double logDatumLikelihoodFactor = Double.parseDouble(String.valueOf(logDatumLikelihoodCharF));

        double likelihoodFactorData = dataLikelihoodFactors.getLogLikelihood();
        double likelihoodFactorDiffusion = dataModelFactor.getLogLikelihood();

        assertEquals("likelihoodBMFactor", format.format(logDatumLikelihoodFactor), format.format(likelihoodFactorData + likelihoodFactorDiffusion));

        // Conditional simulations
        MathUtils.setSeed(17890826);
        ProcessSimulationDelegate simulationDelegate =
                MultivariateConditionalOnTipsRealizedDelegate.constructMultivariateConditionalOnTipsRealizedDelegate(
                        "dataModel", treeModel,
                        diffusionModelFactor, dataModelFactor, rootPrior, rateTransformation, likelihoodDelegateFactors);
        ProcessSimulation simulationProcess = new ProcessSimulation(dataLikelihoodFactors, simulationDelegate);
        simulationProcess.cacheSimulatedTraits(null);
        TreeTrait[] treeTrait = simulationProcess.getTreeTraits();

        String expectedTraits = "{0.6002879987080068,1.3630884580519502,0.5250449300511648,1.4853676908300664,0.667320221595549,1.3998200473802225,1.0853554355129347,1.6054879123935415,0.44954940802560606,1.4427296475118268,0.875078906950004,1.8099596179292197}";
        assertEquals("traitsBMFactor", treeTrait[0].getTraitString(treeModel, null), expectedTraits);
    }

    public void testLikelihoodDriftFactor() {
        System.out.println("\nTest Likelihood using drifted BM and factor:");

        // Diffusion
        List<BranchRateModel> driftModels = new ArrayList<BranchRateModel>();
        driftModels.add(new StrictClockBranchRates(new Parameter.Default("rate.1", new double[]{0.0})));
        driftModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{-40.0})));
        DiffusionProcessDelegate diffusionProcessDelegate = new DriftDiffusionModelDelegate(treeModel, diffusionModelFactor, driftModels);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegateFactors = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModelFactor, rootPriorFactor, rateTransformation, rateModel, false);

        dataModelFactor.setLikelihoodDelegate(likelihoodDelegateFactors);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihoodFactors = new TreeDataLikelihood(likelihoodDelegateFactors, treeModel, rateModel);

        String sf = dataModelFactor.getReport();
        int indLikBegF = sf.indexOf("logMultiVariateNormalDensity = ") + 31;
        int indLikEndF = sf.indexOf("\n", indLikBegF);
        char[] logDatumLikelihoodCharF = new char[indLikEndF - indLikBegF + 1];
        sf.getChars(indLikBegF, indLikEndF, logDatumLikelihoodCharF, 0);
        double logDatumLikelihoodFactor = Double.parseDouble(String.valueOf(logDatumLikelihoodCharF));

        double likelihoodFactorData = dataLikelihoodFactors.getLogLikelihood();
        double likelihoodFactorDiffusion = dataModelFactor.getLogLikelihood();


        assertEquals("likelihoodDriftFactor", format.format(logDatumLikelihoodFactor), format.format(likelihoodFactorData + likelihoodFactorDiffusion));

        // Conditional simulations
        MathUtils.setSeed(17890826);
        ProcessSimulationDelegate simulationDelegate =
                MultivariateConditionalOnTipsRealizedDelegate.constructMultivariateConditionalOnTipsRealizedDelegate(
                        "dataModel", treeModel,
                        diffusionModelFactor, dataModelFactor, rootPrior, rateTransformation, likelihoodDelegateFactors);
        ProcessSimulation simulationProcess = new ProcessSimulation(dataLikelihoodFactors, simulationDelegate);
        simulationProcess.cacheSimulatedTraits(null);
        TreeTrait[] treeTrait = simulationProcess.getTreeTraits();

        String expectedTraits = "{1.5058510863259007,-2.3441077477910293,1.415239714927792,-2.2259379809163264,1.5639840062954742,-2.3082612693286477,1.9875205911751008,-2.1049011248405503,1.335546022528236,-2.284847144156403,1.7423473180267903,-1.9409033371162332}";
        assertEquals("traitsBMDriftFactor", treeTrait[0].getTraitString(treeModel, null), expectedTraits);
    }

    public void testLikelihoodDriftRelaxedFactor() {
        System.out.println("\nTest Likelihood using drifted Relaxed BM and factor:");

        // Diffusion
        List<BranchRateModel> driftModels = new ArrayList<BranchRateModel>();
        driftModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.1", new double[]{0, 10, 20, 30, 40, 40, 30, 20, 10, 0}),
                false, false, false));
        driftModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{0.0})));
        DiffusionProcessDelegate diffusionProcessDelegate = new DriftDiffusionModelDelegate(treeModel, diffusionModelFactor, driftModels);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegateFactors = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModelFactor, rootPriorFactor, rateTransformation, rateModel, false);

        dataModelFactor.setLikelihoodDelegate(likelihoodDelegateFactors);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihoodFactors = new TreeDataLikelihood(likelihoodDelegateFactors, treeModel, rateModel);

        String sf = dataModelFactor.getReport();
        int indLikBegF = sf.indexOf("logMultiVariateNormalDensity = ") + 31;
        int indLikEndF = sf.indexOf("\n", indLikBegF);
        char[] logDatumLikelihoodCharF = new char[indLikEndF - indLikBegF + 1];
        sf.getChars(indLikBegF, indLikEndF, logDatumLikelihoodCharF, 0);
        double logDatumLikelihoodFactor = Double.parseDouble(String.valueOf(logDatumLikelihoodCharF));

        double likelihoodFactorData = dataLikelihoodFactors.getLogLikelihood();
        double likelihoodFactorDiffusion = dataModelFactor.getLogLikelihood();


        assertEquals("likelihoodDriftRelaxedFactor", format.format(logDatumLikelihoodFactor), format.format(likelihoodFactorData + likelihoodFactorDiffusion));

        // Conditional simulations
        MathUtils.setSeed(17890826);
        ProcessSimulationDelegate simulationDelegate =
                MultivariateConditionalOnTipsRealizedDelegate.constructMultivariateConditionalOnTipsRealizedDelegate(
                        "dataModel", treeModel,
                        diffusionModelFactor, dataModelFactor, rootPrior, rateTransformation, likelihoodDelegateFactors);
        ProcessSimulation simulationProcess = new ProcessSimulation(dataLikelihoodFactors, simulationDelegate);
        simulationProcess.cacheSimulatedTraits(null);
        TreeTrait[] treeTrait = simulationProcess.getTreeTraits();

        String expectedTraits = "{0.21992781609528214,1.2713882737115567,0.4076154853975173,1.3682648770877144,0.6599021787120449,1.2830636141108613,1.1488658943588332,1.4721036881533907,0.8971632986744895,1.2074893341485398,1.6037398237268083,1.4761482401796837}";
        assertEquals("traitsBMDriftRelaxedFactor", treeTrait[0].getTraitString(treeModel, null), expectedTraits);

    }

    public void testLikelihoodDiagonalOUFactor() {
        System.out.println("\nTest Likelihood using diagonal OU and factor:");

        // Diffusion
        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.1", new double[]{1.0})));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{-1.5})));

        DiagonalMatrix strengthOfSelectionMatrixParam = new DiagonalMatrix(new Parameter.Default(new double[]{1.5, 2.0}));

        DiffusionProcessDelegate diffusionProcessDelegate = new DiagonalOrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModelFactor, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegateFactors = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModelFactor, rootPriorFactor, rateTransformation, rateModel, false);

        dataModelFactor.setLikelihoodDelegate(likelihoodDelegateFactors);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihoodFactors = new TreeDataLikelihood(likelihoodDelegateFactors, treeModel, rateModel);

        String sf = dataModelFactor.getReport();
        int indLikBegF = sf.indexOf("logMultiVariateNormalDensity = ") + 31;
        int indLikEndF = sf.indexOf("\n", indLikBegF);
        char[] logDatumLikelihoodCharF = new char[indLikEndF - indLikBegF + 1];
        sf.getChars(indLikBegF, indLikEndF, logDatumLikelihoodCharF, 0);
        double logDatumLikelihoodFactor = Double.parseDouble(String.valueOf(logDatumLikelihoodCharF));

        double likelihoodFactorData = dataLikelihoodFactors.getLogLikelihood();
        double likelihoodFactorDiffusion = dataModelFactor.getLogLikelihood();


        assertEquals("likelihoodDiagonalOUFactor", format.format(logDatumLikelihoodFactor), format.format(likelihoodFactorData + likelihoodFactorDiffusion));
    }

    public void testLikelihoodDiagonalOURelaxedFactor() {
        System.out.println("\nTest Likelihood using diagonal Relaxed OU and factor:");

        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.1", new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}),
                false, false, false));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{-1.5})));

        DiagonalMatrix strengthOfSelectionMatrixParam = new DiagonalMatrix(new Parameter.Default(new double[]{1.5, 2.0}));

        DiffusionProcessDelegate diffusionProcessDelegate = new DiagonalOrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModelFactor, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegateFactors = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModelFactor, rootPriorFactor, rateTransformation, rateModel, false);

        dataModelFactor.setLikelihoodDelegate(likelihoodDelegateFactors);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihoodFactors = new TreeDataLikelihood(likelihoodDelegateFactors, treeModel, rateModel);

        String sf = dataModelFactor.getReport();
        int indLikBegF = sf.indexOf("logMultiVariateNormalDensity = ") + 31;
        int indLikEndF = sf.indexOf("\n", indLikBegF);
        char[] logDatumLikelihoodCharF = new char[indLikEndF - indLikBegF + 1];
        sf.getChars(indLikBegF, indLikEndF, logDatumLikelihoodCharF, 0);
        double logDatumLikelihoodFactor = Double.parseDouble(String.valueOf(logDatumLikelihoodCharF));

        double likelihoodFactorData = dataLikelihoodFactors.getLogLikelihood();
        double likelihoodFactorDiffusion = dataModelFactor.getLogLikelihood();


        assertEquals("likelihoodDiagonalOURelaxedFactor", format.format(logDatumLikelihoodFactor), format.format(likelihoodFactorData + likelihoodFactorDiffusion));
    }

    public void testLikelihoodFullOUFactor() {
        System.out.println("\nTest Likelihood using full OU and factor:");

        // Diffusion
        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.1", new double[]{1.0})));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.1", new double[]{2.0})));

        Parameter[] strengthOfSelectionParameters = new Parameter[2];
        strengthOfSelectionParameters[0] = new Parameter.Default(new double[]{0.5, 0.05});
        strengthOfSelectionParameters[1] = new Parameter.Default(new double[]{0.05, 1.5});
        MatrixParameter strengthOfSelectionMatrixParam = new MatrixParameter("strengthOfSelectionMatrix", strengthOfSelectionParameters);

        DiffusionProcessDelegate diffusionProcessDelegate = new OrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModelFactor, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegateFactors = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModelFactor, rootPriorFactor, rateTransformation, rateModel, false);

        dataModelFactor.setLikelihoodDelegate(likelihoodDelegateFactors);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihoodFactors = new TreeDataLikelihood(likelihoodDelegateFactors, treeModel, rateModel);

        String sf = dataModelFactor.getReport();
        int indLikBegF = sf.indexOf("logMultiVariateNormalDensity = ") + 31;
        int indLikEndF = sf.indexOf("\n", indLikBegF);
        char[] logDatumLikelihoodCharF = new char[indLikEndF - indLikBegF + 1];
        sf.getChars(indLikBegF, indLikEndF, logDatumLikelihoodCharF, 0);
        double logDatumLikelihoodFactor = Double.parseDouble(String.valueOf(logDatumLikelihoodCharF));

        double likelihoodFactorData = dataLikelihoodFactors.getLogLikelihood();
        double likelihoodFactorDiffusion = dataModelFactor.getLogLikelihood();


        assertEquals("likelihoodFullOUFactor", format.format(logDatumLikelihoodFactor), format.format(likelihoodFactorData + likelihoodFactorDiffusion));
    }

    public void testLikelihoodFullOURelaxedFactor() {
        System.out.println("\nTest Likelihood using full Relaxed OU and factor:");

        // Diffusion
        List<BranchRateModel> optimalTraitsModels = new ArrayList<BranchRateModel>();
        optimalTraitsModels.add(new ArbitraryBranchRates(treeModel,
                new Parameter.Default("rate.1", new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}),
                false, false, false));
        optimalTraitsModels.add(new StrictClockBranchRates(new Parameter.Default("rate.2", new double[]{1.5})));

        Parameter[] strengthOfSelectionParameters = new Parameter[2];
        strengthOfSelectionParameters[0] = new Parameter.Default(new double[]{0.5, 0.05});
        strengthOfSelectionParameters[1] = new Parameter.Default(new double[]{0.05, 1.5});
        MatrixParameter strengthOfSelectionMatrixParam = new MatrixParameter("strengthOfSelectionMatrix", strengthOfSelectionParameters);

        DiffusionProcessDelegate diffusionProcessDelegate = new OrnsteinUhlenbeckDiffusionModelDelegate(treeModel, diffusionModelFactor, optimalTraitsModels, strengthOfSelectionMatrixParam);

        // Rates
        ContinuousRateTransformation rateTransformation = new ContinuousRateTransformation.Default(
                treeModel, false, false);
        BranchRateModel rateModel = new DefaultBranchRateModel();

        // CDL
        ContinuousDataLikelihoodDelegate likelihoodDelegateFactors = new ContinuousDataLikelihoodDelegate(treeModel,
                diffusionProcessDelegate, dataModelFactor, rootPriorFactor, rateTransformation, rateModel, false);

        dataModelFactor.setLikelihoodDelegate(likelihoodDelegateFactors);

        // Likelihood Computation
        TreeDataLikelihood dataLikelihoodFactors = new TreeDataLikelihood(likelihoodDelegateFactors, treeModel, rateModel);

        String sf = dataModelFactor.getReport();
        int indLikBegF = sf.indexOf("logMultiVariateNormalDensity = ") + 31;
        int indLikEndF = sf.indexOf("\n", indLikBegF);
        char[] logDatumLikelihoodCharF = new char[indLikEndF - indLikBegF + 1];
        sf.getChars(indLikBegF, indLikEndF, logDatumLikelihoodCharF, 0);
        double logDatumLikelihoodFactor = Double.parseDouble(String.valueOf(logDatumLikelihoodCharF));

        double likelihoodFactorData = dataLikelihoodFactors.getLogLikelihood();
        double likelihoodFactorDiffusion = dataModelFactor.getLogLikelihood();


        assertEquals("likelihoodFullRelaxedOUFactor", format.format(logDatumLikelihoodFactor), format.format(likelihoodFactorData + likelihoodFactorDiffusion));
    }

}