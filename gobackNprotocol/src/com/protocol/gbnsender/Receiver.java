/**
 * 
 */
package com.protocol.gbnsender;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

/**
 * @author vishal
 *
 */
class Receiver implements Runnable {
	public long lastUnAck = 0, lastAck = -1, totalPackets;
	public int userErrorOption, num;
	Shared sharedobj;
	Receiver myobj;
	DatagramSocket sock;
	DatagramPacket[] recvack = new DatagramPacket[10000];

	public void recvShared(Shared shobj, Receiver robj) {
		this.sharedobj = shobj;
		this.myobj = robj;
	}

	public void recvSocket(DatagramSocket sock) {
		this.sock = sock;
	}

	public void getTotPackets(long packets, int Option) {
		this.totalPackets = packets;
		this.userErrorOption = Option;
	}

	public static long byte2long(byte[] b) throws IOException {
		ByteArrayInputStream baos = new ByteArrayInputStream(b);
		DataInputStream dos = new DataInputStream(baos);
		long result = dos.readLong();
		dos.close();
		return result;
	}

	public void recvPack() {
		byte[] ackbuf = new byte[8];
		long sequenceNumber;
		while (true) {
			synchronized (sharedobj) {
				try {
					sock.setSoTimeout(100);
					num = (int) lastUnAck;
					recvack[num] = new DatagramPacket(ackbuf, ackbuf.length);
					sock.receive(recvack[num]);
					System.out.println("Received Ack " + lastUnAck);
					sequenceNumber = byte2long(ackbuf);
					if (lastAck < sequenceNumber) {
						lastAck = sequenceNumber;
						lastUnAck = lastAck + 1;
						sharedobj.setLastUnAck(lastUnAck);
						if (lastAck == totalPackets) {
							sharedobj.notify();
							break;
						}
					} else {
						continue;
					}
				} catch (SocketTimeoutException ste) {
					sharedobj.setLastUnAck(lastUnAck);
				} catch (Exception ex) {
				}
				sharedobj.notify();
				try {
					sharedobj.wait();
				} catch (InterruptedException ex) {
				}
			}
		}
	}

	public void run() {
		recvPack();
	}
}
