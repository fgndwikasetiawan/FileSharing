package filesharingserver;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileSharingServer {

    private static String rootPath;
    private static HashMap <String, ClientThread> clientThreadMap;
    
    //Accessor dari clientThreadMap
        public static void addClientThread (String id, ClientThread client) {
            clientThreadMap.put(id, client);
        }
        public static ClientThread getClientThread (String id) {
            return clientThreadMap.get(id);
        }
        public static void removeClientThread (String id) {
            clientThreadMap.remove(id);
        }
        public static boolean isClientConnected (String id) {
            return clientThreadMap.containsKey (id);
        }
        public static ArrayList <ClientThread> getClientThreadsList () {
            ArrayList <ClientThread> list = new ArrayList <> ();
            for (Entry <String, ClientThread> entry : clientThreadMap.entrySet()) {
                list.add (entry.getValue ());
            }
            return list;
        }
    //Accessor dari rootPath
        public static String getRootPath () {
            return rootPath;
        }
    
    public static void main(String[] args) {
        clientThreadMap = new HashMap <> ();
        //Persiapan: input dan pengecekan direktori root
        Scanner s = new Scanner(System.in);
        File rootDir;
        while(true) {
            try {
                System.out.print("Server root directory: ");
                rootPath = s.nextLine();
                rootDir = new File(rootPath);
                if (!rootDir.isDirectory()) {
                    continue;
                }
                break;
            }
            catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }
        //selesai input direktori root
    
        System.out.println ("Starting server..");
        
        //menerima koneksi notif di thread lain
        NotifServer ns = new NotifServer ();
        ns.start();
        
        //menerima koneksi command
        try (ServerSocket servsock = new ServerSocket (1100))
        {
            while (true) {
                Socket clientsock = servsock.accept ();
                AuthSession aus = new AuthSession (clientsock, AuthSession.ConnectionMode.COMMAND);
                aus.start ();
            }
        }
        catch (Exception ex) {
            Logger.getLogger(AuthSession.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
}
