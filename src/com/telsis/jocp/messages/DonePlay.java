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
 * Send this message to signify that the requested file play has completed.
 *
 * <p/>
 * This message has the following parameters:
 * <table>
 * <tr>
 * <th>Field Name</th>
 * <th>Size</th>
 * <th>Description</th>
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
 * </table>
 *
 * @see PlayFile
 * @author John Schofield
 */
public class DonePlay extends CallControlMessage {
    /** The message type. */
    public static final LegacyOCPMessageTypes TYPE = LegacyOCPMessageTypes.DONE_PLAY;
    /** The expected length of the message. */
    private static final int            EXPECTED_LENGTH = 2;


    /** The index into the zip table on the SCP for this result. */
    private byte zipNumber;
    
    protected DonePlay(ByteBuffer buffer) throws OCPException {
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
        buffer.get(); // for word alignment
        zipNumber = buffer.get();
    }
    
    /**
     * Instantiates a new Done Play message.
     */
    public DonePlay() {
        super(TYPE.getCommandCode());
    }
    
    @Override
    protected final void encode(final ByteBuffer buffer) {
        super.encode(buffer);
        buffer.put((byte) 0);
        buffer.put(zipNumber);
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

}
