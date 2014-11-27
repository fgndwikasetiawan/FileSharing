/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package filesharingserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Dwika
 */

public class SharedFileServer extends Thread {
	
    private ServerSocket serverSocket;
    private String filename;
    private String originalFilename;
    private String senderId;
    private HashMap <String, Boolean> recipients;
    private Long filesize;

    public SharedFileServer (String filename, String originalFilename, String senderId, long filesize, ArrayList<ClientThread> recipientList) throws Exception {
        this.filename = filename;
        this.originalFilename = originalFilename;
        this.senderId = senderId;
        this.filesize = filesize;
        recipients = new HashMap <> ();
        for (ClientThread rec : recipientList) {
            recipients.put (rec.getClientId(), true);
        }
        serverSocket = new ServerSocket (0);
        File f = new File (filename);
        f.createNewFile();
    }
    
    public int getPort () {
        return serverSocket.getLocalPort ();
    }
    
    
    @Override
    public void run () {
        BufferedReader reader;
        BufferedWriter writer;
        while (!serverSocket.isClosed ()) {
            try {
                Socket s = serverSocket.accept();
                reader = new BufferedReader (new InputStreamReader (s.getInputStream ()));
                writer = new BufferedWriter (new OutputStreamWriter (s.getOutputStream ()));
                writer.flush ();
                try {
                    Scanner scan = new Scanner (reader.readLine ());
                    scan.useDelimiter (" ");
                    String command = scan.next();
                    String id = scan.next();
                    String key = scan.next();
                    switch (command) {
                        case "WRITE":
                            if (id.equals(senderId)) {
                                ClientThread ct = FileSharingServer.getClientThread(id);
                                if (ct.getClientKey().equals(key)) {
                                    SharedFileWriter sfw = new SharedFileWriter (s, filename, filesize);
                                    sfw.start();
                                    writer.write ("500 OK\r\n");
                                    writer.flush ();
                                }
                                else {
                                    writer.write ("501 DENIED\r\n");
                                    writer.flush ();
                                    break;
                                }
                            }
                        break;
                        case "READ":
                            if (recipients.containsKey(id)) {
                                ClientThread ct = FileSharingServer.getClientThread(id);
                                long offset = 0;
                                
                                try {
                                    offset = scan.nextLong ();
                                }
                                catch (Exception ex) {}
                                
                                if (ct.getClientKey().equals(key)) {
                                    SharedFileReader sfr = new SharedFileReader (s, id, filename, filesize, offset, recipients, serverSocket);
                                    sfr.start();
                                    writer.write ("500 OK\r\n");
                                    writer.flush ();
                                }
                                else {
                                    writer.write ("501 DENIED\r\n");
                                    writer.flush ();
                                    break;
                                }
                            }
                            break;
                        default:
                            writer.write("502 BAD COMMAND\r\n");
                            writer.flush();
                    }
                }
                catch (Exception ex) {
                    writer.write ("502 BAD COMMAND\r\n");
                    writer.flush ();
                    s.close();
                }
            }
            catch (Exception ex) {
                
            }
        }
        System.out.println ("file " + filename + " on port " + serverSocket.getLocalPort() + " has been closed\r\n");
        File f = new File (filename);
        f.delete();
    }
    
    private class SharedFileWriter extends Thread {
        private Socket socket;
        private String filename;
        private Long filesize;
        public SharedFileWriter (Socket socket, String filename, Long filesize) {
            this.socket = socket;
            this.filename = filename;
            this.filesize = filesize;
        }
        @Override
        public void run () {
            long written = 0;
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream (filename);
                InputStream in = socket.getInputStream ();
                int bytesRead;
                byte[] buffer = new byte[1024];
                while (true) {
                    bytesRead = in.read (buffer);
                    fos.write (buffer, 0, bytesRead);
                    written += bytesRead;
                }
            }
            catch (Exception ex) {
                try {
                    if (fos != null) fos.close();
                } catch (IOException ex1) {
                    Logger.getLogger(SharedFileServer.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
            filesize = written;
        }
    }
    
    private class SharedFileReader extends Thread {
        private final Socket socket;
        private final String filename;
        private final Long filesize;
        private long offset;
        private final String recId;
        private final HashMap <String, Boolean> recipients;
        private final ServerSocket serverSocket;
        
        public SharedFileReader (Socket socket, String recId, String filename, Long filesize, long offset, HashMap <String, Boolean> recipients, ServerSocket serverSocket) {
            this.socket = socket;
            this.filename = filename;
            this.filesize = filesize;
            this.offset = offset;
            this.recId = recId;
            this.recipients = recipients;
            this.serverSocket = serverSocket;
        }
        
        @Override 
        public void run () { 
            try {
                FileInputStream fis = new FileInputStream (filename);
                OutputStream out = socket.getOutputStream ();
                
                System.out.println ("Offset: " + offset + "\n" + "Skipping " + fis.skip (offset) + " bytes");
                int bytesRead;
                byte[] buffer = new byte[1024];
                
                
                while (offset < filesize) {
                    bytesRead = fis.read (buffer);
                    if (bytesRead > 0) {
                        out.write (buffer, 0, bytesRead);
                        offset += bytesRead;
                    }
                }
                
                fis.close ();
                socket.close();
                recipients.remove (recId);
                //kalau semua recipient udah selesai download
                if (recipients.isEmpty ()) {
                    //stop listening
                    serverSocket.close ();
                }
            } 
            //kalau recipient tiba-tiba diskonek, simpen posisi baca file terakhir
            catch (Exception ex) {
                Logger.getLogger(AuthSession.class.getName()).log(Level.SEVERE, null, ex);
                System.out.println (recId + " diskonek pada posisi " + offset);
            }
            
        }
    }
	
}