import ij.WindowManager;
import ij.gui.Roi;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.DataView;
import net.imagej.display.ImageDisplayService;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.special.computer.Computers;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
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
            "Draw any ROI selection for each perspective, then click Create 3D mask." ;

    @Parameter(label="Display perspective:",choices={"XY", "XZ", "YZ"}, style="radioButtonHorizontal", persist=false, callback="viewChoice")
    private String viewChoiceSelection;

    @Parameter(label="Projection method:", persist = false, callback = "viewChoice")
    private projectionMethods projectionChoice;

    @Parameter(label="Add perspective's ROI.", callback = "addROI")
    private Button addROIButton;

    @Parameter(label="Create 3D mask", callback = "generateMask")
    private Button createMaskButton;

    @Parameter
    protected ImgPlus<T> inputImage;

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

    private DataView currentDisplayView;
    protected Display currentDisplay;
    private RandomAccessibleInterval<FloatType> floatView;
    private Img<FloatType> currentView;
    private int xIndex, yIndex, zIndex;
    private long xDim, yDim, zDim;
    private Roi xyROI, xzROI, yzROI;


    @Override
    public void run(){
    }

    protected void viewChoice(){
        switch (viewChoiceSelection){
            case "XY":
                currentView = ImgView.wrap(floatView);
                break;
            case "XZ":
                currentView = ImgView.wrap(Views.permute(floatView, yIndex, zIndex));
                break;
            case "YZ":
                currentView = ImgView.wrap(Views.permute(floatView, xIndex, zIndex));
                break;
        }
        if(projectionChoice != projectionMethods.NONE) {
            currentView = zProject(currentView);
        }
        updateDisplay();
    }


    //final Overlay o = overlayService.getActiveOverlay(display); //will try and compare
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

    protected Img zProject(Img input){
        long[] projectedDimensions = new long[input.numDimensions() - 1];

        int i = 0;

        for (int d = 0; d < input.numDimensions(); d++) {
            if (d != zIndex) {
                projectedDimensions[i] = input.dimension(d);
                i++;
            }
        }

        Img projection = ops.create().img(new FinalDimensions(
                projectedDimensions),new FloatType());


        UnaryComputerOp maxOp = Computers.unary(ops, projectionChoice.projectorOp, projection.firstElement(), input);

        ops.transform().project(projection, input, maxOp, zIndex);
        return projection;
    }

    protected void generateMask(){
        statusService.showStatus("Generating mask.");
        FinalDimensions dims = new FinalDimensions(xDim, yDim, zDim);
        Img<BitType> outputImg = ops.create().img(dims, new BitType());

        LoopBuilder.setImages(Intervals.positions(outputImg), outputImg).multiThreaded().forEachPixel(
                (position, value) ->{
                    int x = position.getIntPosition(0);
                    int y = position.getIntPosition(1);
                    int z = position.getIntPosition(2);

                    if(xyROI.contains(x,y) && xzROI.contains(x, z) && yzROI.contains(z,y))
                        value.setOne();
                }
        );

        outputMask = datasetService.create(ImgPlus.wrap(outputImg));
        outputMask.setAxis(inputImage.axis(xIndex), 0);
        outputMask.setAxis(inputImage.axis(yIndex), 1);
        outputMask.setAxis(inputImage.axis(zIndex), 2);
        uiService.show(outputMask);
        statusService.showStatus("Mask Generated.");
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

        xDim = inputImage.dimension(xIndex);
        yDim = inputImage.dimension(yIndex);
        zDim = inputImage.dimension(zIndex);

        floatView = ops.convert().float32(inputImage);

        currentView = ImgView.wrap(floatView);

        currentDisplayView = imageDisplayService.createDataView(datasetService.create(currentView));
        currentDisplay = imageDisplayService.getDisplayService().createDisplay(currentDisplayView);
    }
}
