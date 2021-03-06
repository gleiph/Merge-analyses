/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.uff.ic.mergeguider;

import br.uff.ic.gems.resources.utils.MergeStatusAnalizer;
import br.uff.ic.gems.resources.vcs.Git;
import br.uff.ic.mergeguider.datastructure.ConflictingChunksDependency;
import br.uff.ic.mergeguider.datastructure.ConflictingChunkInformation;
import br.uff.ic.mergeguider.datastructure.MergeDependency;
import br.uff.ic.mergeguider.dependency.DependencyType;
import br.uff.ic.mergeguider.javaparser.ClassLanguageContructs;
import br.uff.ic.mergeguider.javaparser.ProjectAST;
import br.uff.ic.mergeguider.languageConstructs.Location;
import br.uff.ic.mergeguider.languageConstructs.MyAnnotationDeclaration;
import br.uff.ic.mergeguider.languageConstructs.MyAnnotationUsage;
import br.uff.ic.mergeguider.languageConstructs.MyAttributeDeclaration;
import br.uff.ic.mergeguider.languageConstructs.MyAttributeCall;
import br.uff.ic.mergeguider.languageConstructs.MyMethodDeclaration;
import br.uff.ic.mergeguider.languageConstructs.MyMethodInvocation;
import br.uff.ic.mergeguider.languageConstructs.MyTypeDeclaration;
import br.uff.ic.mergeguider.languageConstructs.MyVariableCall;
import br.uff.ic.mergeguider.languageConstructs.MyVariableDeclaration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleType;

/**
 *
 * @author gleiph
 */
public class MergeGuider {

    public static void main(String[] args) {

        //Home
        List<String> projectsPath = new ArrayList<>();
        projectsPath.add("/Users/gleiph/repositories/icse/antlr4");
        projectsPath.add("/Users/gleiph/repositories/icse/lombok");
        projectsPath.add("/Users/gleiph/repositories/icse/mct");
        projectsPath.add("/Users/gleiph/repositories/icse/twitter4j");
        projectsPath.add("/Users/gleiph/repositories/icse/voldemort");

        String sandbox = "/Users/gleiph/repositories/icse";
        //UFF
//        String projectPath = "/home/gmenezes/repositorios/antlr4";
//        String projectPath = "/home/gmenezes/repositorios/lombok";
//        String projectPath = "/home/gmenezes/repositorios/twitter4j";
//        String projectPath = "/home/gmenezes/repositorios/mct";

        for (String projectPath : projectsPath) {
            System.out.println("Project: " + projectPath);
            analyzeProject(projectPath, sandbox);
        }

    }

    public static void analyzeProject(String projectPath, String sandbox) {
        //        String sandbox = "/home/gmenezes/repositorios/";
        List<String> mergeRevisions = Git.getMergeRevisions(projectPath);
        int hasDependencies = 0, hasNoDependencies = 0, oneCC = 0, moreThanOneCC = 0;

        int conflictingChunks, dependecies/*, files, filesDepedency*/;
        String project, mergeSHA, leftSHA, rightSHA, mergebaseSHA;

        project = projectPath;

        for (String mergeRevision : mergeRevisions) {

            mergeSHA = mergeRevision;

            List<String> parents = Git.getParents(projectPath, mergeRevision);

            if (parents.size() == 2) {
                leftSHA = parents.get(0);
                rightSHA = parents.get(1);

                MergeDependency mergeDependency;
                try {
//                    System.out.println("\tMerging " + SHALeft + " and " + SHARight);
                    mergeDependency = performMerge(projectPath, leftSHA, rightSHA, sandbox);

                    //Treating dependencies 
                    if (mergeDependency == null) {
                        conflictingChunks = 0;
                        dependecies = 0;
                    } else {
                        conflictingChunks = mergeDependency.getConflictingChunksAmount();
                        dependecies = mergeDependency.getConflictingChunksDependencies().size();
                    }

                    System.out.println(project + ", " + conflictingChunks + ", " + dependecies + ", " + mergeSHA + ", " + leftSHA + ", " + rightSHA);
                } catch (IOException ex) {
                    System.out.println("Merge between revisions " + leftSHA + " and " + rightSHA + " was not performed.");

                }

            }
        }
        System.out.println("hasNoDependencies = " + hasNoDependencies);
        System.out.println("hasDependencies = " + hasDependencies);
        System.out.println("moreThanOneCC = " + moreThanOneCC);
        System.out.println("oneCC = " + oneCC);
    }

