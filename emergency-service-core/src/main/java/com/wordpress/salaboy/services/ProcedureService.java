/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wordpress.salaboy.services;

import java.util.Map;

/**
 *
 * @author salaboy
 */
public interface ProcedureService {
    public String getId();
    public void setId(String id);
    public void configure(String emergencyId, Map<String, Object> parameters);
}
