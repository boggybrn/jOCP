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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import com.telsis.jocp.messages.DonePlay;
import com.telsis.jocp.messages.InitialDP;
import com.telsis.jocp.messages.InitialDPResponse;
import com.telsis.jocp.messages.InitialDPServiceKey;
import com.telsis.jocp.messages.PlayFile;
import com.telsis.jocp.messages.SetCDRExtendedFieldData;
import com.telsis.jocp.messages.SetCDRExtendedFieldDataResult;
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
    private static String local_ip = "0.0.0.0";    // set up from command line
    /** Remote IP to connect to. */
    private static String remote_ip0 = "127.0.0.1";  // set up from command line
    private static String remote_ip1 = "127.0.0.1";  // set up from command line
    private static int serviceKey = 1000;   // for use in initialDP - to trigger a particular map
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
     * Whether we have completed the test yet.
     */
    private static volatile boolean testDone = false;
    private static volatile boolean skipAnswer = true;
    
    /**
     * Entry point.
     * @param args Command line arguments
     */
    public static void main(final String[] args) {
        CommandLineParser parser = new GnuParser();
        Options options = new Options();
        options.addOption("a", "calling", true, "calling number (4922116804109)");
        options.addOption("b", "called", true, "called number (4922116804109)");
        options.getOption("b").setRequired(true);
        options.addOption("c", "client", true, "client IP Address (default is '0.0.0.0' ie. any)");
        options.addOption("k", "service-key", true, "service-key defaults to 1000");
        options.addOption("m", "master", true, "master server IP address");
        options.getOption("m").setRequired(true);
        options.addOption("s", "slave", true, "slave server IP address");
        options.addOption("A", "skip-answer", false, "simulates an unanswered call");
        options.addOption("t", "tmr", false, "transmission medium required (integer value)");
        options.addOption("v", "verbose", false, "verbose output");
        options.addOption("h", "help", false, "prints this help");
        HelpFormatter formatter = new HelpFormatter();
        
        try {
            CommandLine line = parser.parse(options, args);

            if (line.hasOption("help")) {
                formatter.printHelp("BasicOCPApp", options);
                System.exit(1);
            }
            if (line.hasOption("calling")) {
                throw new ParseException("'calling' is not yet implemented.");                
            }
            if (line.hasOption("tmr")) {
                throw new ParseException("'tmr' is not yet implemented.");                
            }
            if (line.hasOption("called")) {
                // turn the dialled number into a byte array
                // there must be an easier way than this!!!
                String called = line.getOptionValue("called");
                ByteBuffer buf = ByteBuffer.allocate(called.length());
                char[] fin_char = called.toCharArray();
                for (char c : fin_char) {
                    buf.put((byte) Character.getNumericValue(c));
                }
                if (buf.hasArray()) {
                    fin = buf.array(); 
                }
            }
            if (line.hasOption("client")) {
                local_ip = line.getOptionValue("client");
            }
            if (line.hasOption("service-key")) {
                serviceKey = Integer.parseInt(line.getOptionValue("service-key"));
            }
            if (line.hasOption("master")) {
                remote_ip0 = line.getOptionValue("master");
            }
            if (line.hasOption("slave")) {
                remote_ip1 = line.getOptionValue("slave");
            }
            skipAnswer = line.hasOption("skip-answer");
            if (line.hasOption("verbose")) {
                logger.setLevel(Level.ALL);
            } else {
                logger.setLevel(Level.WARN);
            }
        } catch (ParseException e) {
            System.out.println("Invalid arguments: " + e);            
            formatter.printHelp("BasicOCPApp", options);            
            System.exit(1);
        }
        System.out.printf("from %s to %s and %s for %s\n", 
                local_ip, 
                remote_ip0,
                remote_ip1,
                Arrays.toString(fin));
        
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
        while (!testDone) {
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
        properties.setProperty("ocpLink0LocalAddress", local_ip);
        properties.setProperty("ocpLink1RemoteAddress", remote_ip1);
        properties.setProperty("ocpLink1LocalAddress", local_ip);
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
                testDone = true;      // the call is over!
                break;
               
            case REQUEST_CLEARDOWN:
                testDone = true;     
                break;
                
            case SET_CDR_EXTENDED_FIELD_DATA:
                doSetCDRExtendedFieldData((SetCDRExtendedFieldData) message);
                break;
                
            case PLAY_FILE:
                doPlayFile((PlayFile) message);
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

    /** Handle the a Play File request. */
    private static void doPlayFile(final PlayFile playFile) {
        long file = playFile.getFileNo();
        System.out.printf("Play File - %d\n", file);
        if(playFile.getFlags() != 0) {          //has a play done been requested?
            DonePlay returnMessage = new DonePlay();
            returnMessage.setZipNumber(playFile.getZipNumber());
            sendOCPMessage(returnMessage);
        }
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
            testDone = true;
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
        returnMessage.setTime(SignallingUtil.getOceanTime());
        returnMessage.setZipNumber(deliverTo.getZipNumber());
        
        if(skipAnswer) {   // treat the call as having failed
            returnMessage.setOutdialFailureReason(SignallingUtil.Q850_CAUSE_USER_BUSY);
        }
        else {
            returnMessage.setFlags(DeliverToResult.FLAG_OUTDIAL_SUCCEEDED);
            testDone = true;
        }
        sendOCPMessage(returnMessage);
        
        System.out.print("Call Delivered To - " );
        for (byte digit :deliverTo.getOutdialTelno().getUnpackedDigits() ) {
            System.out.print(digit);
        }
        System.out.print("\n");
    }
  
    
    /**
     * Handle a set cdr extended field data message.
     * @param SetCDRExtendedFieldData fieldData request
     */   
    private static void doSetCDRExtendedFieldData(final SetCDRExtendedFieldData fieldData) {
        // send a result back to ack the message
        SetCDRExtendedFieldDataResult returnMessage = new SetCDRExtendedFieldDataResult(); 
        sendOCPMessage(returnMessage);
        byte[] extendedData = fieldData.getRawData();
        long serviceVersion = ((extendedData[0] << 24) & 0xff000000) | ((extendedData[1] << 16) & 0xff0000) | ((extendedData[2] << 8) & 0xff00) | (extendedData[3] & 0xff);
        long blockCdrId = ((extendedData[4] << 24) & 0xff000000) | ((extendedData[5] << 16) & 0xff0000) | ((extendedData[6] << 8) & 0xff00) | (extendedData[7] & 0xff);

        // print out the service version & block ID that would go in the CDR
        System.out.printf("CDR data service version = %d last block cdr-id = %d \n", 
                serviceVersion,
                blockCdrId);
    }
    
    /**
     * Build an initial DP (detection point) message to the OCP link.
     * @return InitialDP message
     */
    private static InitialDP buildInitialDP() {
        InitialDPServiceKey message = new InitialDPServiceKey();
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
        message.setServiceKey(serviceKey);
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
