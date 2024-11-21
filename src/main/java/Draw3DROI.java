import ij.WindowManager;
import ij.gui.Roi;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.display.DataView;
import net.imagej.display.ImageDisplayService;
import net.imagej.display.OverlayService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.app.AppService;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

@Plugin(type = Command.class, headless = false, menuPath = "Plugins>Draw 3D ROI", initializer = "init")
public class Draw3DROI extends InteractiveCommand {

    @Parameter
    protected LogService logService;

    @Parameter
    protected StatusService statusService;

    @Parameter
    protected DatasetService datasetService;

    @Parameter
    protected ImageDisplayService imageDisplayService;

    @Parameter
    protected UIService uiService;

    @Parameter(label="Display view:",choices={"XY", "XZ", "YZ"}, style="radioButtonHorizontal", persist=false, callback="viewChoice")
    private String viewChoiceSelection;

    @Parameter(label="Add view-specific ROI.", callback = "addROI")
    private Button addROIButton;

    @Parameter
    protected Dataset inputImage;

    //Need to update the display of the data, without copying or duplicating the data. Possibilities: ImageDisplayService, ImageDisplay
//    @Parameter
//    protected DatasetView inputView;

    @Parameter(type = ItemIO.OUTPUT)
    protected Dataset outputMask;


    private DataView currentDisplayView;
    protected Display currentDisplay;
    private RandomAccessibleInterval currentView;
    private String standardMessage = "Draw an ROI to define bounds from this perspective, then press OK.";
    private int xIndex, yIndex, zIndex;
    private long xDim, yDim, zDim;
    private Roi xyROI, xzROI, yzROI;

//    protected boolean showDialog(String title){
//        DialogPrompt.Result userSelection = uiService.showDialog(standardMessage,title, DialogPrompt.MessageType.PLAIN_MESSAGE, DialogPrompt.OptionType.OK_CANCEL_OPTION);
//        if (userSelection == DialogPrompt.Result.CANCEL_OPTION)
//                return false;
//        return true;
//    }

    @Override
    public void run(){
    }

    protected void viewChoice(){
        switch (viewChoiceSelection){
            case "XY":
                currentView = inputImage;
                break;
            case "XZ":
                currentView = Views.moveAxis(inputImage, yIndex, zIndex);
                break;
            case "YZ":
                currentView = Views.moveAxis(inputImage, xIndex, zIndex);
                break;
        }
        updateDisplay();
    }

    protected void addROI(){
        switch (viewChoiceSelection){
            case "XY":
                xyROI = WindowManager.getCurrentImage().getRoi();
                break;
            case "XZ":
                xzROI = WindowManager.getCurrentImage().getRoi();
                break;
            case "YZ":
                yzROI = WindowManager.getCurrentImage().getRoi();
                break;
        }
        statusService.showStatus("ROI registered");
        WindowManager.getCurrentImage().deleteRoi();
    }

    private void updateDisplay(){
        currentDisplay.close();
        currentDisplayView.dispose();
        currentDisplayView = imageDisplayService.createDataView(datasetService.create(currentView));
        currentDisplay = imageDisplayService.getDisplayService().createDisplay(currentDisplayView);
    }

    protected void init() {
        xIndex = inputImage.dimensionIndex(Axes.X);
        yIndex = inputImage.dimensionIndex(Axes.Y);
        zIndex = inputImage.dimensionIndex(Axes.Z);

        xDim = inputImage.dimension(Axes.X);
        yDim = inputImage.dimension(Axes.Y);
        zDim = inputImage.dimension(Axes.Z);

        currentView = inputImage;


        currentDisplayView = imageDisplayService.createDataView(datasetService.create(currentView));
        currentDisplay = imageDisplayService.getDisplayService().createDisplay(currentDisplayView);
    }


//            uiService.show(Views.moveAxis(inputImage, xIndex, zIndex));
//
//        ij.WindowManager.getCurrentImage().getRoi();
    //final Overlay o = overlayService.getActiveOverlay(display); //to get ROI?

    /*
    Get intersection procedure:
    1. Get 3 2D images as masks for each cross-section
    2. LoopBuilder parallel through every pixel
    3. For each, convert 3D point to corresponding 2D point in XY, then compare to XY mask
    4. Repeat for XZ and YZ
    5. If meets all three, add to output mask image
     */
}
