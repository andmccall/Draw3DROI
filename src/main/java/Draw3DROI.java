import ij.WindowManager;
import ij.gui.Roi;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.DatasetView;
import net.imagej.display.ImageDisplayService;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.special.computer.Computers;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.exception.InvalidDimensionsException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.display.Display;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

@Plugin(type = Command.class, headless = false, menuPath = "Plugins>Draw 3D ROI", initializer = "init")
public class Draw3DROI< T extends RealType< T > > extends InteractiveCommand {

    @Parameter
    protected StatusService statusService;

    @Parameter
    protected LogService logService;

    @Parameter
    protected DatasetService datasetService;

    @Parameter
    protected ImageDisplayService imageDisplayService;

    @Parameter
    protected UIService uiService;

    @Parameter
    protected OpService ops;

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
    private final String msg =
            "Set any ROI selection for each perspective, then click Create 3D mask." ;

    @Parameter(label="Display perspective:",choices={"Front (XY)", "Top (XZ)", "Left (YZ)"}, style="radioButtonHorizontal", persist=false, callback="viewChoice")
    private String viewChoiceSelection;

    @Parameter(label="Projection method:", persist = false, callback = "viewChoice")
    private projectionMethods projectionChoice;

    @Parameter(label="Set perspective's ROI.", callback = "setROI")
    private Button setROIButton;

    @Parameter(label="Get perspective's ROI.", callback = "getROI")
    private Button getROIButton;

    @Parameter(label="Reset perspective's ROI.", callback = "resetROI")
    private Button resetROIButton;

    @Parameter(label="Preview?", persist = false)
    private boolean preview;

    @Parameter(label="Create 3D mask", callback = "exportMask")
    private Button createMaskButton;

    @Parameter
    protected ImgPlus<T> inputImage;

    @Parameter
    protected DatasetView inputView;

    @Parameter(type = ItemIO.OUTPUT)
    protected Dataset outputMask;

    protected enum projectionMethods{
        NONE(null),
        MAX(Ops.Stats.Max.class),
        MEAN(Ops.Stats.Mean.class),
        MEDIAN(Ops.Stats.Median.class),
        VARIANCE(Ops.Stats.Variance.class);

        Class projectorOp;

        projectionMethods(Class projectorOp){
            this.projectorOp = projectorOp;
        }
    }

    private String imageName;
    private DatasetView currentDisplayView;
    protected Display currentDisplay;
    private ImgPlus<T> currentXYView;
    private Img<T> previewMask;
    private Img currentView;
    private int xIndex, yIndex, zIndex, chIndex;
    private int previewChIndex;
    private long xDim, yDim, zDim;
    private Roi xyROI, xzROI, zyROI;
    private boolean changes;


    @Override
    public void run(){
        //command is run exclusively interactively
    }

    @Override
    public void preview(){
        if(!preview) {
            currentXYView = inputImage;
            viewChoice();
            return;
        }
        if(changes) {
            generatePreviewMask();
        }
        showPreviewMask();
        viewChoice();
    }

    //This doesn't seem to do anything yet.
    @Override
    public void cancel(){
        if(currentDisplay != null) currentDisplay.close();
        if(currentDisplayView != null) currentDisplayView.dispose();
    }

    protected void viewChoice(){
        switch (viewChoiceSelection){
            case "Front (XY)":
                currentView = ImgView.wrap(currentXYView);
                break;
            case "Top (XZ)":
                currentView = ImgView.wrap(Views.permute(currentXYView, yIndex, zIndex));
                break;
            case "Left (YZ)":
                currentView = ImgView.wrap(Views.permute(currentXYView, xIndex, zIndex));
                break;
        }
        if(projectionChoice != projectionMethods.NONE) {
            currentView = zProject(currentView, projectionChoice.projectorOp);
        }
        updateDisplay();
    }


