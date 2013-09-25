/*
 * Telsis Limited jOCP library
 *
 * Copyright (C) Telsis Ltd. 2013.
 *
 * This Program is free software: you can copy, redistribute and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License or (at your option) any later version.
 *
 * If you modify this Program you must mark it as changed by you and give a relevant date.
 *
 * This Program is published in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * receive a copy of the GNU General Public License along with this program. If not,
 * see <http//www.gnu.org/licenses/>.
 *
 * In making commercial use of this Program you indemnify Telsis Limited and all of its related
 * Companies for any contractual assumptions of liability that may be imposed on Telsis Limited
 * or any of its related Companies.
 *
 */
package com.telsis.jocp.sampleApp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.telsis.jocp.OCPLink;
import com.telsis.jocp.OCPMessage;
import com.telsis.jocp.OCPMessageHandler;
import com.telsis.jocp.OCPSystemManager;
import com.telsis.jocp.OCPTelno;
import com.telsis.jocp.OCPUtil;
import com.telsis.jocp.messages.DeliverTo;
import com.telsis.jocp.messages.DeliverToResult;
import com.telsis.jocp.messages.InitialDP;
import com.telsis.jocp.messages.InitialDPResponse;
import com.telsis.jocp.messages.TelsisHandler;
import com.telsis.jocp.messages.TelsisHandlerResult;
import com.telsis.jocp.messages.TelsisHandlerWithParty;
import com.telsis.jocp.messages.telsishandler.MakeINAPffCallPayload;
import com.telsis.jocp.messages.telsishandler.TelsisHandlerNumber;
import com.telsis.jocp.messages.telsishandler.UpdateMatchedDigitsPayload;
import com.telsis.jutils.signalling.GenericTelno;
import com.telsis.jutils.signalling.SignallingUtil;
import com.telsis.jutils.signalling.TelnoType;

/**
 * Basic implementation of an application that sends and receives OCP events.
 * <h3>Message flow</h3>
 * <table>
 * <thead>
 * <tr><th>OCPApp</th><th> -&gt; / &lt;- </th><th>SCP</th></tr>
 * </thead>
 * <tbody>
 * <tr><td>Connects</td><td> -&gt;</td><td></td></tr>
 * <tr><td>Initial DP</td><td> -&gt;</td><td></td></tr>
 * <tr><td></td><td> &lt;-</td><td>DeliverTo</td></tr>
 * <tr><td>DeliverToResult</td><td> -&gt;</td><td></td></tr>
 * </tbody>
 * <tfoot>
 * <tr><th>OCPApp</th><th> -&gt; / &lt;- </th><th>SCP</th></tr>
 * </tfoot>
 * </table>
 * @author Telsis Ltd.
 * @version 1.0.0
 *
 */
