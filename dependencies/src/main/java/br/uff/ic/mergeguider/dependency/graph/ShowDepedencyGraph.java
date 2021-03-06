/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.uff.ic.mergeguider.dependency.graph;

import br.uff.ic.gems.resources.vcs.Git;
import java.io.IOException;
import java.util.List;

/**
 *
 * @author gleiph
 */
public class ShowDepedencyGraph {

    public static void main(String[] args) throws IOException {
//        ShowDependencies show = new ShowDependencies();
//        show.setProjectPath("/Users/gleiph/repositories/voldemort");
//        show.setSHALeft("aee112d9ef0ed960c7bc9955d7e85e6ed6ac91a0");
//        show.setSHARight("fd5dbeb5113ffed51cf1836ac78b129a4bea4cb6");
//        show.setSandbox("/Users/gleiph/repositories/icse/");

        ShowDependencies show = new ShowDependencies();
//        show.setProjectPath("/Users/gleiph/repositories/wro4j");
//        show.setSHALeft("6de49bc");
//        show.setSHARight("e8b80e4");
//        show.setSandbox("/Users/gleiph/repositories/icse");
//        show.setProjectPath("C:\\Users\\gleiph\\repositorios\\spring-data-neo4j");
//        show.setSHALeft("3ba54fd4");
//        show.setSHARight("4a8f404c");
//        show.setSandbox("C:\\Users\\gleiph\\repositorios\\teste1");

        String repositoryPath = "/Users/gleiph/repositories/ATK";
        String revision = "5f510840636176b75a5339188c1b3f342f76584c";
        show.setProjectPath(repositoryPath);

        Git git = new Git(repositoryPath);
        List<String> parents = git.getParents(repositoryPath, revision);

        show.setSHALeft(parents.get(0));
        show.setSHARight(parents.get(1));
        show.setSandbox("/Users/gleiph/repositories/icse");

        show.show();
    }
}
