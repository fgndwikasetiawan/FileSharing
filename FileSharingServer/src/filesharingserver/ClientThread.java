package filesharingserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ClientThread extends Thread {
    private final Socket commandSocket;
    private Socket notifSocket;
    private final String id;
    private final String key;
    
    public ClientThread (Socket commandSocket, String id, String key) {
        this.commandSocket = commandSocket;
        this.id = id;
        this.key = key;
        notifSocket = null;
    }
    
    public void setNotifSocket (Socket socket) {
        notifSocket = socket;
    }
    
    public String getClientId () {
        return id;
    }
    
    public String getClientKey () {
        return key;
    }
    
    public boolean isNotifReady () {
        return notifSocket != null;
    }
    
    public void sendMessage (String message) throws Exception {
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter (notifSocket.getOutputStream ()));
        writer.write (message);
        writer.flush ();
    }
    
    public String sendMessageWithReply (String message) throws Exception {
        BufferedReader reader = new BufferedReader (new InputStreamReader (notifSocket.getInputStream ()));
        sendMessage (message);
        return reader.readLine();
    }
    
    
//-----------------------------------------------------------------------------------------------------------------------//
//------------------------FUNGSI RUN-------------------------------------------------------------------------------------//
//-----------------------------------------------------------------------------------------------------------------------//
    @Override
    public void run () {
        try {
            BufferedWriter writer = new BufferedWriter (new OutputStreamWriter (commandSocket.getOutputStream ()));
            BufferedReader reader = new BufferedReader (new InputStreamReader (commandSocket.getInputStream ()));
            Scanner s;
            while (true) {
                s = new Scanner (reader.readLine ());
                s.useDelimiter(" ");
                try {
                    String command = s.next ();
                    System.out.println (id + ": " + command);
                    switch (command) {
                        case "LIST":
                            ArrayList <String> idList = new ArrayList <> ();
                            for (ClientThread c : FileSharingServer.getClientThreadsList ()) {
                                if (c.isNotifReady () )
                                    idList.add (c.getClientId ());
                            }
                            writer.write ("300 " + idList.size () + "\r\n");
                            for (String cid : idList) {
                                writer.write (cid + "\r\n");
                            }
                            writer.flush ();
                            break;
                            
                        case "SEND":
                            try {
                                s.reset();
                                //baca parameter (SEND [filename] [filesize] [jumlah penerima])
                                String filename = s.findInLine (Pattern.compile ("\\\".*\\\""));
                                System.out.println (filename);
                                long filesize = s.nextLong ();
                                System.out.println (filesize);
                                int num = s.nextInt ();
                                System.out.println (num);
                                ArrayList <ClientThread> recipientList = new ArrayList <> ();
                                //Kirim RCFR ke semua calon recipient (RCFR [id pengirim] [filename])
                                for (int i=0; i<num; i++) {
                                    String recId = reader.readLine ();
                                    if (FileSharingServer.isClientConnected (recId)) {
                                        ClientThread recipient = FileSharingServer.getClientThread (recId);
                                        try {
                                            Scanner replyScan = new Scanner ( recipient.sendMessageWithReply("RCFR " + id + "\r\n") );                                           
                                            //kalau dibales 400 OK (diterima) tambahin ke recipientList
                                            if (replyScan.nextInt () == 400) {
                                                recipientList.add (recipient);
                                            }
                                        }
                                        catch (Exception ex) {}
                                    }
                                }
                                
                                //Kalau ada yang mau nerima
                                if (recipientList.size () > 0) {
                                    String storedFilename = id + System.currentTimeMillis();
                                    SharedFileServer sfs;
                                    try {
                                        sfs = new SharedFileServer (storedFilename, FileSharingServer.getRootPath() + filename, id, filesize, recipientList);
                                        sfs.start ();
                                    }
                                    catch (Exception ex) {
                                        Logger.getLogger(AuthSession.class.getName()).log(Level.SEVERE, null, ex);
                                        writer.write ("404 SERVER ERROR\r\n");
                                        writer.flush ();
                                        break;
                                    }
                                    //Kirim daftar client yang siap nerima ke pengirim
                                    writer.write ("402 " + recipientList.size () + "\r\n");
                                    for (ClientThread ct : recipientList) {
                                        writer.write (ct.getClientId () + "\r\n");
                                    }
                                    //Kirim port file ke pengirim
                                    writer.write (sfs.getPort() + "\r\n");
                                    writer.flush ();
                                    
                                    //Kirim port file ke penerima (OPEN [id penerima] [filename] [port])
                                    for (ClientThread ct : recipientList) {
                                        ct.sendMessage ("OPEN " + id + " " + filename + " " + sfs.getPort() + "\r\n");
                                    }
                                    
                                    //Sekarang harusnya pengirim dan penerima bikin koneksi ke port yang dikirim
                                    //Command selesai, cerita pindah ke SharedFileServer
                                }
                                //Kalau gak ada yang mau nerima
                                else {
                                    writer.write ("403 NO RECIPIENT\r\n");
                                    writer.flush ();
                                }
                            }
                            catch (Exception ex) {
                                writer.write ("405 BAD SEND COMMAND\r\n");
                                writer.flush ();
                            }
                            break;
                            
                        case "CAST":
                            break;
                            
                        default:
                            writer.write("900 UNKNOWN COMMAND\r\n");
                            writer.flush();
                    }
                }
                catch (NoSuchElementException ex) {
                    Logger.getLogger(AuthSession.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        catch (IOException ex) {
            System.out.println (id + ": CONNECTION CLOSED");
        }
        
        try {
            commandSocket.close ();
            if (notifSocket != null)
                notifSocket.close ();
        } 
        catch (IOException ex) {
            Logger.getLogger(ClientThread.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        FileSharingServer.removeClientThread(this.id);
    }
//------------------------------------------------------------------------------------------------------------------//
    
}
