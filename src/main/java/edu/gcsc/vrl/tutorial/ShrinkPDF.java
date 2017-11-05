/*
 * Copyright 2017 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY Michael Hoffer <info@michaelhoffer.de> "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Michael Hoffer <info@michaelhoffer.de> OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of Michael Hoffer <info@michaelhoffer.de>.
 */
package edu.gcsc.vrl.tutorial;

import eu.mihosoft.vrl.annotation.ComponentInfo;
import eu.mihosoft.vrl.annotation.ParamInfo;
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.io.*;
import java.util.Iterator;

/**
 * A tutorial component.
 *
 * @author Michael Hoffer <info@michaelhoffer.de>
 */
@ComponentInfo(name="ShrinkPDF", category="PDFBox")
public class ShrinkPDF implements Serializable{

    // necessary for session serialization
    private static final long serialVersionUID = 1L;

    // -- custom code --


    public void shrinkPDF(@ParamInfo(name="PDF Input file", style="load-dialog", options="endings=[]") File inF,
                          @ParamInfo(name="PDF Output file", style="save-dialog") File outF,
                          @ParamInfo(name="Image Quality", style="slider", options="min=0;max=100;value=90") int quality
                          ) throws IOException {
        shrinkPDF(inF,outF,1.f/quality);
    }

    /**
     * Shrink a PDF
     * @param input input file to shrink
     * @param jPEGCompQual JPEG compression quality value. {@code 0} means lowest quality,
     * {@code 1} means highest image quality.
     * @return The compressed {@code PDDocument}
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void shrinkPDF(File input, File output, float jPEGCompQual)
            throws IOException {

        if(jPEGCompQual < 0 || jPEGCompQual > 1) {
            throw new RuntimeException("jPEGCompQual out of range. Valid range: [0, 1]!");
        }

        final PDFParser parser = new PDFParser(new RandomAccessFile(input,"r"));
        parser.parse();
        final PDDocument doc = parser.getPDDocument();
        PDPageTree pages = doc.getDocumentCatalog().getPages();

        // scan resources for images to shrink
        for(PDPage p : pages) {
            scanAndShrinkImages(p.getResources(), doc, jPEGCompQual);
        }

        doc.save(output);
    }

    private void scanAndShrinkImages(final PDResources resources, final PDDocument doc, float jPEGCompQual)
            throws FileNotFoundException, IOException {


        for(COSName k : resources.getXObjectNames()) {
            final PDXObject xObj = resources.getXObject(k);
            if(xObj instanceof PDFormXObject)
                scanAndShrinkImages(((PDFormXObject) xObj).getResources(), doc, jPEGCompQual);
            if(!(xObj instanceof PDImageXObject))
                continue;
            PDImageXObject img = (PDImageXObject) xObj;
            System.out.println(" -> converting img: " + k);
            final Iterator<ImageWriter> jpgWriters =
                    ImageIO.getImageWritersByFormatName("jpeg");
            final ImageWriter jpgWriter = jpgWriters.next();
            final ImageWriteParam iwp = jpgWriter.getDefaultWriteParam();
            iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            iwp.setCompressionQuality(jPEGCompQual);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            jpgWriter.setOutput(ImageIO.createImageOutputStream(baos));
            jpgWriter.write(null,
                    new IIOImage(img.getImage(), null, null), iwp);
            ByteArrayInputStream bais =
                    new ByteArrayInputStream(baos.toByteArray());
            PDImageXObject compressedImg = JPEGFactory.createFromStream(doc,bais);
            resources.put(k, compressedImg);
        }
    }
}
