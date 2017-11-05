/*
 * Copyright 2017 Michael Hoffer <info@michaelhoffer.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * If you use this software for scientific research then please cite the following publication(s):
 *
 * M. Hoffer, C. Poliwoda, & G. Wittum. (2013). Visual reflection library:
 * a framework for declarative GUI programming on the Java platform.
 * Computing and Visualization in Science, 2013, 16(4),
 * 181–192. http://doi.org/10.1007/s00791-014-0230-y
 */
package eu.mihosoft.pdfbox.vrlplugin;

import eu.mihosoft.vrl.annotation.ComponentInfo;
import eu.mihosoft.vrl.annotation.ParamInfo;
import eu.mihosoft.vrl.system.VMessage;
import eu.mihosoft.vrl.visual.ImageUtils;
import eu.mihosoft.vrl.visual.MessageType;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.*;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tutorial component.
 *
 * @author Michael Hoffer <info@michaelhoffer.de>
 */
@ComponentInfo(name = "ShrinkPDF", category = "PDFBox")
public class ShrinkPDF implements Serializable {

    // necessary for session serialization
    private static final long serialVersionUID = 1L;

    private transient int imgCounter = 1;

    // -- custom code --


    public void shrinkPDF(@ParamInfo(name = "PDF Input file", style = "load-dialog", options = "endings=[\".pdf\"]; description=\"*.pdf - Files\"") File inF,
                          @ParamInfo(name = "PDF Output file", style = "save-dialog", options = "endings=[\".pdf\"]; description=\"*.pdf - Files\"") File outF,
                          @ParamInfo(name = "Image Quality", style = "slider", options = "min=0;max=100;value=90") int quality,
                          @ParamInfo(name = "DPI", options = "min=0;value=300") int dpi
    ) throws IOException {
        shrinkPDF(inF, outF, quality / 100f, dpi);
    }

