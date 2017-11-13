/**
 * @author vishal
 *
 */
package com.protocol.gbnreceiver;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.Scanner;

public class GbnReceiver {
			public static DatagramSocket sock = null;
			public static DatagramPacket sizepacket, acksize, sendackpack = null;
			public static DatagramPacket[] recvpacket = new DatagramPacket[10000];
			public static FileOutputStream fos = null;
			public static int bufferLength, randomInt;
			public static long previousSequence;

			public static long calculateChecksum(byte[] buf) {
				int l = buf.length;
				int i = 0;
				long s = 0;
				long d;
				while (l > 1) {
					d = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
					s += d;
					if ((s & 0xFFFF0000) > 0) {
						s = s & 0xFFFF;
						s += 1;
					}

					i += 2;
					l -= 2;
				}
				if (l > 0) {
					s += (buf[i] << 8 & 0xFF00);
					if ((s & 0xFFFF0000) > 0) {
						s = s & 0xFFFF;
						s += 1;
					}
				}
				return s;
			}

			public static byte[] long2byte(long l) throws IOException {
				ByteArrayOutputStream baos = new ByteArrayOutputStream(Long.SIZE / 8);
				DataOutputStream dos = new DataOutputStream(baos);
				dos.writeLong(l);
				byte[] result = baos.toByteArray();
				dos.close();
				return result;
			}

			public static long byte2long(byte[] b) throws IOException {
				ByteArrayInputStream baos = new ByteArrayInputStream(b);
				DataInputStream dos = new DataInputStream(baos);
				long result = dos.readLong();
				dos.close();
				return result;
			}

			public static byte[] append2Arrays(byte[] arr1, byte[] arr2) {
				byte[] appendarray = new byte[arr1.length + arr2.length];
				System.arraycopy(arr1, 0, appendarray, 0, arr1.length);
				System.arraycopy(arr2, 0, appendarray, arr1.length, arr2.length);
				return appendarray;
			}

			public static byte[] extractSeq(byte[] arr3, int length) {
				int f = length - 8;
				int k = length - f;
				byte[] arr4 = new byte[k];
				for (int i = f; i < length; i++) {
					arr4[i - f] = arr3[i];
				}
				return arr4;
			}

			public static byte[] extractChecksum(byte[] arr3, int length) {
				int g = length - 16;
				int f = length - 8;
				int k = length - f;
				byte[] arr4 = new byte[k];
				for (int i = g; i < f; i++) {
					arr4[i - g] = arr3[i];
				}
				return arr4;
			}

			public static byte[] extractData(byte[] arr3, int length) {
				int k = length - 16;
				byte[] arr4 = new byte[k];
				System.arraycopy(arr3, 0, arr4, 0, k);
				return arr4;
			}

			public static void main(String args[]) {
				GbnReceiver Gbnreceiver = new GbnReceiver();
				try {

					try {
						sock = new DatagramSocket(5000);
					} catch (IOException ex) {
						System.out.println(ex);
					}

					Scanner scannerfile = new Scanner(System.in);
					System.out.println("Enter Error Percentage: ");
					int errorPercentage = scannerfile.nextInt();
					System.out.flush();
					byte[] sbuffer = new byte[10];
					sizepacket = new DatagramPacket(sbuffer, sbuffer.length);
					sock.receive(sizepacket);
					int port = sizepacket.getPort();
					InetAddress ip = sizepacket.getAddress();
					byte[] sackbuf = new byte[1];
					sackbuf[0] = 0;
					acksize = new DatagramPacket(sackbuf, sackbuf.length, ip, port);
					sock.send(acksize);
					long l = byte2long(sbuffer);
					float f = l / 1000;
					int TotalPackets, num;
					TotalPackets = (int) f;
					System.out.println("totalPackets: "+TotalPackets);
					byte[] buffer = new byte[1016];
					byte[] seqnobuf = new byte[8];
					byte[] checksumbuf = new byte[8];
					byte[] databuf = new byte[1000];
					long expectedSequence = 0, sequence_Number;
					long finalchecksu;
					long checksu = 0;
					long checksu1 = 0;
					long expectedchecksu = 0xFFFF;
					byte[] ack = new byte[8];
					while (expectedSequence <= TotalPackets) {
						num = (int) expectedSequence;
						recvpacket[num] = new DatagramPacket(buffer, buffer.length);
						sock.receive(recvpacket[num]);
						System.out.println("received packet " + expectedSequence+ ", Size: "+ recvpacket[num].getLength());
						ip = recvpacket[0].getAddress();
						port = recvpacket[0].getPort();
						bufferLength = recvpacket[num].getLength();
						seqnobuf = extractSeq(buffer, bufferLength);
						sequence_Number = byte2long(seqnobuf);
						checksumbuf = extractChecksum(buffer, bufferLength);
						databuf = extractData(buffer, bufferLength);
						checksu1 = byte2long(checksumbuf);
						checksu = calculateChecksum(databuf);
						finalchecksu = checksu + checksu1;
						fos = new FileOutputStream("a1.jpg", true);
						Random randomGenerator = new Random();
						randomInt = randomGenerator.nextInt(100);
						if (errorPercentage < randomInt) {
							if (finalchecksu == expectedchecksu && sequence_Number == expectedSequence) {
								int length5 = databuf.length;
								fos.write(databuf);
								ack = long2byte(expectedSequence);
								sendackpack = new DatagramPacket(ack, ack.length, ip, port);
								try {
									sock.send(sendackpack);
									System.out.println("sent ack: "+sequence_Number);
								} catch (IOException ex1) {
									System.out.println(ex1);
								}
								expectedSequence += 1;
								// buffer=new byte[bufferLength];
							} // if
							
							  else if(finalchecksu==expectedchecksu && seqnobuf[0]<expectedSequence) {
							  previousSequence=expectedSequence-1;
							  System.out.println("sent ack again for " +previousSequence); 
							  ack=long2byte(previousSequence);
							  sendackpack=new
							  DatagramPacket(ack,ack.length,ip,port);
							  try {
								  sock.send(sendackpack); } 
							  catch(IOException ex1) {
								  System.out.println(ex1);
							  } 
						    }
							 
							else {
								continue;
							}
						} 
					} 
					fos.close();
				} // try
				catch (Exception ex) {
					System.out.println(ex);
				}
				System.out.println("Transfer complete");
			}// main
}


