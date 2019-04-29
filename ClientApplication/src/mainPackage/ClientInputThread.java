package mainPackage;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

public class ClientInputThread extends Thread {

    private final Socket socket;
    private final DataInputStream input;

    public ClientInputThread(Socket socket, DataInputStream input) {
        this.socket = socket;
        this.input = input;
    }

    @Override
    public void run() {
        String messageFromServer;

        while (true) {
            try {
                //Read data from server
                messageFromServer = input.readUTF();
                System.out.println("Message received to inputThread " + Thread.currentThread());
                //Send data from server to queue: requestsFromServer
                Main.getMainWindowController().requestsFromServer.put(messageFromServer);
                //Notify JavaFX-thread to update
                Main.getMainWindowController().doUpdate.setValue(true);
            } catch (InterruptedException e) {
                closeResources();
                break;
            } catch (SocketException e) {
                System.out.println("No connection with server");
                closeResources();
                break;
            } catch (EOFException e) {
                System.out.println("No connection with server");
                closeResources();
                break;
            }catch (IOException e) {
                e.printStackTrace();
                closeResources();
                break;
            }
        }
    }

    private void closeResources() {
        try {
            socket.close();
            input.close();
            System.out.println("Input Thread closed");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
