/*
 * Telsis Limited jOCP library
 *
 * Copyright (C) Telsis Ltd. 2011-2013.
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
package com.telsis.jocp.messages;

import java.nio.ByteBuffer;

import com.telsis.jocp.CallMessageException;
import com.telsis.jocp.LegacyOCPMessageTypes;
import com.telsis.jocp.OCPException;

/**
 * Send this message if you want to make a call handling unit play an audio prompt.
 * The expected reply to this message is {@link DonePlay}
 * <p/>
 * This message has the following parameters:
 * <table>
 * <tr>
 * <th>Field Name</th>
 * <th>Size</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>destLegID</td>
 * <td>2</td>
 * <td>The ID of the leg of the call to whom the file is to be played.
 * This is either the calling party or the called party.</td>
 * </tr>
 * <tr>
 * <td>spare</td>
 * <td>1</td>
 * <td><i>For word alignment</i></td>
 * </tr>
 * <tr>
 * <td>zipNumber</td>
 * <td>1</td>
 * <td>The index into the zip table on the SCP for this result</td>
 * </tr>
 * <tr>
 * <td>fileNo</td>
 * <td>4</td>
 * <td>The number of the file to be played</td>
 * </tr>
 * <tr>
 * <td>flags</td>
 * <td>2</td>
 * <td>Bit 0 - send Done Play message when completed</td>
 * </tr>
 * <tr>
 * <td>numRepeats</td>
 * <td>2</td>
 * <td>Number of times to play the file, 0=infinite</td>
 * </tr>
 * </table>
 *
 * @see DonePlay
 * @author John Schofield
 */
public class PlayFile extends CallControlMessage{
    

    /** The message type. */
    public static final LegacyOCPMessageTypes TYPE =
            LegacyOCPMessageTypes.PLAY_FILE;
    /** The expected length of the message. */
    private static final int            EXPECTED_LENGTH = 12;


    /** The ID of the leg of the call for which the CDR information is to be set. */
    private short destLegID;
    /** The index into the zip table on the SCP for this result. */
    private byte zipNumber;
    /** The number of the file to play**/
    private int fileNo;
    /** Flags - just one bit to request a PlayDone response **/
    private short flags;
    /** The number of times to play the file **/
    private short numRepeats;
    
    /**
     * Decode the buffer into an Set CDR Extended Field Data message.
     *
     * @param buffer
     *            the message to decode
     * @throws OCPException
     *             if the buffer could not be decoded
     */
    public PlayFile(final ByteBuffer buffer) throws OCPException {
        super(buffer);
        super.advance(buffer);

        if (buffer.limit() != EXPECTED_LENGTH) {
            throw new CallMessageException(
                    getDestTID(),
                    getOrigTID(),
                    TYPE.getCommandCode(),
                    CallCommandUnsupported.REASON_LENGTH_UNSUPPORTED,
                    (short) buffer.limit());
        }

        destLegID = buffer.getShort();
        buffer.get(); // for word alignment
        zipNumber = buffer.get();
        fileNo = buffer.getInt();
        flags = buffer.getShort();
        numRepeats = buffer.getShort();
    }

    /**
     * Instantiates a new Play File message.
     */
    public PlayFile() {
        super(TYPE.getCommandCode());
    }

    @Override
    protected final void encode(final ByteBuffer buffer) {
        super.encode(buffer);

        buffer.putShort(destLegID);
        buffer.put((byte) 0);
        buffer.put(zipNumber);
        buffer.putInt(fileNo);
        buffer.putShort(flags);
        buffer.putShort(numRepeats);
    }
    
    /**
     * Gets the ID of the leg of the call for which the CDR information is to be
     * set.
     *
     * @return the ID of the leg of the call for which the CDR information is to
     *         be set
     */
    public final short getDestLegID() {
        return destLegID;
    }

    /**
     * Sets the ID of the leg of the call for which the CDR information is to be
     * set.
     *
     * @param newDestLegID
     *            the new ID of the leg of the call for which the CDR
     *            information is to be set
     */
    public final void setDestLegID(final short newDestLegID) {
        this.destLegID = newDestLegID;
    }

    /**
     * Gets the index into the zip table on the SCP for this result.
     *
     * @return the index into the zip table on the SCP for this result
     */
    public final byte getZipNumber() {
        return zipNumber;
    }

    /**
     * Sets the index into the zip table on the SCP for this result.
     *
     * @param newZipNumber
     *            the new index into the zip table on the SCP for this result
     */
    public final void setZipNumber(final byte newZipNumber) {
        this.zipNumber = newZipNumber;
    }

    /**
     * Gets the file number.
     *
     * @return the file number for the play
     */
    public final int getFileNo() {
        return fileNo;
    }

    /**
     * Sets the file number.
     *
     * @param int the number of the file to play
     */
    public final void setFileNo(final int fileNo) {
        this.fileNo = fileNo;
    }
    
    /**
     * Gets the flags data
     *
     * @return the flags associated with the play
     */
    public final short getFlags() {
        return flags;
    }

    /**
     * Sets the flags data.
     *
     * @param short the flags data
     */
    public final void setFileNo(final short flags) {
        this.flags = flags;
    }
    
    /**
     * Gets the number of times the file should be played
     *
     * @return the number of times to play the file
     */
    public final short getNumRepeats() {
        return numRepeats;
    }

    /**
     * Sets the flags data.
     *
     * @param short the flags data
     */
    public final void setNumRepeats(final short numRepeats) {
        this.flags = numRepeats;
    }
}