    //final Overlay o = overlayService.getActiveOverlay(display); //will try and compare
    protected void setROI(){
        Roi inputROI = WindowManager.getCurrentImage().getRoi();
        if(inputROI == null){
            logService.warn("No ROI found");
            return;
        }
        switch (viewChoiceSelection){
            case "Front (XY)":
                xyROI = inputROI;
                break;
            case "Top (XZ)":
                xzROI = inputROI;
                break;
            case "Left (YZ)":
                zyROI = inputROI;
                break;
        }
        changes = true;
        preview();
        statusService.showStatus("ROI registered");
        WindowManager.getCurrentImage().deleteRoi();
    }

    protected void getROI(){
        if(WindowManager.getCurrentImage().getRoi() != null){
            DialogPrompt.Result result = uiService.showDialog("This will overwrite your current ROI, continue?", "Warning", DialogPrompt.MessageType.WARNING_MESSAGE, DialogPrompt.OptionType.OK_CANCEL_OPTION);
            if (result == DialogPrompt.Result.CANCEL_OPTION)
                return;
        }
        switch (viewChoiceSelection){
            case "Front (XY)":
                WindowManager.getCurrentImage().setRoi(xyROI);
                break;
            case "Top (XZ)":
                WindowManager.getCurrentImage().setRoi(xzROI);
                break;
            case "Left (YZ)":
                WindowManager.getCurrentImage().setRoi(zyROI);
                break;
        }
    }

    protected void resetROI(){
        switch (viewChoiceSelection){
            case "Front (XY)":
                xyROI = new Roi(0,0,xDim,yDim);
                break;
            case "Top (XZ)":
                xzROI = new Roi(0,0,xDim,zDim);
                break;
            case "Left (YZ)":
                zyROI = new Roi(0,0,zDim,yDim);
                break;
        }
        changes = true;
        preview();
        statusService.showStatus("ROI reset");
    }


    protected Img zProject(Img input, Class projectionOp) {
        long[] projectedDimensions = new long[input.numDimensions() - 1];

        int i = 0;

        for (int d = 0; d < input.numDimensions(); d++) {
            if (d != zIndex) {
                projectedDimensions[i] = input.dimension(d);
                i++;
            }
        }

        Img projection;
        if(projectionChoice == projectionMethods.MAX){
            projection = input.factory().create(new FinalDimensions(projectedDimensions));
        }
        else{
            projection = ops.create().img(new FinalDimensions(
                    projectedDimensions),new FloatType());
        }

        UnaryComputerOp maxOp = Computers.unary(ops, projectionOp, projection.firstElement(), input);
        ops.transform().project(projection, input, maxOp, zIndex);
        return projection;
    }

    protected void showPreviewMask(){
        if(chIndex != -1) {
            currentXYView = inputImage.copy();
        }
        else {
            currentXYView = ImgPlus.wrap(ImgView.wrap(Views.addDimension(inputImage, 0, 0)));
        }

        currentXYView = ImgPlus.wrap(ImgView.wrap(Views.concatenate(previewChIndex, currentXYView, previewMask)));
        currentXYView.axis(previewChIndex).setType(Axes.CHANNEL);
    }

    protected void generatePreviewMask(){
        statusService.showStatus("Generating mask.");
        final float maxValue = (float)inputImage.firstElement().getMaxValue();

        if(chIndex != -1) {
            currentXYView = inputImage.copy();
            previewChIndex = chIndex;
        }
        else {
            currentXYView = ImgPlus.wrap(ImgView.wrap(Views.addDimension(inputImage, 0, 0)));
            previewChIndex = inputImage.numDimensions();
        }

        long[] min = new long[currentXYView.numDimensions()];
        long[] max = new long[currentXYView.numDimensions()];
        for(int d = 0; d < min.length; ++d){
            min[d] = currentXYView.min(d);
            max[d] = currentXYView.max(d);
        }
        min[previewChIndex] = 0;
        max[previewChIndex] = 0;

        previewMask = currentXYView.factory().create(ops.transform().crop(currentXYView, new FinalInterval(min, max), false));
        LoopBuilder.setImages(Intervals.positions(previewMask), previewMask).multiThreaded().forEachPixel(
            (position, value) ->{
                int x = position.getIntPosition(xIndex);
                int y = position.getIntPosition(yIndex);
                int z = position.getIntPosition(zIndex);

                if(xyROI.contains(x,y) && xzROI.contains(x, z) && zyROI.contains(z,y)) {
                    value.setReal(maxValue);
            }
        });

        changes = false;
    }

