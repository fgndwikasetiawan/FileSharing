package filesharingserver;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NotifServer extends Thread {
    @Override 
    public void run () {
        try (ServerSocket servsock = new ServerSocket (1101))
        {
            while (true) {
                Socket clientsock = servsock.accept ();
                AuthSession aus = new AuthSession (clientsock, AuthSession.ConnectionMode.NOTIF);
                aus.start ();
            }
        }
        catch (Exception ex) {
            Logger.getLogger(AuthSession.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
}
