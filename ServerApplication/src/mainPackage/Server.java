package mainPackage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public BlockingQueue<ClientRequest> serverRequest;

    //Maybe add a queue for serverOutputs to clients, which method outputToClient could scan from a new thread

    private final List<Client> activeClients;

    private final Object lockObject1;
    private final Object lockObject2;

    private static Server instance = null;

    private Server() {
        serverRequest = new ArrayBlockingQueue<>(10);
        //If synchronizing ordinary list with synchronized blocks doesn't work; try Vector or CopyOnWriteArrayList or Set
        activeClients = Collections.synchronizedList(new ArrayList<>());
        lockObject1 = new Object();
        lockObject2 = new Object();
    }

    public static Server getInstance() {
        if (instance == null) {
            instance = new Server();
        }
        return instance;
    }

    public void run() {
        //Deploy methods on threads.
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    acceptClientConnections();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                processClientRequests();
            }
        });

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //Invoked by t1
    private void acceptClientConnections() throws IOException {
        synchronized (lockObject1) {

            // Thread pool for better control of the threads being launched.
            ExecutorService executor = Executors.newFixedThreadPool(10);

            // The server is listening on a port
            ServerSocket server = new ServerSocket(8081);

            System.out.println("Server running...");

            while (true) {

                Socket clientConnection = null;
                Client client = null;

                try {
                    // socket object to receive incoming client requests
                    clientConnection = server.accept();

                    // obtaining input and output streams
                    DataInputStream input = new DataInputStream(clientConnection.getInputStream());
                    DataOutputStream output = new DataOutputStream(clientConnection.getOutputStream());

                    client = new Client("null", 0, false, clientConnection, input, output);

                    // Assigning new thread for client
                    executor.submit(new ClientThread(client));

                } catch (Exception e) {
                    clientConnection.close();
                    e.printStackTrace();
                    System.out.println("Exception in acceptClients");
                }
            }
        }
    }

    //Invoked by acceptClientConnections() -> t1
    private void addClient(Client client) {
        synchronized (activeClients) {
            activeClients.add(client);
            //LOG PURPOSE
            iterateActiveClients();
        }
    }

    //Invoked by ClientThreads
    public void removeClient(Client clientToRemove) {
        synchronized (activeClients) {

            for (int i = 0; i < activeClients.size(); i++) {
                if (activeClients.get(i).getSocket().equals(clientToRemove.getSocket())) {
                    try {
                        activeClients.get(i).getSocket().close();
                        activeClients.get(i).getOutput().close();
                        activeClients.get(i).getInput().close();
                    } catch (IOException e) {
                        //e.printStackTrace();
                        System.out.println("Remove client error: Unable to close all resources for client " + activeClients.get(i).getAccountID());
                    } finally {
                        activeClients.remove(i);
                    }
                }
            }
            //LOG PURPOSE
            iterateActiveClients();
        }
    }

    // Server log purpose. Standard output -> Server log
    public void iterateActiveClients() {
        synchronized (activeClients) {
            String clients = "=========== " + " Active clients ======== " + Thread.currentThread().getName() + "\n";
            if (activeClients.size() == 0) {
                clients = clients.concat("Zero");
            } else {
                for (Client client : activeClients) {
                    clients = clients.concat(client.getAccountID() + " " + (client.isAdmin() ? "[Admin]" : "[Non-admin]") + " " + client.getSocket().getInetAddress() + "\n");
                }
            }
            System.out.println(clients + "\n==========================================");
        }
    }

    //Invoked by ClientThread to assure account is logged in to proceed
    public Client login(ClientRequest loginRequest) {
        synchronized (lockObject2) { //Same lock as processClientRequest, to assure no simultanous operations on DB
            Client client = loginRequest.getClient();

            String[] commands = loginRequest.getRequest().split(":");

            if (commands[0].equals("1")) { //If it is a login request

                try {
                    //Try to log in with [1] accountID and [2] password
                    String[] result = DB.getInstance().login(commands[1], commands[2]); //login() throws Exception

                    String accountID = result[0];
                    String systemID = result[1];
                    String name = result[2];
                    String admin = result[3];
                    String password = result[4];

                    client = new Client(
                            accountID, Integer.parseInt(systemID), admin.equals("1"), loginRequest.getClient().getSocket(), loginRequest.getClient().getInput(), loginRequest.getClient().getOutput());

                    //Add client to list of active clients
                    addClient(client);

                    String clientOutput = String.format("%s%s%s%s%s%s%s%s%s%s",
                            "2:ok:null:", accountID, ":", systemID, ":", name, ":", admin, ":", password);

                    outputToClients(true, false, (clientOutput), loginRequest.getClient().getSocket(), loginRequest.getClient().getSystemID());
                } catch (Exception e) {
                    String exceptionMessage = e.getMessage();
                    try {
                        client.getOutput().writeUTF("2:no:".concat(exceptionMessage));
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
            return client;
        }
    }

    //Process requests from logged in clients
    public void processClientRequests() {
        ClientRequest clientRequest;
        while (true) {
            try {
                //Waits here until there are any requests
                clientRequest = serverRequest.take();

                synchronized (lockObject2) {
                    //Process the request according to LAAS communication protocol
                    String commands[] = clientRequest.getRequest().split(":");

                    switch (commands[0]) {
                        case "1": //Login request. If user can reach this; user is already logged in.
                            outputToClients(true, false, "15:Already logged in", clientRequest.getClient().getSocket(), clientRequest.getClient().getSystemID());
                            break;
                        case "3": //Request to update a gadget's state
                            updateGadgetState(commands, clientRequest.getClient());
                            break;
                        case "5": //Individual request for all gadgets info
                            outputGadgets(clientRequest.getClient(), false, true);
                            break;
                        case "6": //Request to edit gadget's info
                            editGadgetsInfo(commands, clientRequest.getClient());
                            break;
                        case "7a": //Request to add a gadget
                            addGadget(commands, clientRequest.getClient());
                            break;
                        case "7b": //Request to delete a gadget
                            deleteGadget(commands, clientRequest.getClient());
                            break;
                        case "9": // Individual request for all users info
                            if(clientRequest.getClient().isAdmin()) {
                                outputAccounts(clientRequest.getClient(), true);
                            }
                            break;
                        case "10": // Request to edit user info
                            editAccountsInfo(commands, clientRequest.getClient());
                            break;
                        case "11a": // Request add a user
                            addAccount(commands, clientRequest.getClient());
                            break;
                        case "11b": // Request to delete a user
                            deleteAccount(commands, clientRequest.getClient());
                            break;
                        case "13": //Log request
                            outputLogs(clientRequest.getClient());
                            break;
                        default:
                            outputToClients(true, false, "15:Invalid server request", clientRequest.getClient().getSocket(), clientRequest.getClient().getSystemID());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateGadgetState(String[] commands, Client fromClient) {
        //Note: In the database; all gadget states are integers; heat=value, others=boolean(1/0)
        int systemID = fromClient.getSystemID();
        int gadgetID = Integer.parseInt(commands[1]);
        int newState = Integer.parseInt(commands[2]);
        try {
            DB.getInstance().setGadgetState(systemID, gadgetID, newState);
            //output to clients
            outputGadgets(fromClient, true, false);
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
        //Add gadget usage log
        try {
            addLog(fromClient, gadgetID, newState);
        } catch (Exception e) {
            System.out.println("Adding logs error " + e.getMessage());
        }
    }

    private void addGadget(String[] commands, Client fromClient) {
        String type = commands[1];
        String name = commands[2];
        String room = commands[3];
        String consumption = commands[4];
        try {
            DB.getInstance().addGadget(fromClient.getSystemID(), type, name, room, consumption);
            outputGadgets(fromClient, false, false);
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    private void deleteGadget(String[] commands, Client fromClient) {
        int systemID = fromClient.getSystemID();
        int gadgetID = Integer.parseInt(commands[1]);
        try {
            DB.getInstance().deleteGadget(systemID, gadgetID);
            outputGadgets(fromClient, false, false);
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    private void editGadgetsInfo(String[] commands, Client fromClient) {
        int systemID = fromClient.getSystemID();
        String type = commands[1];
        int gadgetID = Integer.parseInt(commands[2]);
        String name = commands[3];
        String room = commands[4];
        int consumption = Integer.parseInt(commands[5]);
        try {
            DB.getInstance().editGadgetsInfo(systemID, type, gadgetID, name, room, consumption);
            outputGadgets(fromClient, false, false);
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    // Produces output commands 4 and 8 of LAAS communication protocol
    private void outputGadgets(Client fromClient, boolean onlyGadgetStates, boolean onlyToIndividual) {
        String clientOutput = onlyGadgetStates ? "4:" : "8:";
        try {
            ArrayList<String[]> gadgetList = DB.getInstance().getGadgets(fromClient.getSystemID(), onlyGadgetStates);
            // If there were no sql results
            if (gadgetList.size() < 1) {
                clientOutput = clientOutput.concat("null");
            } else {
                clientOutput = clientOutput.concat("notnull:");
                // Send gadget state info ("4:XXX")
                if (onlyGadgetStates) {
                    for (int i = 0; i < gadgetList.size(); i++) {
                        String gadgetID = gadgetList.get(i)[1];
                        String state = gadgetList.get(i)[2];

                        clientOutput = String.format("%s%s%s%s",
                                clientOutput, gadgetID, ":", state);

                        if (i == gadgetList.size() - 1) {
                            clientOutput = clientOutput.concat(":null");
                        } else {
                            clientOutput = clientOutput.concat(":next:");
                        }
                    }
                    // Sends full gadgets info ("8:XXX")
                } else {
                    for (int i = 0; i < gadgetList.size(); i++) {
                        String type = gadgetList.get(i)[0];
                        String gadgetID = gadgetList.get(i)[1];
                        String name = gadgetList.get(i)[2];
                        String room = gadgetList.get(i)[3];
                        String state = gadgetList.get(i)[4];
                        String consumption = gadgetList.get(i)[5];

                        clientOutput = String.format("%s%s%s%s%s%s%s%s%s%s%s%s",
                                clientOutput, type, ":", gadgetID, ":", name, ":", room, ":", state, ":", consumption);

                        if (i == gadgetList.size() - 1) {
                            clientOutput = clientOutput.concat(":null");
                        } else {
                            clientOutput = clientOutput.concat(":next:");
                        }
                    }
                }
            }
            outputToClients(onlyToIndividual, false, clientOutput, fromClient.getSocket(), fromClient.getSystemID());
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    //String accountID, int systemID, String name, String admin, String password
    private void addAccount(String[] commands, Client fromClient) {
        String accountID = commands[1];
        int systemID = fromClient.getSystemID();
        String name = commands[2];
        String admin = commands[3];
        String password = commands[4];
        try {
            DB.getInstance().addAccount(accountID, systemID, name, admin, password);
            outputAccounts(fromClient, false);
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    private void deleteAccount(String[] commands, Client fromClient) {
        int systemID = fromClient.getSystemID();
        String accountID = commands[1];
        try {
            DB.getInstance().deleteAccount(systemID, accountID);
            outputAccounts(fromClient, false);
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    private void editAccountsInfo(String[] commands, Client fromClient) {
        int systemID = fromClient.getSystemID();
        String accountID = commands[1];
        String name = commands[2];
        String admin = commands[3].equals("1") ? "1" : "0";
        String password = commands[4];
        try {
            DB.getInstance().editAccountsInfo(systemID, accountID, name, admin, password);
            outputAccounts(fromClient, false);
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    private void outputAccounts(Client fromClient, boolean onlyToIndividual) {
        try{
            ArrayList<String[]> accountList = DB.getInstance().getAccounts(fromClient.getSystemID());
            String clientOutput = "12:";

            for (int i = 0; i < accountList.size(); i++) {
                String accountID = accountList.get(i)[0];
                String name = accountList.get(i)[1];
                String password = accountList.get(i)[2];
                String admin = accountList.get(i)[3];

                clientOutput = String.format("%s%s%s%s%s%s%s%s",
                        clientOutput, accountID, ":", name, ":", password, ":", admin);

                if (i == accountList.size() - 1) {
                    clientOutput = clientOutput.concat(":null");
                } else {
                    clientOutput = clientOutput.concat(":next:");
                }
            }
            outputToClients(onlyToIndividual, true, clientOutput, fromClient.getSocket(), fromClient.getSystemID());
        }catch(Exception e) {
            outputToClients(true, true, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    private void addLog(Client fromClient, int gadgetID, int newState) throws Exception {
        String items[] = new String[4];
        try {
            //Get the data needed to form a user friendly log message
            items = DB.getInstance().getLogCreationData(fromClient.getAccountID(), gadgetID);
            String accountName = items[0];
            String gadgetName = items[1];
            String gadgetType = items[2];
            String gadgetRoom = items[3];

            //Form a user friendly log message
            String logMessage = accountName;
            switch (gadgetType) {
                case "Heat":
                    logMessage = String.format("%s%s%s%s%s", logMessage, " turned heat to ", newState, " C in ", gadgetRoom);
                    break;
                case "Door":
                    logMessage = String.format("%s%s%s", logMessage, (newState == 1 ? " locked " : " unlocked "), gadgetName);
                    break;
                default:
                    logMessage = String.format("%s%s%s%s%s%s", logMessage, " turned ", (newState == 1 ? "on " : "off "), gadgetName, " in ", gadgetRoom);
                    break;
            }
            DB.getInstance().addLog(fromClient.getSystemID(), logMessage);
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    //Produces command 14
    private void outputLogs(Client fromClient) {
        try {
            ArrayList<String[]> logs = DB.getInstance().getLogs(fromClient.getSystemID());

            //Form the output string according to LAAS protocol: 12:timestamp:log:next/null
            String clientOutput = "14:";
            for (int i = 0; i < logs.size(); i++) {

                // NOTE: timestamp contains colon (ex 18:34:15), which is the break mark for commands in LAAS protocol,
                // so we first exchange the colons in the timestamp with "&"
                logs.get(i)[0] = logs.get(i)[0].replace(":", "&");
                //Remove 'seconds' from timestamp        start                       end  (removing ':seconds')
                logs.get(i)[0] = logs.get(i)[0].substring(0, logs.get(i)[0].length() - 3);

                //                                                       timestamp           log message
                clientOutput = String.format("%s%s%s%s", clientOutput, logs.get(i)[0], ":", logs.get(i)[1]);
                //if there are more logs to read, or not
                if (i == logs.size() - 1) {
                    clientOutput = clientOutput.concat(":null");
                } else {
                    clientOutput = clientOutput.concat(":next:");
                }
            }
            outputToClients(true, false, clientOutput, fromClient.getSocket(), fromClient.getSystemID());
        } catch (Exception e) {
            outputToClients(true, false, "15:".concat(e.getMessage()), fromClient.getSocket(), fromClient.getSystemID());
        }
    }

    //The final output operation
    private void outputToClients(boolean onlyToIndividual, boolean onlyToAdmins, String message, Socket connection, int systemID) {
        synchronized (activeClients) {
            //System.out.println("LIST SIZE = " + activeClients.size());
            try {
                if (onlyToIndividual) {
                    for (Client client : activeClients) {
                        if (client.getSocket().equals(connection)) {
                            if ((onlyToAdmins & client.isAdmin()) || !onlyToAdmins) {
                                try {
                                    client.getOutput().writeUTF(message);
                                } catch (IOException e) {
                                    System.out.println("Unable to write to: " + client.getAccountID());
                                }
                            }
                        }
                    }
                } else {
                    for (Client client : activeClients) {
                        if (client.getSystemID() == systemID) {
                            if ((onlyToAdmins & client.isAdmin()) || !onlyToAdmins) {
                                client.getOutput().writeUTF(message);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
