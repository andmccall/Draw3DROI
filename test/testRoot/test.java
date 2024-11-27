import net.imagej.Dataset;
import net.imagej.ImageJ;
import org.scijava.command.CommandInfo;
import org.scijava.module.ModuleInfo;

import java.io.IOException;

public class test {
    public static void main(String args[]) throws IOException {
        ImageJ ij = new ImageJ();
        ModuleInfo drawInfo = new CommandInfo(Draw3DROI.class);

        ij.module().addModule(drawInfo);
        ij.launch(args);

        Dataset testImage = ij.scifio().datasetIO().open("test/testResources/organ-of-corti.tif");
        ij.ui().show(testImage);

        ij.module().run(drawInfo, true, testImage);
    }
}