public final class BasicOCPApp {
    /** Time to wait to allow the link to connect. */
    private static final long LINK_WAIT_TIME = 1000;
    /** Destination (FIN). **/
    private static byte[] fin = new byte [] {};  // set up from command line
    /** Source (CLI). **/
    private static final byte[] CLI = new byte []{
        4, 9, 2, 2, 1, 9, 7, 6, 0, 0, 0, 1};
    /** Local IP to bind on. */
    private static final String LOCAL_IP = "0.0.0.0";
    /** Remote IP to connect to. */
    private static String remote_ip0 = "";  // set up from command line
    private static String remote_ip1 = "";  // set up from command line
    /**
     * The amount of time to wait before checking again to see whether the call has finished.
     */
    private static final long WAIT_TIME = 1000;
    /** The current link for transmitting OCP messages. */
    private static OCPLink link;
    /** An array of failed links. */
    private static ArrayList<OCPLink> failedLinks;
    /** OCP system manager. */
    private static OCPSystemManager sysManager = null;
    /** The OCP message dispatcher. */
    private static OCPMessageHandler handler = null;
    /** The local task ID - unique to a call. */
    private static int localTID = 1;
    /** The remote task ID. */
    private static int remoteTID = -1;
    /** Logging instance.     */
    private static Logger logger = Logger.getLogger(BasicOCPApp.class);
    /**
     * Whether we have sent a result message yet.
     */
    private static volatile boolean sentResult = false;
    /**
     * Entry point.
     * @param args Command line arguments
     */
    public static void main(final String[] args) {
        
        if (args.length < 3) {
            System.out.println("Usage:");
            System.out.println("BasicOCPApp <2280 A IP address> <2280 B IP address> <dialled number> [verbose]");
            System.exit(1);  
        }
        else {
            remote_ip0 = args[0]; 
            remote_ip1 = args[1];
            
            // turn the dialled number into a byte array
            // there must be an easier way than this!!!
            ByteBuffer buf = ByteBuffer.allocate(args[2].length());
            char [] fin_char = args[2].toCharArray();
            for( char c : fin_char)
            {
                buf.put((byte) Character.getNumericValue(c));
            }
            if(buf.hasArray()) {
                fin = buf.array(); 
            }
            if ((args.length > 3) && (args[3].equals("verbose"))) {
                logger.setLevel(Level.ALL); 
                System.out.println("Verbose mode");
            }
            else {
                logger.setLevel(Level.FATAL);
            }
        }
        System.out.printf("to %s and %s for %s\n", remote_ip0, remote_ip1, Arrays.toString(fin));
        setup();
        //Wait a bit so the link has time to come up
        try {
            Thread.sleep(LINK_WAIT_TIME);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        boolean haveLink = getLink();
        if (haveLink) {
            OCPMessage message = buildInitialDP();
            sendOCPMessage(message);
        } else {
            logger.fatal("No link was available");
            System.exit(1);
        }
        while (!sentResult) {
            try {
                Thread.sleep(WAIT_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        shutdown();
        System.exit(0);
    }
    /**
     * Retrieves a link and registers the task ID handler.
     * @return <code>true</code> if there's an available link
     */
    private static boolean getLink() {
        link = sysManager.getLink(failedLinks);
        if (link != null) {
            link.registerTidHandler(handler, localTID);
            return true;
        }
        return false;
    }

    /**
     * Perform set-up of the OCP link and logging.
     */
    private static void setup() {
        BasicConfigurator.configure();
        // System.out.println("Turning logging off");
        // logger.setLevel(Level.FATAL);  // change level to INFO to see more output
        Properties properties = new Properties();
        properties.setProperty("ocpSystemUnitName", "BasicOCPApp");
        properties.setProperty("ocpSystemNumLinks", "2");
        properties.setProperty("ocpLink0RemoteAddress", remote_ip0);
        properties.setProperty("ocpLink0LocalAddress", LOCAL_IP);
        properties.setProperty("ocpLink1RemoteAddress", remote_ip1);
        properties.setProperty("ocpLink1LocalAddress", LOCAL_IP);
        properties.setProperty("ocpSystemLoggingLevel", "FATAL");
        sysManager = new OCPSystemManager(properties);
        sysManager.connect();
        handler = new OCPMessageHandler() {
            @Override
            public void queueMessage(final OCPMessage message, final OCPLink callingLink) {
                logger.info("Recieved OCP message");
                handleMessage(message);
            }
        };
        sysManager.registerManagementTidHandler(handler);
    }

    private static void shutdown() {
        sysManager.disconnect();
        sysManager.deregisterManagementTidHandler();
    }

    /**
     * Handle a message received from the link.
     * @param message Message received.
     */
    private static void handleMessage(final OCPMessage message) {
        logger.info("Handling OCP message: " + message.getMessageType());
        switch(message.getMessageType()) {
            case INITIAL_DP_RESPONSE:
                handleInitialDPResponse((InitialDPResponse) message);
                break;
            case DELIVER_TO:
                doDeliverTo((DeliverTo) message);
                break;
                
            case TELSIS_HANDLER:
                doTelsisHandler((TelsisHandler) message);
                break;
            
            case TELSIS_HANDLER_WITH_PARTY:    
                doTelsisHandlerWithParty((TelsisHandlerWithParty) message);
                break;
                
            case INAP_CONTINUE:
                System.out.println("No service defined for this number - INAP continue sent");
                sentResult = true;      // the call is over!
                break;
                
            default:
                logger.warn("Unhandled message type");
                logger.info("Type:" + message.getMessageType());
                logger.info("Orig:" + message.getOrigTID());
                logger.info("Dest:" + message.getDestTID());
        }
    }
    /** Handle the response to the initial DP. */
    private static void handleInitialDPResponse(
            final InitialDPResponse iDPresponse) {
        remoteTID = iDPresponse.getOrigTID();
    }

    /**
     * Handle a Telsis handler message.
     * @param doTelsisHandler Telsis Handler request
     */
    private static void doTelsisHandler(final TelsisHandler telsisHandler) {
        remoteTID = telsisHandler.getOrigTID();
        if(telsisHandler.getHandlerNumber() == TelsisHandlerNumber.UPDATE_MATCHED_DIGITS)
        {
            // just send a result so that we get the Deliver To
            TelsisHandlerResult returnMessage = new TelsisHandlerResult();
            UpdateMatchedDigitsPayload payload = new UpdateMatchedDigitsPayload();
            payload.setMatchedDigits(1);
            returnMessage.setPayload(payload);
            sendOCPMessage(returnMessage);
        }
    }
    
    /**
     * Handle a Telsis handler with party message.
     * @param doTelsisHandler Telsis Handler request
     */
    private static void doTelsisHandlerWithParty(final TelsisHandlerWithParty telsisHandler) {
        remoteTID = telsisHandler.getOrigTID();
        if(telsisHandler.getHandlerNumber() == TelsisHandlerNumber.MAKE_INAP_FF_CALL)
        {
            MakeINAPffCallPayload payload = (MakeINAPffCallPayload) telsisHandler.getPayload();
            System.out.println("INAP Connect to " + payload.getOutdialNo());
            // Now send a result to stop the map
            TelsisHandlerResult returnMessage = new TelsisHandlerResult();
            sendOCPMessage(returnMessage);
            // and set the flag to say we are done
            sentResult = true;
        }
    }
    
    
    /**
     * Handle a deliver to message.
     * @param deliverTo Deliver To request
     */
    private static void doDeliverTo(final DeliverTo deliverTo) {
        remoteTID = deliverTo.getOrigTID();
        //Respond to the deliver to message
        DeliverToResult returnMessage = new DeliverToResult();
        returnMessage.setFlags(DeliverToResult.FLAG_OUTDIAL_SUCCEEDED);
        returnMessage.setTime(SignallingUtil.getOceanTime());
        returnMessage.setZipNumber(deliverTo.getZipNumber());
        sendOCPMessage(returnMessage);
        sentResult = true;
        System.out.print("Call Delivered To - " );
        for (byte digit :deliverTo.getOutdialTelno().getUnpackedDigits() ) {
            System.out.print(digit);
            
        }
    }
    
    /**
     * Build an initial DP (detection point) message to the OCP link.
     * @return InitialDP message
     */
    private static InitialDP buildInitialDP() {
        InitialDP message = new InitialDP();
        GenericTelno gFIN = new GenericTelno(TelnoType.INTERNATIONAL, fin);
        GenericTelno gCLI = new GenericTelno(TelnoType.INTERNATIONAL, CLI);
        OCPTelno ocpFIN = OCPUtil.convertGenericTelnoToOCPTelno(gFIN);
        OCPTelno ocpCLI = OCPUtil.convertGenericTelnoToOCPTelno(gCLI);
        message.setOrigLegID((short) 1);
        message.setCPC(InitialDP.CPC_UNUSED);
        message.setFINTypePlan(ocpFIN.getTypePlan());
        message.setFIN(ocpFIN.getTelno());

        message.setCLI(ocpCLI.getTelno());
        message.setCLITypePlan(ocpFIN.getTypePlan());
        message.setCLIPresScreen((byte) (SignallingUtil.Q931_CLI_NETWORK
                | SignallingUtil.Q931_CLI_PRESENTATION_ALLOWED));
        message.setOceanTime(SignallingUtil.getOceanTime());
        return message;
    }

    /**
     * Send a OCP message over the link.
     * @param message Message to send
     */
    private static void sendOCPMessage(final OCPMessage message) {
        logger.info("Sending OCP message: " + message.getMessageType());
        if (link != null) {
            message.setOrigTID(localTID);
            message.setDestTID(remoteTID);
            link.queueMessage(message, null);
        } else {
            logger.fatal("No link was available");
            System.exit(1);
        }
    }

    /**
     * Default constructor.
     */
    private BasicOCPApp() {
    }
}
