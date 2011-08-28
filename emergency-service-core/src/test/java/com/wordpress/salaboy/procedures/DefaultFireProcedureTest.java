/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wordpress.salaboy.procedures;


import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.grid.SocketService;
import org.example.ws_ht.api.TTaskAbstract;
import org.example.ws_ht.api.wsdl.IllegalAccessFault;
import org.example.ws_ht.api.wsdl.IllegalArgumentFault;
import org.example.ws_ht.api.wsdl.IllegalStateFault;
import org.hornetq.api.core.HornetQException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.wordpress.salaboy.api.HumanTaskService;
import com.wordpress.salaboy.api.HumanTaskServiceFactory;
import com.wordpress.salaboy.conf.HumanTaskServiceConfiguration;
import com.wordpress.salaboy.grid.GridBaseTest;
import com.wordpress.salaboy.messaging.MessageServerSingleton;
import com.wordpress.salaboy.model.Call;
import com.wordpress.salaboy.model.Emergency;
import com.wordpress.salaboy.model.FireTruck;
import com.wordpress.salaboy.model.Hospital;
import com.wordpress.salaboy.model.Location;
import com.wordpress.salaboy.model.messages.VehicleHitsEmergencyMessage;
import com.wordpress.salaboy.model.serviceclient.DistributedPeristenceServerService;
import com.wordpress.salaboy.services.HumanTaskServerService;
import com.wordpress.salaboy.services.ProceduresMGMTService;
import com.wordpress.salaboy.smarttasks.jbpm5wrapper.conf.JBPM5HornetQHumanTaskClientConfiguration;
import com.wordpress.salaboy.tracking.ContextTrackingServiceImpl;
import junit.framework.Assert;
import org.junit.Test;

/**
 *
 * @author esteban
 */
public class DefaultFireProcedureTest extends GridBaseTest {

    private HumanTaskService humanTaskServiceClient;
    

    public DefaultFireProcedureTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        HumanTaskServerService.getInstance().initTaskServer();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {

        HumanTaskServerService.getInstance().stopTaskServer();
    }

    Emergency emergency = null;
    FireTruck fireTruck = null;
    Call call = null;
    
    @Before
    public void setUp() throws Exception {
        emergency = new Emergency();
        String emergencyId = ContextTrackingServiceImpl.getInstance().newEmergency();
        emergency.setId(emergencyId);
        
        fireTruck = new FireTruck("FireTruck 1");
                
        call = new Call(1,2,new Date());

        String callId = ContextTrackingServiceImpl.getInstance().newCall();
        call.setId(callId);
        emergency.setCall(call);
        emergency.setLocation(new Location(1,2));
        emergency.setType(Emergency.EmergencyType.FIRE);
        emergency.setNroOfPeople(1);
        
        
        DistributedPeristenceServerService.getInstance().storeHospital(new Hospital("My Hospital", 12, 1));
        DistributedPeristenceServerService.getInstance().storeEmergency(emergency);
        DistributedPeristenceServerService.getInstance().storeVehicle(fireTruck);
        MessageServerSingleton.getInstance().start();

        this.coreServicesMap = new HashMap();
        createRemoteNode();

        HumanTaskServiceConfiguration taskClientConf = new HumanTaskServiceConfiguration();


        taskClientConf.addHumanTaskClientConfiguration("jBPM5-HT-Client", new JBPM5HornetQHumanTaskClientConfiguration("127.0.0.1", 5446));

        humanTaskServiceClient = HumanTaskServiceFactory.newHumanTaskService(taskClientConf);
        humanTaskServiceClient.initializeService();

    }

    @After
    public void tearDown() throws Exception {
        MessageServerSingleton.getInstance().stop();
        if (remoteN1 != null) {
            remoteN1.dispose();
        }
        if (grid1 != null) {
            grid1.get(SocketService.class).close();
        }
    }

    @Test
    public void defaultHeartAttackSimpleTest() throws HornetQException, InterruptedException, IOException, ClassNotFoundException, IllegalArgumentFault, IllegalStateFault, IllegalAccessFault {


        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("call", call);
        parameters.put("emergency", emergency);
        parameters.put("vehicle", fireTruck);

        ProceduresMGMTService.getInstance().newRequestedProcedure(emergency.getId(), "DefaultFireProcedure", parameters);

        //The fire truck doesn't reach the emergency yet. No task for 
        //the firefighter.
        humanTaskServiceClient.setAuthorizedEntityId("firefighter");
        List<TTaskAbstract> taskAbstracts = humanTaskServiceClient.getMyTaskAbstracts("", "firefighter", "", null, "", "", "", 0, 0);
        
        Assert.assertTrue(taskAbstracts.isEmpty());
        
        //Now the fire truck arrives to the emergency
        ProceduresMGMTService.getInstance().notifyProcedures(new VehicleHitsEmergencyMessage(fireTruck.getId(), emergency.getId(), new Date()));
        
        Thread.sleep(2000);
        
        //A new task for the firefighter should be there now
        taskAbstracts = humanTaskServiceClient.getMyTaskAbstracts("", "firefighter", "", null, "", "", "", 0, 0);
        
        Assert.assertEquals(1,taskAbstracts.size());
        
        TTaskAbstract firefighterTask = taskAbstracts.get(0);
        
        //The firefighter completes the task
        Map<String, Object> info = new HashMap<String, Object>();
        info.put("emergency.priority", 1);
        humanTaskServiceClient.start(firefighterTask.getId());
        humanTaskServiceClient.complete(firefighterTask.getId(), info);
        
        Thread.sleep(5000);
        
        //TODO: validate that the process has finished
        
        

    }
}