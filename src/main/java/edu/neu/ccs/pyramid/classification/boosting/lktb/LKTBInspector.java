package edu.neu.ccs.pyramid.classification.boosting.lktb;


import edu.neu.ccs.pyramid.dataset.LabelTranslator;
import edu.neu.ccs.pyramid.dataset.MultiLabel;
import edu.neu.ccs.pyramid.multilabel_classification.imlgb.IMLGradientBoosting;
import edu.neu.ccs.pyramid.regression.ConstantRegressor;
import edu.neu.ccs.pyramid.regression.Regressor;
import edu.neu.ccs.pyramid.regression.regression_tree.Decision;
import edu.neu.ccs.pyramid.regression.regression_tree.DecisionPath;
import edu.neu.ccs.pyramid.regression.regression_tree.RegTreeInspector;
import edu.neu.ccs.pyramid.regression.regression_tree.RegressionTree;
import edu.neu.ccs.pyramid.util.Pair;
import org.apache.mahout.math.Vector;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by chengli on 9/4/14.
 */
public class LKTBInspector {

    //todo: consider newton step and learning rate

    /**
     * only trees are considered
     * @param lkTreeBoost
     * @param classIndex
     * @return list of feature index and feature name pairs
     */
    public static List<Pair<Integer,String>> topFeatures(LKTreeBoost lkTreeBoost, int classIndex){
        Map<Integer,Pair<String,Double>> totalContributions = new HashMap<>();
        List<Regressor> regressors = lkTreeBoost.getRegressors(classIndex);
        List<RegressionTree> trees = regressors.stream().filter(regressor ->
                regressor instanceof RegressionTree)
                .map(regressor -> (RegressionTree) regressor)
                .collect(Collectors.toList());
        for (RegressionTree tree: trees){
            Map<Integer,Pair<String,Double>> contributions = RegTreeInspector.featureImportance(tree);
            for (Map.Entry<Integer, Pair<String,Double>> entry: contributions.entrySet()){
                int featureIndex = entry.getKey();
                Pair<String,Double> pair = entry.getValue();
                String featureName = pair.getFirst();
                Pair<String,Double> oldPair = totalContributions.getOrDefault(featureIndex,new Pair<>(featureName,0.0));
                Pair<String,Double> newPair = new Pair<>(featureName,oldPair.getSecond()+pair.getSecond());
                totalContributions.put(featureIndex,newPair);
            }
        }
        Comparator<Map.Entry<Integer, Pair<String,Double>>> comparator = Comparator.comparing(entry -> entry.getValue().getSecond());
        List<Pair<Integer,String>> list = totalContributions.entrySet().stream().sorted(comparator.reversed())
                .map(entry -> new Pair<>(entry.getKey(),entry.getValue().getFirst())).collect(Collectors.toList());
        return list;
    }

    public static List<Integer> topFeatureIndices(LKTreeBoost lkTreeBoost, int classIndex){
        return topFeatures(lkTreeBoost,classIndex).stream().map(Pair::getFirst)
                .collect(Collectors.toList());
    }

    public static List<String> topFeatureNames(LKTreeBoost lkTreeBoost, int classIndex){
        return topFeatures(lkTreeBoost,classIndex).stream().map(Pair::getSecond)
                .collect(Collectors.toList());
    }

