package mainPackage;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import mainPackage.houseFrame.BlueprintController;
import mainPackage.modelClasses.Account;
import mainPackage.modelClasses.Gadget;
import mainPackage.modelClasses.Lamp;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MainWindowController {


    @FXML
    private HBox menuFrame;

    @FXML
    private VBox centerBox;

    @FXML
    private Pane houseFrame, dynamicFrame;

    @FXML
    private GridPane loggedintatusFrame;

    @FXML
    private Button btn1, btn2, btn3;

    @FXML
    private Label exceptionLabel, loggedInLabel;

    private DynamicFrame currentDynamicFrameController;

    public ArrayList<Gadget> gadgetList;
    // When model class Room is added: private ArrayList<Room> roomList;
    // Or:                             public ArrayList<Pane> roomList;

    //Producer-consumer pattern. Thread safe. Add requests to send to server.
    //Maybe have private, with getters
    public BlockingQueue<String> requestsToServer;

    public BlockingQueue<String> requestsFromServer;

    //To notify the JavaFX-thread that updates has arrived from the Server.
    public BooleanProperty doUpdate;

    @FXML
    public void initialize() {

        //Set name of dynamic frame to which the button links
        btn1.setUserData("Test");
        btn2.setUserData("RoomsController");
        btn3.setUserData("testFrame");

        gadgetList = new ArrayList<>();
        requestsToServer = new ArrayBlockingQueue<>(10);
        requestsFromServer = new ArrayBlockingQueue<>(10);

        doUpdate = new SimpleBooleanProperty(false);
        isNotLoggedIn();

        //Until we can get Gadgets from Server:
        gadgetList.add(new Lamp("LampOne", 25, "Kitchen"));
        gadgetList.add(new Lamp("LampTwo", 25, "Kitchen"));

        //Add listener to loggedInAccount object's loggedInAccountProperty
        AccountLoggedin.getInstance().loggedInAccountProperty().addListener(
                new ChangeListener<Account>() {
                    @Override
                    public void changed(ObservableValue<? extends Account> observable, Account oldValue, Account newValue) {
                        try {
                            isLoggedIn();
                        } catch (NullPointerException e) {  //If no account is held by the AccountLoggedin-object, newValue == null
                            isNotLoggedIn();
                        }
                    }
                }
        );
        blueprint();

        //Since requests for updates from the Server is received by another thread than JavaFX, we need a way to notify the
        //JavaFX-Thread to process the new data that has arrived from the server.
        //Could also be done by an int, representing the number of updates to be done (if more arrives while processing)
        doUpdate.addListener(
                new ChangeListener<Boolean>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                        if(doUpdate.getValue()) {

                            //Iin order to have this sun by FX thread, and not the thread issuing the doUpate.setValue(true).
                            Platform.runLater(new Runnable() {
                                @Override public void run() {
                                    //Update UI here
                                    update();
                                }
                            });

                            doUpdate.setValue(false);
                        }
                    }
                }
        );
    }

    public void isLoggedIn() {
        //Code to execute when logged in
        loggedInLabel.setText("Logged in as  " + AccountLoggedin.getInstance().getLoggedInAccount().getName() + "  " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    public void isNotLoggedIn() {
        //Code to execute when  not logged in, ex disable controls
        loggedInLabel.setText("Not logged in");
        //+ Set dynamic frame to log in frame
    }

    public void setCurrentDynamicFrameController(DynamicFrame controller) {
        currentDynamicFrameController = controller;
    }

    @FXML
    void setDynamicFrame(ActionEvent event) {
        exceptionLabel.setText("");

        //Set all buttons to default layout.
        for (Node node : menuFrame.getChildren()) {
            if (node instanceof Button) {
                node.setStyle("");
            }
        }

        //Style the pressed button
        ((Button) event.getSource()).setStyle(
                "-fx-background-color: orange;" +
                        "-fx-border-color:white;");

        //Perform scene change within the dynamicFrame of the MainWindow
        String url = ((Button) event.getSource()).getUserData().toString();

        try {
            dynamicFrame.getChildren().clear();
            dynamicFrame.getChildren().add(FXMLLoader.load(getClass().getResource("dynamicFrames/" + url + ".fxml")));
            //dynamicFrame.setLayoutX(100);
            //dynamicFrame.setLayoutY(0);
            //currentDynamicFrame = url;
        } catch (IOException io) {
            exceptionLabel.setText("Unable to load new scene.");
        } catch (NullPointerException e) {
            exceptionLabel.setText("Unable to load new scene.");
        }
    }

    //Adding blueprint to houseframe window
    public void blueprint(){
        try{
            houseFrame.getChildren().clear();
            houseFrame.getChildren().add(FXMLLoader.load(getClass().getResource("houseFrame/Blueprint.fxml")));
        }catch(IOException e){

        }
    }

    public void setExceptionLabel(String message) { //To set it from dynamic frames.
        exceptionLabel.setText(message);
    }

    //update() should be run by JavaFX-Thread, so should not be invoked by other threads (ex ClientInputThread)
    //update while requestsFromServer is not empty
    public void update() {
        exceptionLabel.setText("");
        //Update houseFrame + invoke update method of currentFrame
        try {
            String request = requestsFromServer.take();
            System.out.println(request);
            String[] commands = request.split(":");

            //Translate the requests according to the LAAS communication protocol:
            switch (commands[0]) {
                case "2": //Result of login attempt
                    if(commands[1].equals("ok")) {
                        //Create account and send it as parameter to: AccountLoggedin.getInstance().setLoggedInAccount(account);
                        String accountID =   commands[3];
                        String systemID =    commands[4];
                        String name =        commands[5];
                        String accessLevel = commands[6];
                        String password =    commands[7];

                        Account a1 = new Account(name, accountID, Integer.parseInt(systemID), accessLevel, password);
                        AccountLoggedin.getInstance().setLoggedInAccount(a1);

                    }
                    else if(commands[1].equals("no")) {
                        exceptionLabel.setText(commands[2]);
                    }else {
                        exceptionLabel.setText("Login failed for unknown reasons");
                    }
                    break;
                case "4": //Gadgets' states has been updated
                    break;
                case "7": //Gadgets info has been updates
                    break;
                case"10": //Users' info has been updated
                    break;
                case "12": //Log has been sent
                    //Cast log scene
                    break;
                case "13": //Exception message from server
                    exceptionLabel.setText(commands[1]);
                    break;
                default:
                    exceptionLabel.setText("Unknown update request received");
            }

        }catch(InterruptedException e) {
            exceptionLabel.setText("Unable to update from server");
        }
        currentDynamicFrameController.updateFrame();

    }

}