    public static MergeDependency performMerge(String projectPath, String SHALeft, String SHARight, String sandbox) throws IOException {
        if (isFailedMerge(projectPath, SHALeft, SHARight)) {

            List<String> conflictedFilePaths = Git.conflictedFiles(projectPath);

            //Getting conflicting chunks
            List<ConflictingChunkInformation> ccis = ConflictingChunkInformation.extractConflictingChunksInformation(conflictedFilePaths);

            //Extracting Left AST
            System.out.println("Cloning left repository...");
            String repositoryLeft = sandbox + File.separator + "left";

            clone(projectPath, repositoryLeft);
            Git.reset(repositoryLeft);
            Git.clean(repositoryLeft);
            Git.checkout(repositoryLeft, SHALeft);

            System.out.println("Extracting left repository AST...");

            List<ClassLanguageContructs> ASTLeft = extractAST(repositoryLeft);

            //Extracting Right AST
            System.out.println("Cloning right repository...");

            String repositoryRight = sandbox + File.separator + "right";

            clone(projectPath, repositoryRight);
            Git.reset(repositoryRight);
            Git.clean(repositoryRight);
            Git.checkout(repositoryRight, SHARight);

            System.out.println("Extracting right repository AST...");

            List<ClassLanguageContructs> ASTRight = extractAST(repositoryRight);

            System.out.println("Repositioning...");
            repositioningConflictingChunksInformation(ccis, repositoryLeft, projectPath, repositoryRight);

            //Creating depedency matrix
            System.out.println("Extracting dependency matrix...");
            MergeDependency mergeDependency = extractDepedencies(ccis, projectPath, ASTLeft, ASTRight);
            mergeDependency.setCcis(ccis);

            return mergeDependency;
        }

        return null;
    }

