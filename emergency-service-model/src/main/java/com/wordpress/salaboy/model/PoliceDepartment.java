/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wordpress.salaboy.model;

/**
 *
 * @author salaboy
 */
public class PoliceDepartment implements EmergencyEntityBuilding{
    private int x;
    private int y;
    private String name;

    public PoliceDepartment(String name, int x, int y) {
        this.name = name;
        this.x = x;
        this.y = y;
        
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public String getName() {
        return name;
    }
    
    
    
}
