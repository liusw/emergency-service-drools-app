/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wordpress.salaboy;

import com.wordpress.salaboy.messaging.MessageConsumerWorker;
import com.wordpress.salaboy.messaging.MessageConsumerWorkerHandler;
import com.wordpress.salaboy.messaging.MessageServerSingleton;
import com.wordpress.salaboy.model.Emergency;
import com.wordpress.salaboy.model.messages.EmergencyDetailsMessage;
import com.wordpress.salaboy.model.messages.IncomingCallMessage;
import com.wordpress.salaboy.model.messages.SelectedProcedureMessage;
import com.wordpress.salaboy.model.serviceclient.DistributedPeristenceServerService;

import com.wordpress.salaboy.services.HumanTaskServerService;
import com.wordpress.salaboy.model.serviceclient.InMemoryPersistenceService;
import com.wordpress.salaboy.services.IncomingCallsMGMTService;
import com.wordpress.salaboy.services.ProceduresMGMTService;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.drools.SystemEventListenerFactory;
import org.drools.grid.ConnectionFactoryService;
import org.drools.grid.Grid;
import org.drools.grid.GridConnection;
import org.drools.grid.GridNode;
import org.drools.grid.GridServiceDescription;
import org.drools.grid.SocketService;
import org.drools.grid.conf.GridPeerServiceConfiguration;
import org.drools.grid.conf.impl.GridPeerConfiguration;
import org.drools.grid.impl.GridImpl;
import org.drools.grid.impl.MultiplexSocketServerImpl;
import org.drools.grid.io.impl.MultiplexSocketServiceCongifuration;
import org.drools.grid.remote.mina.MinaAcceptorFactoryService;
import org.drools.grid.service.directory.WhitePages;
import org.drools.grid.service.directory.impl.CoreServicesLookupConfiguration;
import org.drools.grid.service.directory.impl.WhitePagesLocalConfiguration;
import org.drools.grid.timer.impl.CoreServicesSchedulerConfiguration;

/**
 *
 * @author esteban
 */
public class CoreServer {

    protected Map<String, GridServiceDescription> coreServicesMap = new HashMap<String, GridServiceDescription>();
    protected Grid grid1;
    protected GridNode remoteN1;
    
    
    public static void main(String[] args) throws Exception {
        new CoreServer().startServer();
    }

    public void startServer() throws Exception {
        MessageServerSingleton.getInstance().start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(){

            @Override
            public void run() {
                try {
                    System.out.println("Stopping Core Server ... ");
                    MessageServerSingleton.getInstance().stop();
                    remoteN1.dispose();
                    grid1.get(SocketService.class).close();
                    HumanTaskServerService.getInstance().stopTaskServer();
                    System.out.println("Core Server Stopped! ");
                } catch (Exception ex) {
                    Logger.getLogger(CoreServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        //Starting Grid View
        createRemoteNode();
        //Starting Human Task Server
        HumanTaskServerService.getInstance().initTaskServer();
        
        //Start Workers
        startQueuesWorkers();
        
        
    }

    private void startQueuesWorkers() {
        try {
            
            //Phone Calls Worker
            MessageConsumerWorker phoneCallsWorker = new MessageConsumerWorker("IncomingCall",new MessageConsumerWorkerHandler<IncomingCallMessage>() {
                @Override
                public void handleMessage(IncomingCallMessage incomingCallMessage) {
                    IncomingCallsMGMTService.getInstance().newPhoneCall(incomingCallMessage.getCall());
                }
            }); 
            
             //Heart Attack Procedure Selected Worker
            MessageConsumerWorker selectedProcedureWorker = new MessageConsumerWorker("selectedProcedure",new MessageConsumerWorkerHandler<SelectedProcedureMessage>() {
                @Override
                public void handleMessage(SelectedProcedureMessage selectedProcedureMessage) {
                    ProceduresMGMTService.getInstance()
                                .newRequestedProcedure(selectedProcedureMessage.getCallId(), 
                                                    selectedProcedureMessage.getProcedureName(),
                                                    selectedProcedureMessage.getParameters());
                }
            }); 
            
              //Emergency Details Persistence Selected Worker
            MessageConsumerWorker emergencyDetailsPersistenceWorker = new MessageConsumerWorker("emergencyDetails",new MessageConsumerWorkerHandler<EmergencyDetailsMessage>() {
                @Override
                public void handleMessage(EmergencyDetailsMessage emergencyDetailsMessage) {
                    DistributedPeristenceServerService.getInstance()
                                .storeEmergency(emergencyDetailsMessage.getEmergency());
                }
            }); 
            emergencyDetailsPersistenceWorker.start();
            selectedProcedureWorker.start();
            phoneCallsWorker.start();
            
            phoneCallsWorker.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(CoreServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
     protected void createRemoteNode() {
        grid1 = new GridImpl(new HashMap<String, Object>());
        configureGrid1(grid1,
                8000,
                null);

        Grid grid2 = new GridImpl(new HashMap<String, Object>());
        configureGrid1(grid2,
                -1,
                grid1.get(WhitePages.class));

        GridNode n1 = grid1.createGridNode("n1");
        grid1.get(SocketService.class).addService("n1", 8000, n1);

        GridServiceDescription<GridNode> n1Gsd = grid2.get(WhitePages.class).lookup("n1");
        GridConnection<GridNode> conn = grid2.get(ConnectionFactoryService.class).createConnection(n1Gsd);
        remoteN1 = conn.connect();

    }

    private void configureGrid1(Grid grid,
            int port,
            WhitePages wp) {

        //Local Grid Configuration, for our client
        GridPeerConfiguration conf = new GridPeerConfiguration();

        //Configuring the Core Services White Pages
        GridPeerServiceConfiguration coreSeviceWPConf = new CoreServicesLookupConfiguration(coreServicesMap);
        conf.addConfiguration(coreSeviceWPConf);

        //Configuring the Core Services Scheduler
        GridPeerServiceConfiguration coreSeviceSchedulerConf = new CoreServicesSchedulerConfiguration();
        conf.addConfiguration(coreSeviceSchedulerConf);

        //Configuring the WhitePages 
        WhitePagesLocalConfiguration wplConf = new WhitePagesLocalConfiguration();
        wplConf.setWhitePages(wp);
        conf.addConfiguration(wplConf);

        if (port >= 0) {
            //Configuring the SocketService
            MultiplexSocketServiceCongifuration socketConf = new MultiplexSocketServiceCongifuration(new MultiplexSocketServerImpl("127.0.0.1",
                    new MinaAcceptorFactoryService(),
                    SystemEventListenerFactory.getSystemEventListener(),
                    grid));
            socketConf.addService(WhitePages.class.getName(), wplConf.getWhitePages(), port);

            conf.addConfiguration(socketConf);
        }
        conf.configure(grid);

    }

}
