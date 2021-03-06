/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.uff.ic.kraken.extractdata;

import br.uff.ic.gems.resources.analises.merge.RevisionAnalyzer;
import br.uff.ic.gems.resources.data.ConflictingChunk;
import br.uff.ic.gems.resources.data.ConflictingFile;
import br.uff.ic.gems.resources.data.Revision;
import br.uff.ic.gems.resources.diff.translator.GitTranslator;
import br.uff.ic.gems.resources.operation.Operation;
import br.uff.ic.gems.resources.repositioning.Repositioning;
import br.uff.ic.gems.resources.utils.MergeStatusAnalizer;
import br.uff.ic.gems.resources.vcs.Git;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author gleiph
 */
public class PostponedResolutions {

    public static void main(String[] args) throws ParseException {

        int daysRange = 30;

        List<String> projects = new ArrayList<>();

        projects.add("/Users/gleiph/repositories/antlr4");
        projects.add("/Users/gleiph/repositories/lombok");
        projects.add("/Users/gleiph/repositories/mct");
        projects.add("/Users/gleiph/repositories/twitter4j");
        projects.add("/Users/gleiph/repositories/voldemort");

        for (String project : projects) {
            projectAnalysis(project, daysRange);
        }

    }

    public static void projectAnalysis(String repository, int daysRange) throws ParseException {

        //Cloning repository to suport the analysis
        String repoAux = "repoAux";
        String repoMergeResult = "repoMergeResult";
        File repoAuxFile = new File(repository + repoAux);
        File repoMergeResultFile = new File(repository + repoMergeResult);

        if (repoAuxFile.isDirectory()) {
            try {
                FileUtils.deleteDirectory(repoAuxFile);
            } catch (IOException ex) {
                Logger.getLogger(PostponedResolutions.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (repoMergeResultFile.isDirectory()) {
            try {
                FileUtils.deleteDirectory(repoMergeResultFile);
            } catch (IOException ex) {
                Logger.getLogger(PostponedResolutions.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        Git.clone(repository, repository, repoAuxFile.getAbsolutePath());
        Git.clone(repository, repository, repoMergeResultFile.getAbsolutePath());

        if (!repository.endsWith(File.separator)) {
            repository += File.separator;
        }

        System.out.println(repository);

        SimpleDateFormat formater = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss z");
        List<String> mergeRevisions = Git.getMergeRevisions(repository, true);

        System.out.println("Merges: " + mergeRevisions.size());

        int postponedConflictingchunks = 0;
        int postponedConflictingchunksTotal = 0;
        
        int filesCochanged = 0, mergesWithCochanges = 0, merges = 0;

        for (String mergeRevision : mergeRevisions) {

            filesCochanged = 0;
            postponedConflictingchunks = 0;
            
            List<String> parents = Git.getParents(repository, mergeRevision);

            if (parents.size() > 2) {
                continue;
            }

            Git.reset(repository);
            Git.clean(repository);
            Git.checkout(repository, parents.get(0));
            List<String> mergeOutput = Git.merge(repository, parents.get(1), false, false);

            Git.checkout(repoMergeResultFile.getAbsolutePath(), mergeRevision);

            if (MergeStatusAnalizer.isConflict(mergeOutput)) {

                merges++;

                List<String> conflictedFiles = Git.conflictedFiles(repository);

                //Getting conflicting chunks
                Revision currentRevision = RevisionAnalyzer.analyze(mergeRevision, repository);
                List<ConflictingFile> conflictingFiles = currentRevision.getConflictingFiles();

                //Converting to relative path the files
                for (String conflictedFile : conflictedFiles) {
                    if (conflictedFile.startsWith(repository)) {
                        conflictedFiles.set(conflictedFiles.indexOf(conflictedFile), conflictedFile.replaceFirst(repository, ""));
                    }
                }

                //Converting to relative path the conflicting files
                for (ConflictingFile conflictingFile : conflictingFiles) {
                    conflictingFile.setPath(conflictingFile.getPath().replace(repository, ""));
                }

                System.out.println("===================================================================");
                System.out.println("Merge " + mergeRevision);
                System.out.println("===================================================================");

                String date = Git.getDateISO(repository, mergeRevision);
                Date mergeDate = formater.parse(date);

                Calendar aux = Calendar.getInstance();
                aux.setTime(mergeDate);
                aux.add(Calendar.DATE, daysRange);
                Date endWindow = aux.getTime();

                //Getting commits in the range of days
                List<String> commitsRange = Git.logByDays(repository, mergeDate, endWindow);

                commitsRange = commitsFreeMerges(repository, commitsRange, mergeRevisions);

                for (ConflictingFile conflictingFile : conflictingFiles) {
                    if (conflictingFile.getPath().toLowerCase().endsWith(".java")) {

                        //Conflicting chunks
                        for (ConflictingChunk conflictingChunk : conflictingFile.getConflictingChunks()) {
                            boolean printed = false;

                            for (String commit : commitsRange) {
                                List<String> changedFiles = Git.getChangedFiles(repository, commit);

                                if (!changedFiles.contains(conflictingFile.getPath())) {
                                    continue;
                                }

                                List<String> chunkEvolution = new ArrayList<>();
                                boolean changed = changed(conflictingChunk, commit, repository, repoAuxFile.getAbsolutePath(), conflictingFile.getPath(), chunkEvolution);

                                if (changed) {
                                    try {
                                        Repositioning repositioning = new Repositioning(repository);
                                        filesCochanged += 1;

                                        File initialFile = new File(repository, conflictingFile.getPath());
                                        List<String> initialFileList = FileUtils.readLines(initialFile);

                                        File finalFile = new File(repoMergeResultFile.getAbsoluteFile(), conflictingFile.getPath());

                                        int beginLine = conflictingChunk.getBeginLine() - 1;
                                        if (beginLine < 0) {
                                            beginLine = 0;
                                        }

                                        int endLine = conflictingChunk.getEndLine() + 1;
                                        if (endLine >= initialFileList.size()) {
                                            endLine = initialFileList.size() - 1;
                                        }

                                        int begin = repositioning.repositioning(initialFile.getAbsolutePath(), finalFile.getAbsolutePath(), beginLine + 1);
                                        int end = repositioning.repositioning(initialFile.getAbsolutePath(), finalFile.getAbsolutePath(), endLine + 1);

                                        List<String> finalLines = FileUtils.readLines(finalFile);

                                        if (begin == -1 && end == -1) {
                                            System.out.println("Treat");
                                        } else if (begin == -1) {
                                            begin = end - (endLine - beginLine);
                                            if (begin < 0) {
                                                begin = 0;
                                            }

                                        } else if (end == -1) {
                                            end = begin + (endLine - beginLine);
                                            if (end >= finalLines.size()) {
                                                end = finalLines.size() - 1;
                                            }
                                        }

                                        if (begin < end) {

                                            if (!printed) {
                                                System.out.println("");
                                                System.out.println("--------------------------------------------------------------------");
                                                System.out.println("Conflict");
                                                System.out.println("--------------------------------------------------------------------");
                                                System.out.println("");
                                                List<String> subList = initialFileList.subList(beginLine, endLine);
                                                for (String subList1 : subList) {
                                                    System.out.println(subList1);
                                                }

                                                System.out.println("");
                                                System.out.println("--------------------------------------------------------------------");
                                                System.out.println("Merge result: ");
                                                System.out.println("--------------------------------------------------------------------");
                                                System.out.println("");

                                                for (String line : finalLines.subList(begin, end)) {
                                                    System.out.println(line);
                                                }

                                                postponedConflictingchunks++;
                                                printed = true;
                                            }

                                            System.out.println("");
                                            System.out.println("--------------------------------------------------------------------");
                                            System.out.println("Resolution " + commit);
                                            System.out.println("--------------------------------------------------------------------");
                                            System.out.println("");
                                            for (String line : chunkEvolution) {
                                                System.out.println(line);
                                            }

                                        }
                                    } catch (IOException ex) {
                                        Logger.getLogger(PostponedResolutions.class.getName()).log(Level.SEVERE, null, ex);
                                    }

                                }
                            }
                        }

                    }
                }

                if (filesCochanged > 0) {
                    mergesWithCochanges++;
                }

                System.out.println("Files co-changed: " + filesCochanged);
                postponedConflictingchunksTotal += postponedConflictingchunks;
                System.out.println("Postponed cc = " + postponedConflictingchunks);

            }
        }
        System.out.println("Merge with co changes: " + mergesWithCochanges + "(" + merges + ")");
        System.out.println("postponedConflictingchunksTotal: "  + postponedConflictingchunksTotal);
    }

    private static boolean hasWayWithoutMerge(String repositoryPath, String shaSource, String shaTarget,
            List<String> commits, List<String> merges) {

        List<String> parents = Git.getParents(repositoryPath, shaTarget);

        for (String parent : parents) {
            if (merges.contains(parent) && !parent.equals(shaSource)) {
            } else if (parent.equals(shaSource)) {
                return true;
            } else if (commits.contains(parent)) {
                boolean hasWay = hasWayWithoutMerge(repositoryPath, shaSource, parent, commits, merges);
                if (hasWay) {
                    return true;
                }
            }

        }

        return false;
    }

    //Filter the commits that has a way to the source merge and does not have merges in the way
    public static List<String> commitsFreeMerges(String repositoryPath, List<String> commits, List<String> merges) {
        List<String> result = new ArrayList<>();

        if (commits == null || commits.isEmpty()) {
            return null;
        }

        String sourceCommit = commits.get(0);

        for (int i = 1; i < commits.size(); i++) {
            String currentCommit = commits.get(i);

            boolean hasWay = hasWayWithoutMerge(repositoryPath, sourceCommit, currentCommit, commits, merges);

            if (hasWay && !merges.contains(currentCommit)) {
                result.add(currentCommit);
            }

        }

        return result;
    }

    public static boolean changed(ConflictingChunk conflictingChunk, String commit,
            String originalRepositoryPath, String auxRepositoryPath, String fileRelativePath, List<String> chunkEvolution) {

        Repositioning repositioning = new Repositioning(originalRepositoryPath);

        Git.reset(auxRepositoryPath);
        Git.clean(auxRepositoryPath);
        Git.checkout(auxRepositoryPath, commit);

        //Get the changes made in the file
        String initialFile, finalFile;

        if (!originalRepositoryPath.endsWith(File.separator)) {
            originalRepositoryPath += File.separator;
        }

        if (!auxRepositoryPath.endsWith(File.separator)) {
            auxRepositoryPath += File.separator;
        }

        initialFile = originalRepositoryPath + fileRelativePath;
        finalFile = auxRepositoryPath + fileRelativePath;

        List<String> diffLog = Git.diffLog(auxRepositoryPath, fileRelativePath);

        String diffOutput = "";
        for (String line : diffLog) {
            diffOutput += line + "\n";
        }

        GitTranslator gitTranslator = new GitTranslator();
        List<Operation> changes = gitTranslator.translateDelta(diffOutput);

        try {
            //Repositioning the conflicting chunk (cc) to the current file
            List<String> initialFileList = FileUtils.readLines(new File(initialFile));

            int beginLine = conflictingChunk.getBeginLine() - 1;
            if (beginLine < 0) {
                beginLine = 0;
            }

            int endLine = conflictingChunk.getEndLine() + 1;
            if (endLine >= initialFileList.size()) {
                endLine = initialFileList.size() - 1;
            }

            int begin = repositioning.repositioning(initialFile, finalFile, beginLine + 1);
            int end = repositioning.repositioning(initialFile, finalFile, endLine + 1);

            List<String> finalFileList = FileUtils.readLines(new File(finalFile));
            //Identify if there are changes inside the cc

            if (begin == -1 && end == -1) {
                System.out.println("Treat");
                //Look around the area 
                int beginAux = beginLine + 1;
                while (begin == -1 && beginAux > 0) {
                    begin = repositioning.repositioning(initialFile, finalFile, --beginAux);
                }

                if (begin < 0) {
                    begin = 0;
                }

                int endAux = endLine + 1;

                while (end == -1 && endAux < initialFileList.size()) {
                    end = repositioning.repositioning(initialFile, finalFile, ++endAux);
                }

                if (end > finalFileList.size()) {
                    end = finalFileList.size() - 1;
                }

                List<String> subList = finalFileList.subList((begin - 1 <= 0) ? 0 : begin - 1, end - 1);
                for (String line : subList) {
                    chunkEvolution.add(line);
                }

                return true;
            } else if (begin == -1) {
                begin = end - (endLine - beginLine);
                if (begin < 0) {
                    begin = 0;
                }

                List<String> subList = finalFileList.subList((begin - 1 <= 0) ? 0 : begin - 1, end - 1);
                for (String line : subList) {
                    chunkEvolution.add(line);
                }

                return true;
            } else if (end == -1) {
                end = begin + (endLine - beginLine);
                if (end >= finalFileList.size()) {
                    end = finalFileList.size() - 1;
                }

                List<String> subList = finalFileList.subList((begin - 1 <= 0) ? 0 : begin - 1, end - 1);
                for (String line : subList) {
                    chunkEvolution.add(line);
                }

                return true;
            }

            for (Operation change : changes) {
                if (change.getLine() > begin && change.getLine() < end) {

                    List<String> subList = finalFileList.subList((begin - 1 <= 0) ? 0 : begin - 1, end - 1);
                    for (String line : subList) {
                        chunkEvolution.add(line);
                    }

                    return true;
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(PostponedResolutions.class.getName()).log(Level.SEVERE, null, ex);
        }

        return false;
    }

}