    /**
     *
     * @param lkTreeBoosts ensemble of lktbs
     * @param classIndex
     * @return
     */
    public static List<Pair<Integer,String>> topFeatures(List<LKTreeBoost> lkTreeBoosts, int classIndex){
        Map<Integer,Pair<String,Double>> totalContributions = new HashMap<>();
        for (LKTreeBoost lkTreeBoost: lkTreeBoosts){
            List<Regressor> regressors = lkTreeBoost.getRegressors(classIndex);
            List<RegressionTree> trees = regressors.stream().filter(regressor ->
                    regressor instanceof RegressionTree)
                    .map(regressor -> (RegressionTree) regressor)
                    .collect(Collectors.toList());
            for (RegressionTree tree: trees){
                Map<Integer,Pair<String,Double>> contributions = RegTreeInspector.featureImportance(tree);
                for (Map.Entry<Integer, Pair<String,Double>> entry: contributions.entrySet()){
                    int featureIndex = entry.getKey();
                    Pair<String,Double> pair = entry.getValue();
                    String featureName = pair.getFirst();
                    Pair<String,Double> oldPair = totalContributions.getOrDefault(featureIndex,new Pair<>(featureName,0.0));
                    Pair<String,Double> newPair = new Pair<>(featureName,oldPair.getSecond()+pair.getSecond());
                    totalContributions.put(featureIndex,newPair);
                }
            }
        }

        Comparator<Map.Entry<Integer, Pair<String,Double>>> comparator = Comparator.comparing(entry -> entry.getValue().getSecond());
        List<Pair<Integer,String>> list = totalContributions.entrySet().stream().sorted(comparator.reversed())
                .map(entry -> new Pair<>(entry.getKey(),entry.getValue().getFirst())).collect(Collectors.toList());
        return list;
    }

    public static List<Integer> topFeatureIndices(List<LKTreeBoost> lkTreeBoosts, int classIndex){
        return topFeatures(lkTreeBoosts,classIndex).stream().map(Pair::getFirst)
                .collect(Collectors.toList());
    }

    public static List<String> topFeatureNames(List<LKTreeBoost> lkTreeBoosts, int classIndex){
        return topFeatures(lkTreeBoosts,classIndex).stream().map(Pair::getSecond)
                .collect(Collectors.toList());
    }

    public static Set<Integer> recentlyUsedFeatures(LKTreeBoost boosting, int k){
        Set<Integer> features = new HashSet<>();
        List<Regressor> regressors = boosting.getRegressors(k);
        int size = regressors.size();
        Regressor lastOne = regressors.get(size-1);
        if (lastOne instanceof RegressionTree){
            features.addAll(RegTreeInspector.features((RegressionTree)lastOne));
        }
        return features;
    }

    public static String decisionProcess(LKTreeBoost boosting, Vector vector, int classIndex, int limit){
        StringBuilder sb = new StringBuilder();
        List<Regressor> regressors = boosting.getRegressors(classIndex).stream().collect(Collectors.toList());
        List<Decision> decisions = new ArrayList<>();
        for (int i=0;i<regressors.size();i++){
            Regressor regressor = regressors.get(i);
            if (regressor instanceof ConstantRegressor){
                sb.append("prior score for the class = ");
                sb.append(((ConstantRegressor) regressor).getScore()).append("\n");
            }

            if (regressor instanceof RegressionTree){
                RegressionTree tree = (RegressionTree)regressor;
                Decision decision = new Decision(tree,vector);
                decisions.add(decision);
            }
        }
        Comparator<Decision> comparator = Comparator.comparing(decision -> Math.abs(decision.getScore()));
        List<Decision> merged = Decision.merge(decisions).stream().sorted(comparator.reversed())
                .limit(limit).collect(Collectors.toList());
        for (Decision decision: merged){
            sb.append(decision.toString());
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String analyzeMistake(LKTreeBoost boosting, Vector vector,
                                        int trueLabel, int prediction,
                                        LabelTranslator labelTranslator, int limit){
        StringBuilder sb = new StringBuilder();
        sb.append("score for the true label ").append(trueLabel).append("(").append(labelTranslator.toExtLabel(trueLabel))
                .append(")").append(" = ").append(boosting.predictClassScore(vector,trueLabel)).append("\n");

        sb.append("score for the prediction ").append(prediction).append("(").append(labelTranslator.toExtLabel(prediction))
                .append(")").append(" = ").append(boosting.predictClassScore(vector,prediction)).append("\n");
        sb.append("decision process for the true label ").append(trueLabel).append("(").append(labelTranslator.toExtLabel(trueLabel))
                .append(")").append(":").append("\n");
        sb.append(decisionProcess(boosting,vector,trueLabel,limit));

        sb.append("decision process for the prediction ").append(prediction).append("(").append(labelTranslator.toExtLabel(prediction))
                .append(")").append(":").append("\n");
        sb.append(decisionProcess(boosting,vector,prediction,limit));

        return sb.toString();
    }
}
