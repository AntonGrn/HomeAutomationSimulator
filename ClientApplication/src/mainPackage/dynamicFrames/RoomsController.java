package mainPackage.dynamicFrames;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import mainPackage.DynamicFrame;
import mainPackage.Main;
import mainPackage.MainWindowController;
import mainPackage.modelClasses.Gadget;
import mainPackage.modelClasses.Room;
import mainPackage.modelClasses.RoomSlider;

import java.util.ArrayList;
import java.util.Set;

public class RoomsController implements DynamicFrame {
    private ObservableList<RoomSlider> listOfRoomPics;

    private Timeline timeLineLeft;
    private Timeline timeLineRight;

    @FXML
    private TableColumn<RoomSlider, Button> clmFive;
    @FXML
    private TableColumn<RoomSlider, Button> clmOne;
    @FXML
    private TableColumn<RoomSlider, Button> clmTwo;
    @FXML
    private TableColumn<RoomSlider, Button> clmThree;
    @FXML
    private TableColumn<RoomSlider, Button> clmFour;
    @FXML
    private TableColumn<Gadget, ImageView> clmType;
    @FXML
    private TableColumn<Gadget, String> clmId;
    @FXML
    private TableColumn<Gadget, ImageView> clmState;
    @FXML
    private Button btnLeftHover, btnRightHover;

    @FXML
    private TableView<RoomSlider> tblViewRooms;
    @FXML
    public TableView tblViewDynamicGadgets;

    @FXML
    public Pane dynamicFrameRooms;

    public void initialize() {
        listOfRoomPics = FXCollections.observableArrayList(RoomSlider.getRoomSliderInstance());

        //update all clients and tables and such, when a request is confirmed from server.
        updateFrame();

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                RoomSlider.getRoomSliderInstance().getBedRoom().setOnAction(e -> {
                    String roomName = ((Button) e.getSource()).getUserData().toString();
                    updateTableView(roomName);
                });
                RoomSlider.getRoomSliderInstance().getKitchen().setOnAction(e -> {
                    String roomName = ((Button) e.getSource()).getUserData().toString();
                    updateTableView(roomName);
                });
                RoomSlider.getRoomSliderInstance().getGarage().setOnAction(e -> {
                    String roomName = ((Button) e.getSource()).getUserData().toString();
                    updateTableView(roomName);
                });
                RoomSlider.getRoomSliderInstance().getToilet().setOnAction(e -> {
                    String roomName = ((Button) e.getSource()).getUserData().toString();
                    updateTableView(roomName);
                });
                RoomSlider.getRoomSliderInstance().getLivingRoom().setOnAction(e -> {
                    String roomName = ((Button) e.getSource()).getUserData().toString();
                    updateTableView(roomName);
                });
            }
        });
    }

    @Override
    public void updateFrame() {
        //Setting properties for RoomSlider tableview.
        clmOne.setCellValueFactory(new PropertyValueFactory<>("bedRoom"));
        clmTwo.setCellValueFactory(new PropertyValueFactory<>("kitchen"));
        clmThree.setCellValueFactory(new PropertyValueFactory<>("garage"));
        clmFour.setCellValueFactory(new PropertyValueFactory<>("toilet"));
        clmFive.setCellValueFactory(new PropertyValueFactory<>("livingRoom"));

        //Setting properties for Gadgets tableview.
        clmType.setCellValueFactory(new PropertyValueFactory<>("typeImage"));
        clmId.setCellValueFactory(new PropertyValueFactory<>("name"));
        clmState.setCellValueFactory(new PropertyValueFactory<>("onOffImage"));

        //add to room tblView, will never be changed
        tblViewDynamicGadgets.getItems().addAll(Main.getMainWindowController().gadgetListTableView);

        //add to gadget tblView
        tblViewRooms.getItems().addAll(listOfRoomPics);
    }

    public void updateTableView(String roomName) {
        ArrayList<Gadget> gadgetList = new ArrayList<>();
        try {
            for (Gadget g : Main.getMainWindowController().gadgetList) {
                if (g.getRoom().equals(roomName)) {
                    gadgetList.add(g);
                }
                tblViewDynamicGadgets.getItems().clear();
                tblViewDynamicGadgets.getItems().addAll(gadgetList);
            }
        } catch (Exception ex) {
            Main.getMainWindowController().exceptionLabel.setText("Could not load gadgets..");
        }
    }

    public void scrollLeft() {
        Set<Node> node = tblViewRooms.lookupAll(".scroll-bar");
        for (final Node nodeFound : node) {
            if (nodeFound instanceof ScrollBar) {
                ScrollBar scrollBar = (ScrollBar) nodeFound;
                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    timeLineLeft = new Timeline(new KeyFrame(Duration.millis(30), e -> {
                        if (scrollBar.getValue() >= scrollBar.getMin()) {
                            scrollBar.setValue(scrollBar.getValue() - 4);
                        }
                    }));
                    timeLineLeft.setCycleCount(Animation.INDEFINITE);
                    timeLineLeft.play();
                }
            }
        }
    }

    public void scrollRight() {
        Set<Node> node = tblViewRooms.lookupAll(".scroll-bar");
        for (final Node nodeFound : node) {
            if (nodeFound instanceof ScrollBar) {
                ScrollBar scrollBar = (ScrollBar) nodeFound;
                if (scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    timeLineRight = new Timeline(new KeyFrame(Duration.millis(30), e -> {
                        if (scrollBar.getValue() <= scrollBar.getMax()) {
                            scrollBar.setValue(scrollBar.getValue() + 4);
                        }
                    }));
                    timeLineRight.setCycleCount(Animation.INDEFINITE);
                    timeLineRight.play();
                }
            }
        }
    }

    public void stopScrollLeft() {
        timeLineLeft.stop();
    }

    public void stopScrollRight() {
        timeLineRight.stop();
    }


}