    /**
     * Shrink a PDF
     *
     * @param input        input file to shrink
     * @param output       output file (shrinked pdf)
     * @param jPEGCompQual JPEG compression quality value. {@code 0} means lowest quality,
     *                     {@code 1} means highest image quality.
     * @param dpi          image dpi, e.g., 300
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void shrinkPDF(File input, File output, float jPEGCompQual, int dpi)
            throws IOException {

        if (jPEGCompQual < 0 || jPEGCompQual > 1) {
            throw new RuntimeException("jPEGCompQual out of range. Valid range: [0, 1]!");
        }

        if (dpi < 0) {
            throw new RuntimeException("dpi out of range. Valid range: [0, Integer.MAX_VALUE]!");
        }

        System.out.println("********************************************************************************");
        System.out.println("Starting with shrinking " + input.getAbsolutePath());

        VMessage.getMsgBox().addMessageAsLog("Processing PDF", "Starting with shrinking " + input.getAbsolutePath() + "\n",MessageType.INFO);


        final PDFParser parser = new PDFParser(new RandomAccessFile(input, "r"));
        parser.parse();

        try(PDDocument doc = parser.getPDDocument()) {
            PDPageTree pages = doc.getDocumentCatalog().getPages();

            ImageDPI dpiProcessor = new ImageDPI();

            // compute image dpi
            for (PDPage p : pages) {
                dpiProcessor.processPage(p);
            }

            imgCounter = 1;

            // scan resources for images to shrink
            for (PDPage p : pages) {
                scanAndShrinkImages(p.getResources(), doc, jPEGCompQual, dpiProcessor, dpi);
            }

            System.out.println("Saving...");
            VMessage.getMsgBox().addMessageAsLog("Processing PDF", "Saving...\n",MessageType.INFO);

            doc.save(output);
        }

        System.out.println("Done with shrinking " + input.getAbsolutePath());
        System.out.println("********************************************************************************");

        VMessage.getMsgBox().addMessageAsLog("Processing PDF", "Done with shrinking " + input.getAbsolutePath() + "\n",MessageType.INFO);
    }

    private void scanAndShrinkImages(PDResources resources, final PDDocument doc, float jPEGCompQual, ImageDPI dpiProcessor, int dpi)
            throws IOException {



        for (COSName k : resources.getXObjectNames()) {
            final PDXObject xObj = resources.getXObject(k);

            // if this is a form we dive into it
            if (xObj instanceof PDFormXObject) {
                scanAndShrinkImages(((PDFormXObject) xObj).getResources(), doc, jPEGCompQual, dpiProcessor, dpi);
            }

            // we are only interested in images. thus, we skip everything else
            if (!(xObj instanceof PDImageXObject)) {
                continue;
            }

            PDImageXObject img = (PDImageXObject) xObj;
            System.out.println("-> converting img: " + k.getName() + " : (" + imgCounter + "/"+dpiProcessor.getNumerOfImages()+")");


            VMessage.getMsgBox().addMessageAsLog("Processing PDF", "-> converting img: " + k.getName() + " : (" + imgCounter + "/"+dpiProcessor.getNumerOfImages() + ")\n", MessageType.INFO, 30);

            int currentDPI = dpiProcessor.getDPIof(k.getName());

            double dpiScale = dpi * 1.0 / currentDPI;

            System.out.println("  -> dpi-scale: " + dpiScale);

            int newW = (int) (img.getWidth()*dpiScale);
            int newH = (int) (img.getHeight()*dpiScale);

            System.out.println("  -> img-size@" + dpi + "dpi: " + newW + "x"+newH);

            BufferedImage scaledImg = ImageUtils.convert(img.getImage(), BufferedImage.TYPE_INT_ARGB, newW, newH);

            PDImageXObject compressedImg = JPEGFactory.createFromImage(doc, scaledImg, jPEGCompQual);

            resources.put(k, compressedImg);

            imgCounter++;
        }
    }
}

class ImageDPI extends PDFStreamEngine {

    private final Map<String, Integer> imageDPI = new HashMap<>();

    public ImageDPI() {
        // prepare PDFStreamEngine
        addOperator(new Concatenate());
        addOperator(new DrawObject());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new SetMatrix());
    }

    public Integer getDPIof(String img) {
        return imageDPI.get(img);
    }

    public int getNumerOfImages() {
        return imageDPI.size();
    }

    /**
     * This is used to handle an operation.
     *
     * @param operator The operation to perform.
     * @param operands The list of arguments.
     * @throws IOException If there is an error processing the operation.
     */
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();

        if ("Do".equals(operation)) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);
            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject) xobject;
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();
                //System.out.println("*******************************************************************");
                //System.out.println("Found image [" + objectName.getName() + "]");

                //System.out.println("Stack Size: " + getGraphicsStackSize());

                Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                float imageXScale = ctmNew.getScalingFactorX();
                float imageYScale = ctmNew.getScalingFactorY();

                //System.out.println("Scaling factor = " + imageXScale + ", " + imageYScale);

                // position in user space units. 1 unit = 1/72 inch at 72 dpi
                //System.out.println("position in PDF = " + ctmNew.getTranslateX() + ", " + ctmNew.getTranslateY() + " in user space units");
                // raw size in pixels
                //System.out.println("raw image size  = " + imageWidth + ", " + imageHeight + " in pixels");
                // displayed size in user space units
                //System.out.println("displayed size  = " + imageXScale + ", " + imageYScale + " in user space units");
                // displayed size in inches at 72 dpi rendering
                imageXScale /= 72;
                imageYScale /= 72;
                //System.out.println("displayed size  = " + imageXScale + ", " + imageYScale + " in inches at 72 dpi rendering");
                int dpi = (int)(imageWidth / imageXScale);
                //System.out.println("DPI: " + dpi);

                imageDPI.put(objectName.getName(), dpi);

                // displayed size in millimeters at 72 dpi rendering
                imageXScale *= 25.4;
                imageYScale *= 25.4;
                //System.out.println("displayed size  = " + imageXScale + ", " + imageYScale + " in millimeters at 72 dpi rendering");
                //System.out.println();


            } else if (xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) xobject;
                showForm(form);
            }
        } else {
            super.processOperator(operator, operands);
        }
    }
}
