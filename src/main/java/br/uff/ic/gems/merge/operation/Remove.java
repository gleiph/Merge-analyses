/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.uff.ic.gems.merge.operation;

/**
 *
 * @author gleiph
 */
public class Remove extends Operation implements OperationInterface{

    public Remove() {
        super();
        super.setType(OperationType.REMOVE);
    }
    
}