    public static void repositioningConflictingChunksInformation(List<ConflictingChunkInformation> ccis, String repositoryLeft, String projectPath, String repositoryRight) {
        //Repositioning conflicting chunk information to both sides
        for (ConflictingChunkInformation cci : ccis) {

            String baseFilePath = cci.getFilePath();
            String leftFilePath;

            if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
                leftFilePath = repositoryLeft + File.separator
                        + cci.getRelativePathLeft();
            } else {
                leftFilePath = repositoryLeft + File.separator
                        + baseFilePath.replace(projectPath, "");
            }

            String rightFilePath;

            if (cci.isRenamed() && cci.getRelativePathRight() != null) {
                rightFilePath = repositoryRight + File.separator + cci.getRelativePathRight();
            } else {
                rightFilePath = repositoryRight + File.separator
                        + baseFilePath.replace(projectPath, "");
            }

            cci.reposition(baseFilePath, leftFilePath, rightFilePath);

        }
    }

    public static MergeDependency extractDepedencies(List<ConflictingChunkInformation> ccis, String projectPath,
            List<ClassLanguageContructs> ASTLeft, List<ClassLanguageContructs> ASTRight) {

        MergeDependency mergeDependency = new MergeDependency();
        mergeDependency.setConflictingChunksAmount(ccis.size());

        for (ConflictingChunkInformation cci : ccis) {

            //Find method declaration that has some intersection with a method declaration
            List<MyMethodDeclaration> leftMethodDeclarations = leftCCMethodDeclarations(projectPath, cci, ASTLeft);
            List<MyMethodDeclaration> rightMethodDeclarations = rightCCMethodDeclaration(projectPath, cci, ASTRight);

            //Find attribute declarations
            List<MyAttributeDeclaration> leftAttributeDeclarations = leftAttributes(projectPath, cci, ASTLeft);
            List<MyAttributeDeclaration> rightAttributeDeclarations = rightAttributes(projectPath, cci, ASTRight);

            //Find variable declarations
            List<MyVariableDeclaration> leftVariableDeclarations = leftVariableDeclarations(projectPath, cci, ASTLeft);
            List<MyVariableDeclaration> rightVariableDeclarations = rightVariableDeclarations(projectPath, cci, ASTRight);

            //Finding Type declarations
            List<MyTypeDeclaration> leftTypeDeclarations = leftTypeDeclarations(projectPath, cci, ASTLeft);
            List<MyTypeDeclaration> rightTypeDeclarations = rightTypeDeclarations(projectPath, cci, ASTRight);

            //Find Annotation declarations
            List<MyAnnotationDeclaration> leftAnnotationDeclarations = leftAnnotationDeclarations(projectPath, cci, ASTLeft);
            List<MyAnnotationDeclaration> rightAnnotationDeclarations = rightAnnotationDeclarations(projectPath, cci, ASTRight);

            int rowNumber = ccis.indexOf(cci);

            for (ConflictingChunkInformation cciAux : ccis) {
                int columnNumber = ccis.indexOf(cciAux);

                List<MyMethodInvocation> leftMethodInvocations = leftCCMethodInvocations(projectPath, cciAux, ASTLeft);
                List<MyMethodInvocation> rightMethodInvocations = rightCCMethodInvocations(projectPath, cciAux, ASTRight);

                List<MyAttributeCall> leftAttributeCalls = leftAttributeCalls(projectPath, cciAux, ASTLeft);
                List<MyAttributeCall> rightAttributeCalls = rightAttributeCalls(projectPath, cciAux, ASTRight);

                List<MyVariableCall> leftVariableCalls = leftVariableCalls(projectPath, cciAux, ASTLeft);
                List<MyVariableCall> rightVariableCalls = rightVariableCalls(projectPath, cciAux, ASTRight);

                List<MyTypeDeclaration> leftTypeDeclarationsAux = leftTypeDeclarations(projectPath, cci, ASTLeft);
                List<MyTypeDeclaration> rightTypeDeclarationsAux = rightTypeDeclarations(projectPath, cci, ASTRight);

                List<MyAnnotationUsage> leftAnnotationUsages = leftAnnotationUsages(projectPath, cci, ASTLeft);
                List<MyAnnotationUsage> rightAnnotationUsages = rightAnnotationUsages(projectPath, cci, ASTRight);

                boolean hasMethodDependecy
                        = hasMethodDependency(leftMethodDeclarations, leftMethodInvocations)
                        || hasMethodDependency(rightMethodDeclarations, rightMethodInvocations);

                if (hasMethodDependecy) {
                    //CC(rowNumber) depends on CC(ColumnNumber)
                    if (columnNumber != rowNumber) {
                        ConflictingChunksDependency conflictingChunksDependency
                                = new ConflictingChunksDependency(cciAux, cci, DependencyType.METHOD_DECLARATION_INVOCATION);
                        mergeDependency.getConflictingChunksDependencies().add(conflictingChunksDependency);
                    }
                }

                boolean hasAttributeDepedency
                        = hasAttributeDependency(leftAttributeDeclarations, leftAttributeCalls)
                        || hasAttributeDependency(rightAttributeDeclarations, rightAttributeCalls);

                if (hasAttributeDepedency) {
                    //CC(rowNumber) depends on CC(ColumnNumber)
                    if (columnNumber != rowNumber) {
                        ConflictingChunksDependency conflictingChunksDependency
                                = new ConflictingChunksDependency(cciAux, cci, DependencyType.ATTRIBUTE_DECLARATION_USAGE);
                        mergeDependency.getConflictingChunksDependencies().add(conflictingChunksDependency);
                    }
                }

                boolean hasVariableDepedency
                        = hasVariableDependency(leftVariableDeclarations, leftVariableCalls)
                        || hasVariableDependency(rightVariableDeclarations, rightVariableCalls);

                if (hasVariableDepedency) {
                    //CC(rowNumber) depends on CC(ColumnNumber)
                    if (columnNumber != rowNumber) {
                        ConflictingChunksDependency conflictingChunksDependency
                                = new ConflictingChunksDependency(cciAux, cci, DependencyType.VARIABLE_DECLARATION_USAGE);
                        mergeDependency.getConflictingChunksDependencies().add(conflictingChunksDependency);
                    }
                }

//                boolean hasTypeDeclarationDependency
//                        = hasTypeDeclarationDependencyAttribute(leftTypeDeclarations, leftAttributeDeclarations)
//                        || hasTypeDeclarationDependencyAttribute(rightTypeDeclarations, rightAttributeDeclarations)
//                        || hasTypeDeclarationDependencyVariable(leftTypeDeclarations, leftVariableDeclarations)
//                        || hasTypeDeclarationDependencyVariable(rightTypeDeclarations, rightVariableDeclarations);
//
//                if (hasTypeDeclarationDependency) {
//                    //CC(rowNumber) depends on CC(ColumnNumber)
//                    if (columnNumber != rowNumber) {
//                        ConflictingChunksDependency conflictingChunksDependency
//                                = new ConflictingChunksDependency(cciAux, cci, DependencyType.TYPE_DECLARATION_USAGE);
//                        mergeDependency.getConflictingChunksDependencies().add(conflictingChunksDependency);
//                    }
//                }
                boolean hasDependencyTypeDeclarationInterface
                        = hasDependencyTypeDeclarationInterface(leftTypeDeclarations, leftTypeDeclarationsAux)
                        || hasDependencyTypeDeclarationInterface(rightTypeDeclarations, rightTypeDeclarationsAux);

                if (hasDependencyTypeDeclarationInterface) {
                    //CC(rowNumber) depends on CC(ColumnNumber)
                    if (columnNumber != rowNumber) {
                        ConflictingChunksDependency conflictingChunksDependency
                                = new ConflictingChunksDependency(cciAux, cci, DependencyType.TYPE_DECLARATION_INTERFACE);
                        mergeDependency.getConflictingChunksDependencies().add(conflictingChunksDependency);
                    }
                }

                boolean hasAnnotationDependency
                        = hasDependencyAnnotationDeclarationUsage(leftAnnotationDeclarations, leftAnnotationUsages)
                        || hasDependencyAnnotationDeclarationUsage(rightAnnotationDeclarations, rightAnnotationUsages);

                if (hasAnnotationDependency) {
                    //CC(rowNumber) depends on CC(ColumnNumber)
                    if (columnNumber != rowNumber) {
                        ConflictingChunksDependency conflictingChunksDependency
                                = new ConflictingChunksDependency(cciAux, cci, DependencyType.ANNOTATION_DECLARATION_USAGE);
                        mergeDependency.getConflictingChunksDependencies().add(conflictingChunksDependency);
                    }
                }

            }

        }

        return mergeDependency;
    }

    public static int[][] instanciatingDepedencyMatrix(int order) {
        int[][] dependencyMatrix = new int[order][order];
        for (int i = 0; i < order; i++) {
            for (int j = 0; j < order; j++) {
                dependencyMatrix[i][j] = 0;
            }
        }
        return dependencyMatrix;
    }

    public static void printDepedencyMatrix(int[][] dependencyMatrix) {

        int order = dependencyMatrix.length;
        for (int i = 0; i < order; i++) {
            for (int j = 0; j < order; j++) {
                System.out.print(dependencyMatrix[i][j] + "  ");
            }
            System.out.println("");
        }
    }

    public static boolean hasMethodDependency(List<MyMethodDeclaration> methodDeclarations, List<MyMethodInvocation> methodInvocations) {

        for (MyMethodInvocation methodInvocation : methodInvocations) {
            for (MyMethodDeclaration methodDeclaration : methodDeclarations) {

                if (sameMethod(methodDeclaration, methodInvocation)) {
                    return true;
                }

            }
        }

        return false;
    }

    public static boolean sameMethod(MyMethodDeclaration methodDeclaration, MyMethodInvocation methodInvocation) {

        IMethodBinding methodDeclarationBinding = methodDeclaration.getMethodDeclaration().resolveBinding();
        IMethodBinding methodInvocationBinding = methodInvocation.getMethodInvocation().resolveMethodBinding();

        if (methodDeclarationBinding != null && methodInvocationBinding != null && methodDeclarationBinding.equals(methodInvocationBinding)) {
            return true;
        } else {
            return false;
        }
    }

    //Treating attribute dependency between declaration and usage
    public static boolean hasAttributeDependency(List<MyAttributeDeclaration> attributeDeclarations, List<MyAttributeCall> attributeCalls) {

        for (MyAttributeCall attributeCall : attributeCalls) {
            for (MyAttributeDeclaration attributeDeclaration : attributeDeclarations) {

                if (sameAttribute(attributeDeclaration, attributeCall)) {
                    return true;
                }

            }
        }

        return false;
    }

    public static boolean sameAttribute(MyAttributeDeclaration attributeDeclaration, MyAttributeCall attributeCall) {

        IVariableBinding attributeDeclarationBinding = attributeDeclaration.getFieldDeclaration().resolveBinding();
        IBinding attributeCallBinding = attributeCall.getSimpleName().resolveBinding();

        if (attributeDeclarationBinding != null && attributeCallBinding != null && attributeDeclarationBinding.equals(attributeCallBinding)) {
            return true;
        } else {
            return false;
        }
    }

    //Treating variable dependency between declaration and usage
    public static boolean hasVariableDependency(List<MyVariableDeclaration> variableDeclarations, List<MyVariableCall> variableCalls) {

        for (MyVariableCall variableCall : variableCalls) {
            for (MyVariableDeclaration variableDeclaration : variableDeclarations) {

                if (sameVariable(variableDeclaration, variableCall)) {
                    return true;
                }

            }
        }

        return false;
    }

    public static boolean sameVariable(MyVariableDeclaration variableDeclaration, MyVariableCall variableCall) {

        IVariableBinding attributeDeclarationBinding = variableDeclaration.resolveBinding();
        IBinding attributeCallBinding = variableCall.getSimpleName().resolveBinding();

        if (attributeDeclarationBinding != null && attributeCallBinding != null && attributeDeclarationBinding.equals(attributeCallBinding)) {
            return true;
        } else {
            return false;
        }
    }

    //Treating dependency between a type declaration and its usage as variable declaration
    public static boolean hasTypeDeclarationDependencyVariable(List<MyTypeDeclaration> typeDeclarations, List<MyVariableDeclaration> variableDeclarations) {

        for (MyVariableDeclaration variableDeclaration : variableDeclarations) {
            for (MyTypeDeclaration typeDeclaration : typeDeclarations) {

                if (sameTypeDeclaration(typeDeclaration, variableDeclaration)) {
                    return true;
                }

            }
        }

        return false;
    }

    public static boolean sameTypeDeclaration(MyTypeDeclaration typeDeclaration, MyVariableDeclaration variableDeclaration) {

        ITypeBinding typeDeclarationBinding = typeDeclaration.getTypeDeclaration().resolveBinding();
        ITypeBinding variableDeclarationBinding = variableDeclaration.resolveTypeBinding();

        if (typeDeclarationBinding != null && variableDeclarationBinding != null && typeDeclarationBinding.equals(variableDeclarationBinding)) {
            return true;
        } else {
            return false;
        }
    }

    //Treating dependency between a type declaration and its usage as attribute declaration
    public static boolean hasTypeDeclarationDependencyAttribute(List<MyTypeDeclaration> typeDeclarations, List<MyAttributeDeclaration> attributeDeclarations) {

        for (MyAttributeDeclaration attributeDeclaration : attributeDeclarations) {
            for (MyTypeDeclaration typeDeclaration : typeDeclarations) {

                if (sameTypeDeclaration(typeDeclaration, attributeDeclaration)) {
                    return true;
                }

            }
        }

        return false;
    }

    public static boolean sameTypeDeclaration(MyTypeDeclaration typeDeclaration, MyAttributeDeclaration attributeDeclaration) {

        ITypeBinding typeDeclarationBinding = typeDeclaration.getTypeDeclaration().resolveBinding();
        IBinding variableDeclarationBinding = attributeDeclaration.resolveTypeBinding();

        if (typeDeclarationBinding != null && variableDeclarationBinding != null && typeDeclarationBinding.equals(variableDeclarationBinding)) {
            return true;
        } else {
            return false;
        }
    }

    //Treating dependency between a type declaration its interfaces
    public static boolean hasDependencyTypeDeclarationInterface(List<MyTypeDeclaration> typeDeclarations, List<MyTypeDeclaration> typeDeclarationsAux) {

        for (MyTypeDeclaration typeDeclaration : typeDeclarations) {
            for (MyTypeDeclaration typeDeclarationAux : typeDeclarationsAux) {
                List<SimpleType> interfaces = typeDeclarationAux.getInterfaces();
                for (SimpleType aInterface : interfaces) {
                    if (sameTypeDeclaration(typeDeclaration, aInterface)) {
                        return true;
                    }
                }

            }
        }

        return false;
    }

    public static boolean sameTypeDeclaration(MyTypeDeclaration typeDeclaration, SimpleType interfaace) {

        ITypeBinding typeDeclarationBinding = typeDeclaration.getTypeDeclaration().resolveBinding();
        ITypeBinding variableDeclarationBinding = interfaace.resolveBinding();

        if (typeDeclarationBinding != null && variableDeclarationBinding != null && typeDeclarationBinding.equals(variableDeclarationBinding)) {
            return true;
        } else {
            return false;
        }
    }

    public static void printConflictingChunksInformation(List<ConflictingChunkInformation> ccis) throws IOException {
        for (ConflictingChunkInformation cci : ccis) {
            List<String> fileLines = FileUtils.readLines(new File(cci.getFilePath()));

            List<String> ccLines = fileLines.subList(cci.getBegin(), cci.getEnd() + 1);

            System.out.println(cci.toString());
            for (String ccLine : ccLines) {
                System.out.println("\t\t" + ccLine);
            }
        }
    }

    public static boolean isFailedMerge(String projectPath, String SHALeft, String SHARight) {
        Git.reset(projectPath);
        Git.clean(projectPath);
        Git.checkout(projectPath, SHALeft);
        List<String> merge = Git.merge(projectPath, SHARight, false, false);

        return MergeStatusAnalizer.isConflict(merge);
    }

    public static List<ClassLanguageContructs> extractAST(String projectPath) {

        ProjectAST projectAST = new ProjectAST(projectPath);

        return projectAST.getClassesLanguageConstructs();
    }

    public static void clone(String projectPath, String target) throws IOException {

        File targetFile = new File(target);

        if (targetFile.isDirectory()) {
            FileUtils.deleteDirectory(targetFile);
        }

        Git.clone(projectPath, projectPath, target);

    }

    public static List<MyMethodDeclaration> leftCCMethodDeclarations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyMethodDeclaration> result = new ArrayList<>();

        String relativePath = null;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyMethodDeclaration> methodDeclarations = AST.getMethodDeclarations();

                for (MyMethodDeclaration methodDeclaration : methodDeclarations) {
                    if (leftHasIntersection(methodDeclaration, cci)) {
                        result.add(methodDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static boolean leftHasIntersection(Location location, ConflictingChunkInformation cci) {

        if (cci.getLeftBegin() > location.getElementLineEnd()) {
            return false;
        } else if (cci.getLeftEnd() < location.getElementLineBegin()) {
            return false;
        } else {
            return true;
        }

    }

    public static boolean leftHasIntersection(MyMethodDeclaration methodDeclaration, ConflictingChunkInformation cci) {

        return leftHasIntersection(methodDeclaration.getLocation(), cci);

    }

    public static List<MyMethodDeclaration> rightCCMethodDeclaration(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyMethodDeclaration> result = new ArrayList<>();

        String relativePath = null;

        if (cci.isRenamed() && cci.getRelativePathRight() != null) {
            relativePath = cci.getRelativePathRight();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyMethodDeclaration> methodDeclarations = AST.getMethodDeclarations();

                for (MyMethodDeclaration methodDeclaration : methodDeclarations) {
                    if (rightHasIntersection(methodDeclaration, cci)) {
                        result.add(methodDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static boolean rightHasIntersection(Location location, ConflictingChunkInformation cci) {

        if (cci.getRightBegin() > location.getElementLineEnd()) {
            return false;
        } else if (cci.getRightEnd() < location.getElementLineBegin()) {
            return false;
        } else {
            return true;
        }

    }

    public static boolean rightHasIntersection(MyMethodDeclaration methodDeclaration, ConflictingChunkInformation cci) {

        return rightHasIntersection(methodDeclaration.getLocation(), cci);

    }

    public static List<MyMethodInvocation> leftCCMethodInvocations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyMethodInvocation> result = new ArrayList<>();

        String relativePath = null;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyMethodInvocation> methodInvocations = AST.getMethodInvocations();

                for (MyMethodInvocation methodInvocation : methodInvocations) {
                    if (leftHasIntersection(methodInvocation.getLocation(), cci)) {
                        result.add(methodInvocation);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyMethodInvocation> rightCCMethodInvocations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyMethodInvocation> result = new ArrayList<>();

        String relativePath = null;

        if (cci.isRenamed() && cci.getRelativePathRight() != null) {
            relativePath = cci.getRelativePathRight();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyMethodInvocation> methodInvocations = AST.getMethodInvocations();

                for (MyMethodInvocation methodInvocation : methodInvocations) {
                    if (rightHasIntersection(methodInvocation.getLocation(), cci)) {
                        result.add(methodInvocation);
                    }
                }
            }
        }

        return result;
    }

    public static void printDependencies(int[][] dependencyMatrix) {

        for (int i = 0; i < dependencyMatrix.length; i++) {
            int[] row = dependencyMatrix[i];
            for (int j = 0; j < row.length; j++) {
                if (dependencyMatrix[i][j] == 1) {
                    System.out.println("CC" + (i + 1) + " depends on CC" + (j + 1));
                }
            }

        }

    }

//    public static List<CCDependency> extractDependencies(int[][] dependencyMatrix) {
//
//        List<CCDependency> result = new ArrayList<>();
//
//        for (int i = 0; i < dependencyMatrix.length; i++) {
//            int[] row = dependencyMatrix[i];
//            for (int j = 0; j < row.length; j++) {
//                if (dependencyMatrix[i][j] == 1) {
//                    result.add(new ConflictingChunksDependency(i, j));
//                }
//            }
//
//        }
//
//        return result;
//    }
    public static List<MyAttributeDeclaration> leftAttributes(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyAttributeDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAttributeDeclaration> attributeDeclarations = AST.getAttributes();

                for (MyAttributeDeclaration attributeDeclaration : attributeDeclarations) {
                    if (leftHasIntersection(attributeDeclaration.getLocation(), cci)) {
                        result.add(attributeDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyAttributeDeclaration> rightAttributes(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyAttributeDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAttributeDeclaration> attributeDeclarations = AST.getAttributes();

                for (MyAttributeDeclaration attributeDeclaration : attributeDeclarations) {
                    if (rightHasIntersection(attributeDeclaration.getLocation(), cci)) {
                        result.add(attributeDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyAttributeCall> leftAttributeCalls(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyAttributeCall> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAttributeCall> attributeCalls = AST.getAttributeCalls();

                for (MyAttributeCall attributeCall : attributeCalls) {
                    if (leftHasIntersection(attributeCall.getLocation(), cci)) {
                        result.add(attributeCall);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyAttributeCall> rightAttributeCalls(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyAttributeCall> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAttributeCall> attributeCalls = AST.getAttributeCalls();

                for (MyAttributeCall attributeCall : attributeCalls) {
                    if (rightHasIntersection(attributeCall.getLocation(), cci)) {
                        result.add(attributeCall);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyVariableDeclaration> leftVariableDeclarations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyVariableDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyVariableDeclaration> variableDeclarations = AST.getVariableDeclarations();

                for (MyVariableDeclaration variableDeclaration : variableDeclarations) {
                    if (leftHasIntersection(variableDeclaration.getLocation(), cci)) {
                        result.add(variableDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyVariableDeclaration> rightVariableDeclarations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyVariableDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyVariableDeclaration> variableDeclarations = AST.getVariableDeclarations();

                for (MyVariableDeclaration variableDeclaration : variableDeclarations) {
                    if (rightHasIntersection(variableDeclaration.getLocation(), cci)) {
                        result.add(variableDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyVariableCall> leftVariableCalls(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyVariableCall> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyVariableCall> variableCalls = AST.getVariableCalls();

                for (MyVariableCall variableCall : variableCalls) {
                    if (leftHasIntersection(variableCall.getLocation(), cci)) {
                        result.add(variableCall);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyVariableCall> rightVariableCalls(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyVariableCall> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyVariableCall> variableCalls = AST.getVariableCalls();

                for (MyVariableCall variableCall : variableCalls) {
                    if (rightHasIntersection(variableCall.getLocation(), cci)) {
                        result.add(variableCall);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyTypeDeclaration> leftTypeDeclarations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyTypeDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyTypeDeclaration> typeDeclarations = AST.getTypeDeclarations();

                for (MyTypeDeclaration typeDeclaration : typeDeclarations) {
                    if (leftHasIntersection(typeDeclaration.getLocation(), cci)) {
                        result.add(typeDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyTypeDeclaration> rightTypeDeclarations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyTypeDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyTypeDeclaration> typeDeclarations = AST.getTypeDeclarations();

                for (MyTypeDeclaration typeDeclaration : typeDeclarations) {
                    if (rightHasIntersection(typeDeclaration.getLocation(), cci)) {
                        result.add(typeDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyAnnotationDeclaration> leftAnnotationDeclarations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyAnnotationDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAnnotationDeclaration> annotationDeclarations = AST.getAnnotationDeclarations();

                for (MyAnnotationDeclaration annotationDeclaration : annotationDeclarations) {
                    if (leftHasIntersection(annotationDeclaration.getLocation(), cci)) {
                        result.add(annotationDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyAnnotationDeclaration> rightAnnotationDeclarations(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyAnnotationDeclaration> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAnnotationDeclaration> annotationDeclarations = AST.getAnnotationDeclarations();

                for (MyAnnotationDeclaration annotationDeclaration : annotationDeclarations) {
                    if (rightHasIntersection(annotationDeclaration.getLocation(), cci)) {
                        result.add(annotationDeclaration);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyAnnotationUsage> leftAnnotationUsages(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTLeft) {

        List<MyAnnotationUsage> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTLeft) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAnnotationUsage> annotationDeclarations = AST.getAnnotationUsages();

                for (MyAnnotationUsage annotationUsage : annotationDeclarations) {
                    if (leftHasIntersection(annotationUsage.getLocation(), cci)) {
                        result.add(annotationUsage);
                    }
                }
            }
        }

        return result;
    }

    public static List<MyAnnotationUsage> rightAnnotationUsages(String projectPath,
            ConflictingChunkInformation cci, List<ClassLanguageContructs> ASTRight) {

        List<MyAnnotationUsage> result = new ArrayList<>();

        String relativePath;

        if (cci.isRenamed() && cci.getRelativePathLeft() != null) {
            relativePath = cci.getRelativePathLeft();
        } else {
            relativePath = cci.getFilePath().replace(projectPath, "");
        }

        for (ClassLanguageContructs AST : ASTRight) {

            if (containsPath(AST.getPath(), relativePath)) {

                List<MyAnnotationUsage> annotationUsages = AST.getAnnotationUsages();

                for (MyAnnotationUsage annotationUsage : annotationUsages) {
                    if (rightHasIntersection(annotationUsage.getLocation(), cci)) {
                        result.add(annotationUsage);
                    }
                }
            }
        }

        return result;
    }

    //Treating dependency between a annotation declaration its usages
    public static boolean hasDependencyAnnotationDeclarationUsage(List<MyAnnotationDeclaration> annotationDeclarations, List<MyAnnotationUsage> annotationUsages) {

        for (MyAnnotationDeclaration annotationDeclaration : annotationDeclarations) {
            for (MyAnnotationUsage annotationUsage : annotationUsages) {
                if (sameAnnotation(annotationDeclaration, annotationUsage)) {
                    return true;
                }
            }

        }

        return false;
    }

    public static boolean sameAnnotation(MyAnnotationDeclaration annotationDeclaration, MyAnnotationUsage annotationUsage) {

        ITypeBinding annotationDeclarationBinding = annotationDeclaration.resolveTypeBinding();
        ITypeBinding annotationUsageBinding = annotationUsage.resolveTypeBinding();

        if (annotationDeclarationBinding != null && annotationUsageBinding != null && annotationDeclarationBinding.equals(annotationUsageBinding)) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean containsPath(String path, String relativePath) {
        String pathClean = cleanPath(path);
        String relativeClean = cleanPath(relativePath);

        return pathClean.contains(relativeClean);
    }

    public static String cleanPath(String path) {

        while (path.contains("/")) {
            path = path.replace("/", "");
        }

        while (path.contains("\\")) {
            path = path.replace("\\", "");
        }

        return path;
    }
}
