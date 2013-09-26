# Telsis Limited jOCP library
Extended by John Schofield
## Licensing
Copyright (C) Telsis Ltd. 2013.

This Program is free software: you can copy, redistribute and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License or (at your option) any later version.

If you modify this Program you must mark it as changed by you and give a relevant date.

This Program is published in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
receive a copy of the GNU General Public License along with this program. If not,
see <http//www.gnu.org/licenses/>.
 
In making commercial use of this Program you indemnify Telsis Limited and all of its related
Companies for any contractual assumptions of liability that may be imposed on Telsis Limited
or any of its related Companies.

This library provides an implementation of the Ocean Control Protocol (OCP) in Java.

## Build Requirements
* Maven (3.0.5) or Ant (1.7.1) & Ivy (2.2)

## Dependencies
* Log4J

## Sample Application
`com.telsis.jocp.sampleApp` provides the source code for a basic sample application.
This is an adapted version of the original Telsis application.  The aim being to extend this so that it can 
be used as a  test client to verify the behaviour of maps running on the Telsis Ocean 2280.

At the moment it:

* Establishes an OCP link to a pair of remote OCP servers
* Notifies the remote server of an initial detection point event (Initial DP)
* Replies automatically to the Update Matched Digits Telsis handler
* On receipt of request to perform a delivery, prints the destination number and returns a `DeliverToResult` indicating the call was delivered successfully.
* On receipt of a 'Make Fire and Forget INAP Call' Telsis handler with party, prints the destination number and returns a result

To use the sample application you will need a remote OCP server (i.e. an Ocean 2280) running a Map that does a DeliverTo of DeliverTo(INAP)
