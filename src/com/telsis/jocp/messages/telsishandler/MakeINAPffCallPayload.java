/*
 * Telsis Limited jOCP library
 *
 * Copyright (C) Telsis Ltd. 2012-2013.
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
package com.telsis.jocp.messages.telsishandler;

import java.nio.ByteBuffer;

/**
 * Represents the payload of an Update Matched Digits Telsis Handler.
 */
public class MakeINAPffCallPayload implements TelsisHandlerPayload {

    /** Length. */
    private static final short LENGTH = 56;
    private static final int MAX_TELNO_DIGITS = 32;
    private short cliMode;
    private short typePlan;
    private String outdialNo = "";
    private short cliPresnPlan;
    private String cliNo = "";

    /**
     * @return the matchedDigits
     */
    public final String getOutdialNo() {
        return outdialNo;
    }


    @Override
    public final void encode(final ByteBuffer buffer) {

    }

    @Override
    public final void decode(final ByteBuffer buffer) {
       // if (buffer.remaining() < LENGTH) {
       //     throw new IllegalArgumentException("Invalid buffer length");
       // }
        cliMode = buffer.getShort();
        typePlan = buffer.getShort();
        
        outdialNo = decodeTelno(buffer);
    }

    private String decodeTelno(final ByteBuffer buffer) {
        short length = buffer.getShort();

        int fieldEnd = buffer.position() + (MAX_TELNO_DIGITS / 2);

        // check the length looks sensible
        if (length < 0 || length > MAX_TELNO_DIGITS) {
            throw new IllegalArgumentException("invalid length: " + length);
        }

        StringBuilder telno = new StringBuilder(length);

        // nibble-unpack the digits
        for (byte i = 0, b = 0; i < length; i++) {
            if (i % 2 != 0) {
                telno.append(decodeDigit((byte) (b & 0x0F))); // CSIGNORE: MagicNumber
            } else {
                b = buffer.get();
                telno.append(decodeDigit((byte) ((b & 0xF0) >> 4))); // CSIGNORE: MagicNumber
            }
        }

        // move past this field
        buffer.position(fieldEnd);

        return telno.toString();
    }
    
    private char decodeDigit(final byte digit) {
        if (digit <= 9) { // CSIGNORE: MagicNumber
            return (char) (digit + '0');
        } else {
            return (char) (digit - 0xA + 'A'); // CSIGNORE: MagicNumber
        }
    }
    
    @Override
    public short getLength() {
        return LENGTH;
    }

}
