/**
 * 
 */
package com.protocol.gbnsender;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

import static java.lang.Math.toIntExact;

/**
 * @author vishal
 *
 */
public class Sender implements Runnable {
	public static File file;
	public static long length, fposition, length1;
	public static FileChannel inChannel, foutChan = null;
	public static ByteBuffer buffer = ByteBuffer.allocate(1000);
	public static byte[] buf = new byte[buffer.remaining()];
	public int windowInp, errorRate;
	public int userChoice, randomInt;
	public long totalPackets, lastUnAck = 0;
	Shared sharedobj;
	Sender myobj;
	DatagramSocket sock;

	public void sendShared(Shared sobj, Sender sendobj) {
		this.sharedobj = sobj;
		this.myobj = sendobj;
	}

	public void sendSocket(DatagramSocket sock) {
		this.sock = sock;
	}

	public void getFileName(File file, long packets) {
		this.file = file;
		this.totalPackets = packets;
	}

	public void getWindow(int userInput, int errorRate) {
		this.windowInp = userInput;
		this.errorRate = errorRate;
	}

	public void getOption(int userChoice) {
		this.userChoice = userChoice;
	}

	public static byte[] readFile(long packet) {
		try {
			length1 = file.length();
			inChannel = new FileInputStream(file).getChannel();
			buffer = ByteBuffer.allocate(1000);
			buf = new byte[buffer.remaining()];
			length = buf.length;
			if (packet == 0) {
				fposition = 0;
			} else {
				fposition = packet * length;
			}
			inChannel.position(fposition);
			inChannel.read(buffer);// write
			buffer.flip();
			buffer.get(buf);// read from the buffer
			buffer.compact();
		} catch (BufferUnderflowException bue) {
			long m = length1 - fposition;
			int n = toIntExact(m);
			buffer = ByteBuffer.allocate(n);
			buf = new byte[buffer.remaining()];
			length = buf.length;
			try {
				inChannel.position(fposition);
				inChannel.read(buffer);// write
				buffer.flip();
				buffer.get(buf);
				fposition = fposition + n;
			} catch (IOException ex) {
			} // inner catch
		} catch (IOException ex) {
		} // outer catch
		return buf;
	}

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
		s = ~s;
		s = s & 0xFFFF;
		return s;
	}

	public byte[] long2byte(long l) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(Long.SIZE / 8);
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeLong(l);
		byte[] result = baos.toByteArray();
		dos.close();
		return result;
	}

	public byte[] append2Arrays(byte[] arr1, byte[] arr2) {
		byte[] appendarray = new byte[arr1.length + arr2.length];
		System.arraycopy(arr1, 0, appendarray, 0, arr1.length);
		System.arraycopy(arr2, 0, appendarray, arr1.length, arr2.length);
		return appendarray;
	}

	public void sendPack(int window) {
		DatagramPacket[] sendpacket = new DatagramPacket[10000];
		byte[] databuf = new byte[1000];
		byte[] checksumbuf = new byte[8];
		byte[] seqnumbuf = new byte[8];
		byte[] intermbuf = new byte[databuf.length + checksumbuf.length];
		byte[] packbuffer = new byte[intermbuf.length + seqnumbuf.length];
		long base = 0, max;
		int r, num;
		long n = 0, checksu;
		try {
			InetAddress ip = InetAddress.getByName("localhost");
			synchronized (sharedobj) {
				while (true) {
					lastUnAck = sharedobj.getLastUnAck();
					if (base == lastUnAck)// timedout
					{
						n = base;
						max = base + window;
					} else if (lastUnAck > totalPackets) {
						break;
					} else {
						base = lastUnAck;
						max = base + window;
					}
					while (base <= n && n < max) {
						if (n > totalPackets)
							break;

						Random randomGenerator = new Random();
						randomInt = randomGenerator.nextInt(100);
						if (randomInt < errorRate && userChoice == 1)// Introducing errors in the packet
						{
							databuf = readFile(n);
							checksu = calculateChecksum(databuf);
							checksumbuf = long2byte(checksu);
							databuf[0] = 19;
							intermbuf = append2Arrays(databuf, checksumbuf);
							packbuffer = append2Arrays(intermbuf, seqnumbuf);
							num = (int) n;
							sendpacket[num] = new DatagramPacket(packbuffer, packbuffer.length, ip, 5000);
							sock.send(sendpacket[num]);
							System.out.println("Sent Packet " + n);
						} else if (randomInt < errorRate && userChoice == 2) {
							databuf = readFile(n);
							checksu = calculateChecksum(databuf);
							checksumbuf = long2byte(checksu);
							intermbuf = append2Arrays(databuf, checksumbuf);
							seqnumbuf = long2byte(n);
							packbuffer = append2Arrays(intermbuf, seqnumbuf);
							num = (int) n;
							sendpacket[num] = new DatagramPacket(packbuffer, packbuffer.length, ip, 5001);
							sock.send(sendpacket[num]);
							System.out.println("Sent Packet (another port) " + n);
						} else {
							databuf = readFile(n);
							checksu = calculateChecksum(databuf);
							checksumbuf = long2byte(checksu);
							intermbuf = append2Arrays(databuf, checksumbuf);
							seqnumbuf = long2byte(n);
							packbuffer = append2Arrays(intermbuf, seqnumbuf);
							num = (int) n;
							sendpacket[num] = new DatagramPacket(packbuffer, packbuffer.length, ip, 5000);
							sock.send(sendpacket[num]);
							System.out.println("Sent Packet " + n);
						}
						n++;
					}
					sharedobj.notify();
					try {
						sharedobj.wait();
					} catch (InterruptedException ex) {
					}
				}
			}
		} catch (Exception ex) {
		}
	}

	public void run() {
		sendPack(windowInp);
	}
}
