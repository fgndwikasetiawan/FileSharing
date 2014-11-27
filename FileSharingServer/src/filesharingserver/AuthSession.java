
package filesharingserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthSession extends Thread {
    
    public enum ConnectionMode {
        COMMAND, NOTIF;
    }
    
    private Socket socket;
    private ConnectionMode mode;
    
    public AuthSession (Socket socket, ConnectionMode mode) {
        this.socket = socket;
        this.mode = mode;
    }
    
    @Override
    public void run () {
        try
        {
            BufferedWriter writer = new BufferedWriter (new OutputStreamWriter (socket.getOutputStream ()));
            BufferedReader reader = new BufferedReader (new InputStreamReader (socket.getInputStream ()));
        
            writer.write ("WELCOME\r\n");
            writer.flush ();
            
            Scanner s = new Scanner (reader.readLine ());
            s.useDelimiter (" ");
            switch (mode) {
                case COMMAND :
                    try {
                        String id = s.next();
                        if (!FileSharingServer.isClientConnected (id)) {
                            String key = s.next();
                            ClientThread ct = new ClientThread(socket, id, key);
                            FileSharingServer.addClientThread(id, ct);
                            writer.write ("200 AUTHENTICATION OK\r\n");
                            writer.flush ();
                            ct.start();
                        }
                        else {
                            writer.write ("201 ID HAS BEEN TAKEN\r\n");
                            writer.flush ();
                            socket.close ();
                        }
                    }
                    catch (Exception ex) {
                        writer.write ("203 BAD COMMAND\r\n");
                        writer.flush ();
                        socket.close ();
                    }
                    break;
                    
                case NOTIF :
                    try {
                        String id = s.next();
                        if (FileSharingServer.isClientConnected (id)) {
                            ClientThread ct = FileSharingServer.getClientThread (id);
                            String key = s.next();
                            if (key.equals (ct.getClientKey()) ) {
                                ct.setNotifSocket(socket);
                                writer.write ("200 AUTHENTICATION OK\r\n");
                                writer.flush();
                            }
                            else {
                                writer.write ("202 AUTHENTICATION FAILED\r\n");
                                writer.flush ();
                                socket.close();
                            }
                        }
                        else {
                            writer.write ("202 AUTHENTICATION FAILED\r\n");
                            writer.flush ();
                            socket.close();
                        }
                    }
                    catch (Exception ex) {
                        writer.write ("203 BAD COMMAND\r\n");
                        writer.flush ();
                        socket.close ();
                    }
                    break;
                    
                default:
                    writer.write ("203 BAD COMMAND\r\n");
                    writer.flush ();
                    socket.close ();
                    break;
            }
            
        }
        catch (IOException ex) {
            Logger.getLogger(AuthSession.class.getName()).log(Level.SEVERE, null, ex);
            try {
                socket.close ();
            } catch (IOException ex1) {
                Logger.getLogger(AuthSession.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
    }
}
