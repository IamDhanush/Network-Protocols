/**
 * 
 */
package com.protocol.receiver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @author vishal
 *
 */
public class SelectRepeatReceiver {
	public static DatagramSocket sock;
	public DatagramPacket sendAckPacket, recvDataPacket;
	public byte[] recvBytes;
	public byte[] sendBytes = new byte[16];
	public long checkSum, seq_No, recv_Window, totalPackets, base = 1;
	public int port, packet_Size;
	public boolean finFlag = false;
	byte[] dataBuf, checkSumBuf, seqNoBuf, chkDataBuf;
	public InetAddress ip;
	public ByteBuffer fileBuffer;
	public LinkedList<Integer> LL = new LinkedList<Integer>();
		
		public long calculateChecksum(byte[] buf) 
	    {
	    int l = buf.length;
	    int i = 0;
	    long s = 0;
	    long d;
	    while (l > 1) 
	    {
	      d = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
	      s += d;
	      if ((s & 0xFFFF0000) > 0) 
	      {
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
		
		public byte[] long2Byte(long l) throws IOException
	    {
	        ByteArrayOutputStream baos=new ByteArrayOutputStream(Long.SIZE/8);
	        DataOutputStream dos=new DataOutputStream(baos);
	        dos.writeLong(l);
	        byte[] result=baos.toByteArray();
	        dos.close();    
	        return result;
	    }
	        
	    public long byte2Long(byte[] b) throws IOException
	    {
	        ByteArrayInputStream baos=new ByteArrayInputStream(b);
	        DataInputStream dos=new DataInputStream(baos);
	        long result=dos.readLong();
	        dos.close();
	        return result;
	    }
	    
	    public int byte2Int(byte[] b) throws IOException
	    {
	        ByteArrayInputStream baos=new ByteArrayInputStream(b);
	        DataInputStream dos=new DataInputStream(baos);
	        int result=dos.readInt();
	        dos.close();
	        return result;
	    }
	    
	    public byte[] blendArrays(byte[] arr1,byte[] arr2)
	     {
	      byte[] appendarray=new byte[arr1.length+arr2.length];
	      System.arraycopy(arr1,0,appendarray,0,arr1.length);
	      System.arraycopy(arr2,0,appendarray,arr1.length,arr2.length);
	      return appendarray; 	 
	     }
	    
	    public boolean checkSeqNo(int i)
	    {
	       boolean flag=false;
	       flag=LL.contains(i);
	       LL.remove(new Integer(i));
	       return flag;
	    }
	    
	    public void sendAck(byte[] seqNoBuf)
	    {
	      try
	      {
	      	  byte[] checkSum=long2Byte(calculateChecksum(seqNoBuf));
	          sendBytes=blendArrays(checkSum,seqNoBuf);
	          sendAckPacket=new DatagramPacket(sendBytes,sendBytes.length,ip,port);
	          sock.send(sendAckPacket);
	      }
	      catch(Exception e)
	      {
	        System.out.println(e);
	      }
	    }
	    
		public void processPacket(int Length)
		{
		 try
			{
		    checkSumBuf=new byte[8];
		    seqNoBuf=new byte[8];
		    chkDataBuf=new byte[Length-checkSumBuf.length];
			System.arraycopy(recvBytes,0,checkSumBuf,0,checkSumBuf.length);
			System.arraycopy(recvBytes,checkSumBuf.length,chkDataBuf,0,chkDataBuf.length);
			System.out.println("Data in the packet: "+new String(chkDataBuf));
			System.out.println("checksum packet length: "+chkDataBuf.length);
			checkSum=calculateChecksum(chkDataBuf);
		    System.out.println("CheckSum: "+checkSum);
		    System.out.println("Received Packet CheckSum: "+byte2Long(checkSumBuf));
		    //condition checksum
		      if(checkSum+byte2Long(checkSumBuf)==65535)
		         {
		          System.arraycopy(recvBytes,checkSumBuf.length,seqNoBuf,0,seqNoBuf.length);
		          seq_No=byte2Long(seqNoBuf);
		          System.out.println("Sequence No.: "+byte2Long(seqNoBuf));
		        
		          if(seq_No==0)
		               {
		                byte[] recvWindow=new byte[4];
		                byte[] packetSize=new byte[4];
		                dataBuf=new byte[Length-(seqNoBuf.length+checkSumBuf.length+recvWindow.length+packetSize.length)];	
		                System.arraycopy(recvBytes,checkSumBuf.length+seqNoBuf.length,recvWindow,0,recvWindow.length);
		                System.arraycopy(recvBytes,checkSumBuf.length+seqNoBuf.length+recvWindow.length,packetSize,0,packetSize.length);
		                System.arraycopy(recvBytes,checkSumBuf.length+seqNoBuf.length+recvWindow.length+packetSize.length,dataBuf,0,dataBuf.length);
		                
		                
		                port=recvDataPacket.getPort();
		                ip=recvDataPacket.getAddress();
		                System.out.println("Receive Window Size: "+byte2Int(recvWindow));
		                recv_Window=Long.valueOf(byte2Int(recvWindow));
		                
		                System.out.println("Packet Size: "+byte2Int(packetSize));
		                packet_Size=byte2Int(packetSize);
		                recvBytes=new byte[packet_Size+seqNoBuf.length+checkSumBuf.length];
		                System.out.println("File Length: "+byte2Int(dataBuf));
		                int fileLength=byte2Int(dataBuf);
		                fileBuffer= ByteBuffer.allocate(fileLength);
		                
		                if((fileLength % packet_Size)==0)
	                          {
	                           totalPackets=fileLength/packet_Size;
	                           System.out.println("Total Packets: "+totalPackets);
	                          }
	                    else
	       	                  {
	       	   	               totalPackets=(fileLength/packet_Size)+1;
	       	   	               System.out.println("Total Packets: "+totalPackets);
	       	                  } 
		              
	       	      
	       	            sendAck(seqNoBuf);
		               }
		           else if(base<=seq_No && seq_No<(base+recv_Window))
		           	   {
		           	   try{Thread.sleep(300);}
		           	   catch (InterruptedException e) {}
		           	   sendAck(seqNoBuf);
		           	   dataBuf=new byte[Length-(seqNoBuf.length+checkSumBuf.length)];
		           	   System.arraycopy(recvBytes,checkSumBuf.length+seqNoBuf.length,dataBuf,0,dataBuf.length);
		           	   System.out.println("Data in DataBuf: "+new String(dataBuf));
		      	   	   fileBuffer.position((int)((seq_No-1)*packet_Size));
		      	   	   System.out.println("Position: "+fileBuffer.position());
		      	   	      System.out.println("databuf Length: "+dataBuf.length);
		      	   	   fileBuffer.put(dataBuf);
		      	   	   
		   	   	   	   LL.add((int)seq_No);
		           	   	   	   //send the ack packet with seq no	
		           	   	   	   while(base==seq_No && !LL.isEmpty())
		           	   	   	   	   {
		           	   	   	   	   if(checkSeqNo((int)seq_No))
		           	   	   	   	     {
		           	   	   	   	     	 base++;
		           	   	   	   	     	 if(base>totalPackets)
		           	   	   	   	     	 	 {
		           	   	   	   	     	 	  seqNoBuf=new String("FINISHFL").getBytes();
		           	   	   	   	     	 	  sendAck(seqNoBuf);
		           	   	   	   	     	 	  System.out.println(seqNoBuf.length);
		           	   	   	   	     	 	  finFlag=true;
		           	   	   	   	     	 	  break;
		           	   	   	   	     	 	 }
		           	   	   	   	         continue;
		           	   	   	   	     }
		           	   	   	   	   else
		           	   	   	   	     {
		           	   	   	   	     break;
		           	   	   	   	     }
		           	   	   	   	   }
		           	   	}
		           else if(seq_No<base)
		           	   	{
	                      sendAck(seqNoBuf);  	   
		           	   	}
		           	}
		         }
		    
		    catch(Exception ex){System.out.println(ex);}
		    
		}
		
		public static void main(String args[]) 
	    {
			try
			{
				 SelectRepeatReceiver srobj=new SelectRepeatReceiver();
				 sock=new DatagramSocket(15000);
				 srobj.recvBytes=new byte[32];
				 while(!srobj.finFlag)
				  {
					   srobj.recvDataPacket=new DatagramPacket(srobj.recvBytes,srobj.recvBytes.length);
					   sock.receive(srobj.recvDataPacket);
			           srobj.processPacket(srobj.recvDataPacket.getLength());
			           System.out.println("Received Packet Length: "+srobj.recvDataPacket.getLength());
		          }
		          
		           sock.close();
		           System.out.println(new String(srobj.fileBuffer.array(),"ASCII"));
		           
		    }
		   
		   catch(Exception ex){System.out.println(ex);}
		}
}