    protected void exportMask(){
        statusService.showStatus("Exporting mask.");
        FinalDimensions dims = new FinalDimensions(xDim, yDim, zDim);
        Img<BitType> outputImg = ops.create().img(dims, new BitType());

        LoopBuilder.setImages(Intervals.positions(outputImg), outputImg).multiThreaded().forEachPixel(
                (position, value) ->{
                    int x = position.getIntPosition(0);
                    int y = position.getIntPosition(1);
                    int z = position.getIntPosition(2);

                    if(xyROI.contains(x,y) && xzROI.contains(x, z) && zyROI.contains(z,y))
                        value.setOne();
                }
        );
        if(outputImg == null)
            return;
        outputMask = datasetService.create(ImgPlus.wrap(outputImg));
        outputMask.setAxis(inputImage.axis(xIndex), 0);
        outputMask.setAxis(inputImage.axis(yIndex), 1);
        outputMask.setAxis(inputImage.axis(zIndex), 2);
        outputMask.setName("3D mask-"+ imageName);
        uiService.show(outputMask);
        statusService.showStatus("Mask Generated.");
    }

    private void updateDisplay(){
        Roi tempROI = WindowManager.getCurrentImage().getRoi();
        if(currentDisplay != null) currentDisplay.close();
        if(currentDisplayView != null) currentDisplayView.dispose();

        Dataset displayData = datasetService.create(currentView);
        displayData.setName("Working image-draw ROI");

        int currentChIndex = currentXYView.dimensionIndex(Axes.CHANNEL);

        if(currentChIndex != -1) {

            int correctChIndex;

            if (projectionChoice != projectionMethods.NONE && currentChIndex > zIndex)
                correctChIndex = currentChIndex - 1;
            else
                correctChIndex = currentChIndex;
            displayData.axis(correctChIndex).setType(Axes.CHANNEL);
            displayData.setCompositeChannelCount((int)currentXYView.dimension(currentChIndex));
            //todo: Add ColorTable copying. Tried previously but couldn't get it working.
        }

        currentDisplayView = (DatasetView) imageDisplayService.createDataView(displayData);
        currentDisplay = imageDisplayService.getDisplayService().createDisplay(currentDisplayView);

        /*
        This next section seems to only affect ImageJ2, not Fiji, which makes it currently useless.
        I'm keeping it for now, as I imagine it may help in the future.
         */
        if (projectionChoice == projectionMethods.NONE) {
            for (int i = 0; i < inputView.getChannelCount(); i++) {
                currentDisplayView.setChannelRange(i, inputView.getChannelMin(i), inputView.getChannelMax(i));
            }
            currentDisplayView.update();
        }

        if (tempROI != null)
            WindowManager.getCurrentImage().setRoi(tempROI);
    }

    protected void init() {
        imageName = inputImage.getName();

        xIndex = inputImage.dimensionIndex(Axes.X);
        yIndex = inputImage.dimensionIndex(Axes.Y);
        zIndex = inputImage.dimensionIndex(Axes.Z);
        chIndex = inputImage.dimensionIndex(Axes.CHANNEL);

        if(xIndex == -1 || yIndex == -1 || zIndex == -1){
            logService.error("Draw 3D ROI requires a 3D X-Y-Z image.", new InvalidDimensionsException(inputImage.dimensionsAsLongArray(), "Image is not a compatible 3D image."));
        }


        xDim = inputImage.dimension(xIndex);
        yDim = inputImage.dimension(yIndex);
        zDim = inputImage.dimension(zIndex);

        xyROI = new Roi(0,0,xDim,yDim);
        xzROI = new Roi(0,0,xDim,zDim);
        zyROI = new Roi(0,0,zDim,yDim);

        changes = true;

        currentXYView = inputImage;

        currentView = currentXYView;
        updateDisplay();
    }
}
