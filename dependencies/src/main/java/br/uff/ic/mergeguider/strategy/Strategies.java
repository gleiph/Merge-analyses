/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.uff.ic.mergeguider.strategy;

import br.uff.ic.mergeguider.MergeGuider;
import br.uff.ic.mergeguider.datastructure.ConflictingChunkInformation;
import br.uff.ic.mergeguider.datastructure.ConflictingChunksDependency;
import br.uff.ic.mergeguider.datastructure.MergeDependency;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author gleiph
 */
public class Strategies {

    public static void main(String[] args) {
        String projectPath = "/Users/gleiph/repositories/voldemort";
        String shaLeft = "aee112d9ef0ed960c7bc9955d7e85e6ed6ac91a0";
        String shaRight = "fd5dbeb5113ffed51cf1836ac78b129a4bea4cb6";
        String sanbox = "/Users/gleiph/repositories/icse/";

        try {
            MergeDependency dependencies = MergeGuider.performMerge(projectPath, shaLeft, shaRight, sanbox);

            List<NodeDependency> prepare = PrepareNodes.prepare(dependencies);

            //Random strategy 
            System.out.println("Random");
            List<ConflictingChunkInformation> resolutionOrder = Strategies.random(dependencies, prepare);

            for (ConflictingChunkInformation chunk : resolutionOrder) {
                int indexOf = dependencies.getCcis().indexOf(chunk);
                System.out.println("CC" + indexOf);
            }

            System.out.println("Sequencial");
            List<ConflictingChunkInformation> sequencial = Strategies.sequencial(dependencies, prepare);

            for (ConflictingChunkInformation chunk : sequencial) {
                System.out.println("CC" + dependencies.getCcis().indexOf(chunk));
            }

            System.out.println("Greed");
            List<ConflictingChunkInformation> greed = Strategies.greedy(dependencies, prepare);

            for (ConflictingChunkInformation chunk : greed) {
                System.out.println("CC" + dependencies.getCcis().indexOf(chunk));
            }
        } catch (IOException ex) {
            Logger.getLogger(Strategies.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static List<ConflictingChunkInformation> random(MergeDependency mergeDependency, List<NodeDependency> nodesDependency) {

        Random rand = new Random();
        List<ConflictingChunkInformation> ccis = new ArrayList<>(mergeDependency.getCcis());
        List<ConflictingChunkInformation> result = new ArrayList<>();

        while (!ccis.isEmpty()) {
            int randomNumber = rand.nextInt(ccis.size());
//            System.out.println("randomNumber = " + randomNumber);

            ConflictingChunkInformation remove = ccis.remove(randomNumber);
            result.add(remove);
        }

        return result;
    }

    public static List<ConflictingChunkInformation> sequencial(MergeDependency mergeDependency, List<NodeDependency> nodesDependency) {

        return mergeDependency.getCcis();
    }

    public static NodeDependency lowestDependencies(List<NodeDependency> nodes) {

        if (nodes.isEmpty()) {
            return null;
        }

        int i = 0;

        NodeDependency result = null;
        NodeDependency currentNode = null;

        //Take the first unvisited node
        for (; i < nodes.size(); i++) {
            currentNode = nodes.get(i);

            if (!currentNode.isVisited()) {
                result = currentNode;
                break;
            }
        }

        //Get the node with lowest number of dependencies 
        for (; i < nodes.size(); i++) {

            currentNode = nodes.get(i);

            if (!currentNode.isVisited()
                    && currentNode.getDependencies() < result.getDependencies()) {
                result = currentNode;
            }

        }

        return result;
    }

    public static List<ConflictingChunkInformation> greedy(MergeDependency mergeDependency, List<NodeDependency> nodesDependency) {

        Random rand = new Random();
        List<ConflictingChunkInformation> ccis = mergeDependency.getCcis();
        List<ConflictingChunkInformation> result = new ArrayList<>();

        for (NodeDependency node : nodesDependency) {
            node.setVisited(false);
        }

        NodeDependency lowestDependencies = lowestDependencies(nodesDependency);
        int indexOf = nodesDependency.indexOf(lowestDependencies);

        while (lowestDependencies != null) {

            ConflictingChunkInformation cci = ccis.get(indexOf);
            result.add(cci);

            nodesDependency.get(indexOf).setVisited(true);

            //Updating dependencies number 
            for (ConflictingChunksDependency dependency : mergeDependency.getConflictingChunksDependencies()) {

                if (dependency.getDependsOn().equals(cci)) {

                    ConflictingChunkInformation reference = dependency.getReference();
                    ConflictingChunkInformation dependsOn = dependency.getDependsOn();
                    int dependencyIndex = ccis.indexOf(reference);
                    int dependentIndex = ccis.indexOf(dependsOn);

                    //updating dependencies
                    int dependencies = nodesDependency.get(dependencyIndex).getDependencies();
                    nodesDependency.get(dependencyIndex).setDependencies(dependencies - 1);

                    //updating dependent
                    int dependent = nodesDependency.get(dependentIndex).getDependent();
                    nodesDependency.get(dependentIndex).setDependent(dependent - 1);

                }

            }

            lowestDependencies = lowestDependencies(nodesDependency);
            indexOf = nodesDependency.indexOf(lowestDependencies);
        }

        return result;
    }

    
}
